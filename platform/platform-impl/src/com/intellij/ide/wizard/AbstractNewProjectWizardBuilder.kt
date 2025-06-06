// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.ProjectBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter.Companion.NPW_PREFIX
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.trackActivityBlocking
import org.jetbrains.annotations.ApiStatus
import java.util.function.BiConsumer
import javax.swing.Icon

abstract class AbstractNewProjectWizardBuilder : ModuleBuilder() {
  private var panel: NewProjectWizardStepPanel? = null

  abstract override fun getPresentableName(): String
  abstract override fun getNodeIcon(): Icon

  protected abstract fun createStep(context: WizardContext): NewProjectWizardStep

  override fun getDescription(): String = ""

  final override fun getModuleType(): ModuleType<AbstractNewProjectWizardBuilder> =
    object : ModuleType<AbstractNewProjectWizardBuilder>(NPW_PREFIX + javaClass.simpleName) {
      override fun createModuleBuilder() = this@AbstractNewProjectWizardBuilder
      override fun getName() = this@AbstractNewProjectWizardBuilder.presentableName
      override fun getDescription() = this@AbstractNewProjectWizardBuilder.description
      override fun getNodeIcon(isOpened: Boolean) = this@AbstractNewProjectWizardBuilder.nodeIcon
    }

  final override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep {
    val wizardStep = createStep(context)
    panel = NewProjectWizardStepPanel(wizardStep)
    return BridgeStep(panel!!)
  }

  override fun commitModule(project: Project, model: ModifiableModuleModel?): Module? {
    val step = panel!!.step
    step.context.putUserData(MODIFIABLE_MODULE_MODEL_KEY, model)
    return detectCreatedModule(project, model) {
      project.trackActivityBlocking(NewProjectWizardActivityKey) {
        step.setupProject(project)
      }
    }
  }

  @ApiStatus.Internal
  override fun postCommit(project: Project, projectDir: VirtualFile) {
    panel!!.step.context
      .getOrCreateUserData(POST_COMMIT_ACTIONS) { ArrayList() }
      .forEach { it.accept(project, projectDir) }
  }

  override fun cleanup() {
    panel = null
  }

  private class BridgeStep(private val panel: NewProjectWizardStepPanel) :
    ModuleWizardStep(),
    NewProjectWizardStep by panel.step {

    override fun validate() = panel.validate()

    override fun updateDataModel() = panel.apply()

    override fun getPreferredFocusedComponent() = panel.getPreferredFocusedComponent()

    override fun getComponent() = panel.component
  }

  companion object {

    @get:ApiStatus.Internal
    val MODIFIABLE_MODULE_MODEL_KEY: Key<ModifiableModuleModel> = Key.create("MODIFIABLE_MODULE_MODEL_KEY")
    private val POST_COMMIT_ACTIONS: Key<MutableList<BiConsumer<Project, VirtualFile>>> = Key.create("POST_COMMIT_ACTIONS")

    @ApiStatus.Internal
    fun NewProjectWizardStep.commitByBuilder(builder: ProjectBuilder, project: Project): List<Module> {
      val model = context.getUserData(MODIFIABLE_MODULE_MODEL_KEY)
      return builder.commit(project, model)
    }

    @ApiStatus.Internal
    fun NewProjectWizardStep.postCommitByBuilder(builder: ProjectBuilder) {
      context.getOrCreateUserData(POST_COMMIT_ACTIONS) { ArrayList() }
        .add(builder::postCommit)
    }

    @ApiStatus.Internal
    fun NewProjectWizardStep.addPostCommitAction(action: (Project) -> Unit) {
      context.getOrCreateUserData(POST_COMMIT_ACTIONS) { ArrayList() }
        .add { project, _ -> action(project) }
    }

    private fun detectCreatedModule(project: Project, model: ModifiableModuleModel?, action: () -> Unit): Module? {
      if (model == null) {
        return detectCreatedModule(project, action)
      }
      var createdModuleLocal: Module? = null
      val createdModuleGlobal = detectCreatedModule(project) {
        createdModuleLocal = detectCreatedModule(model, action)
      }
      return createdModuleLocal ?: createdModuleGlobal
    }

    private fun detectCreatedModule(project: Project, action: () -> Unit): Module? {
      val manager = ModuleManager.getInstance(project)
      val modules = manager.modules
      action()
      return manager.modules.find { it !in modules }
    }

    private fun detectCreatedModule(model: ModifiableModuleModel, action: () -> Unit): Module? {
      val modules = model.modules
      action()
      return model.modules.find { it !in modules }
    }
  }
}