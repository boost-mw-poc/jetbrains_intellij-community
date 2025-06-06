// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.editor

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.impl.CannotAddVcsLogWindowException
import com.intellij.vcs.log.impl.VcsLogEditorUtil
import com.intellij.vcs.log.impl.VcsLogTabsManager.Companion.onDisplayNameChange
import com.intellij.vcs.log.impl.VcsLogTabsUtil
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.util.VcsLogUtil
import java.awt.BorderLayout
import javax.swing.JComponent

internal class DefaultVcsLogFile(private val pathId: VcsLogVirtualFileSystem.VcsLogComplexPath,
                                 private var filters: VcsLogFilterCollection? = null) :
  VcsLogFile(VcsLogTabsUtil.getFullName(pathId.logId)), VirtualFilePathWrapper { //NON-NLS not displayed

  private val fileSystemInstance: VcsLogVirtualFileSystem = VcsLogVirtualFileSystem.Holder.getInstance()
  internal val tabId get() = pathId.logId

  internal var tabName: String
    get() = service<VcsLogEditorTabNameCache>().getTabName(path) ?: name
    set(value) = service<VcsLogEditorTabNameCache>().putTabName(path, value)

  init {
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
  }

  override fun createMainComponent(project: Project): JComponent {
    val panel = JBPanelWithEmptyText(BorderLayout()).withEmptyText(VcsLogBundle.message("vcs.log.is.loading"))
    VcsLogUtil.runWhenVcsAndLogIsReady(project) { logManager ->
      try {
        val ui = logManager.createLogUi(tabId, filters)
        Disposer.register(ui) {
          isValid = false
          FileEditorManager.getInstance(project).closeFile(this)
        }
        tabName = VcsLogTabsUtil.generateDisplayName(ui)
        ui.onDisplayNameChange {
          tabName = VcsLogTabsUtil.generateDisplayName(ui)
          VcsLogEditorUtil.updateTabName(project, ui)
        }
        if (filters != null) filters = null
        panel.add(VcsLogPanel(logManager, ui), BorderLayout.CENTER)
      }
      catch (e: CannotAddVcsLogWindowException) {
        LOG.error(e)
        panel.emptyText.text = VcsLogBundle.message("vcs.log.duplicated.tab.id.error")
      }
    }
    return panel
  }

  override fun getFileSystem(): VirtualFileSystem = fileSystemInstance
  override fun getPath(): String {
    return fileSystemInstance.getPath(pathId)
  }

  override fun enforcePresentableName(): Boolean = true
  override fun getPresentableName(): String = tabName
  override fun getPresentablePath(): String = tabName

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as DefaultVcsLogFile

    return tabId == other.tabId
  }

  override fun hashCode(): Int {
    return tabId.hashCode()
  }

  companion object {
    private val LOG = logger<DefaultVcsLogFile>()
  }
}

internal class DefaultVcsLogFileTabTitleProvider : EditorTabTitleProvider, DumbAware {

  override fun getEditorTabTooltipText(project: Project, file: VirtualFile): String? {
    if (file !is DefaultVcsLogFile) return null
    return getEditorTabTitle(project, file)
  }

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (file !is DefaultVcsLogFile) return null
    return file.tabName
  }
}

@Service(Service.Level.APP)
@State(name = "Vcs.Log.Editor.Tab.Names", storages = [Storage(StoragePathMacros.CACHE_FILE)])
private class VcsLogEditorTabNameCache : SimplePersistentStateComponent<VcsLogEditorTabNameCache.MyState>(MyState()) {

  fun getTabName(path: String) = state.pathToTabName[path]

  fun putTabName(path: String, tabName: String) {
    state.pathToTabName.remove(path)
    state.pathToTabName[path] = tabName // to put recently changed paths at the end of the linked map

    val limit = UISettings.getInstance().recentFilesLimit
    while (state.pathToTabName.size > limit) {
      val (firstPath, _) = state.pathToTabName.asIterable().first()
      state.pathToTabName.remove(firstPath)
    }

    state.intIncrementModificationCount()
  }

  class MyState : BaseState() {
    @get:Tag("path-to-tab-name")
    val pathToTabName by linkedMap<String, String>()
  }
}
