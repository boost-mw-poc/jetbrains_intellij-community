// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.parameters.ParameterHintsExcludeListConfig
import com.intellij.codeInsight.hints.settings.Diff
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.lang.LangBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.CodeInsightColors.ERRORS_ATTRIBUTES
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.EditorTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel


@ApiStatus.Internal
class ExcludeListDialog(val config: ParameterHintsExcludeListConfig, private val patternToAdd: String? = null) : DialogWrapper(null) {
  lateinit var myEditor: EditorTextField
  private var myPatternsAreValid = true

  init {
    title = CodeInsightBundle.message("settings.inlay.parameter.hints.exclude.list")
    init()
  }

  override fun createCenterPanel(): JComponent? {
    return createExcludePanel(config)
  }


  private fun createExcludePanel(config: ParameterHintsExcludeListConfig): JPanel? {
    if (!config.isExcludeListSupported) return null

    val excludeList = getLanguageExcludeList(config)
    val finalText = if (patternToAdd != null) {
      excludeList + "\n" + patternToAdd
    }
    else {
      excludeList
    }
    val editorTextField = createExcludeListEditorField(finalText)
    editorTextField.alignmentX = Component.LEFT_ALIGNMENT
    editorTextField.addDocumentListener(object : DocumentListener {
      override fun documentChanged(e: DocumentEvent) {
        updateOkEnabled(editorTextField)
      }
    })
    updateOkEnabled(editorTextField)

    myEditor = editorTextField


    return panel {
      row {
        link(LangBundle.message("action.link.reset")) {
          setLanguageExcludeListToDefault(config)
        }.align(AlignX.RIGHT)
      }
      row {
        cell(editorTextField)
          .align(AlignX.FILL)
      }.bottomGap(BottomGap.SMALL)
      baseLanguageComment(config)?.let {
        row {
          comment(it)
        }
      }
      row {
        comment(getExcludeListExplanationHTML(config))
      }
    }
  }

  private fun baseLanguageComment(config: ParameterHintsExcludeListConfig): @Nls String? {
    return config.excludeListDependencyLanguage
      ?.let { CodeInsightBundle.message("inlay.hints.base.exclude.list.description", it.displayName) }
  }

  private fun setLanguageExcludeListToDefault(config: ParameterHintsExcludeListConfig) {
    myEditor.text = StringUtil.join(config.defaultExcludeList, "\n")
  }

  private fun updateOkEnabled(editorTextField: EditorTextField) {
    val text = editorTextField.text
    val invalidLines = getExcludeListInvalidLineNumbers(text)
    myPatternsAreValid = invalidLines.isEmpty()

    okAction.isEnabled = myPatternsAreValid

    val editor = editorTextField.editor
    if (editor != null) {
      highlightErrorLines(invalidLines, editor)
    }
  }

  override fun doOKAction() {
    super.doOKAction()
    val excludeList = myEditor.text
    storeExcludeListDiff(config, excludeList)
  }

  private fun storeExcludeListDiff(config: ParameterHintsExcludeListConfig, text: String) {
    val updatedExcludeList = text.split("\n").filter { e -> e.trim { it <= ' ' }.isNotEmpty() }.toSet()

    val diff = Diff.build(config.defaultExcludeList, updatedExcludeList)
    ParameterNameHintsSettings.getInstance().setExcludeListDiff(config.language, diff)
    refreshParameterHintsOnNextPass()
  }
}


private fun getLanguageExcludeList(config: ParameterHintsExcludeListConfig): String {
  val diff = ParameterNameHintsSettings.getInstance().getExcludeListDiff(config.language)
  val excludeList = diff.applyOn(config.defaultExcludeList)
  return StringUtil.join(excludeList, "\n")
}

private fun createExcludeListEditorField(text: String): EditorTextField {
  val document = EditorFactory.getInstance().createDocument(text)
  val field = EditorTextField(document, null, FileTypes.PLAIN_TEXT, false, false)
  field.preferredSize = Dimension(400, 350)
  field.addSettingsProvider { editor ->
    editor.setVerticalScrollbarVisible(true)
    editor.setHorizontalScrollbarVisible(true)
    editor.settings.additionalLinesCount = 2
    highlightErrorLines(getExcludeListInvalidLineNumbers(text), editor)
  }
  return field
}

private fun highlightErrorLines(lines: List<Int>, editor: Editor) {
  val document = editor.document
  val totalLines = document.lineCount

  val model = editor.markupModel
  model.removeAllHighlighters()
  lines.stream()
    .filter { current -> current < totalLines }
    .forEach { line -> model.addLineHighlighter(ERRORS_ATTRIBUTES, line!!, HighlighterLayer.ERROR) }
}

@NlsContexts.DetailedDescription
private fun getExcludeListExplanationHTML(config: ParameterHintsExcludeListConfig): String {
  return config.excludeListExplanationHtml ?: CodeInsightBundle.message("inlay.hints.exclude.list.pattern.explanation")
}