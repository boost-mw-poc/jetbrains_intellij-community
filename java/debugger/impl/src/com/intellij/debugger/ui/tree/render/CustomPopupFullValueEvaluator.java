// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.tree.render;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class CustomPopupFullValueEvaluator<T> extends JavaValue.JavaFullValueEvaluator {
  public CustomPopupFullValueEvaluator(@NotNull @Nls String linkText, @NotNull EvaluationContextImpl evaluationContext) {
    super(linkText, evaluationContext);
    setShowValuePopup(false);
  }

  protected abstract T getData();

  protected abstract JComponent createComponent(T data);

  @Override
  public void evaluate(final @NotNull XFullValueEvaluationCallback callback) {
    final T data = getData();
    DebuggerUIUtil.invokeLater(() -> {
      if (callback.isObsolete()) return;
      final JComponent comp = createComponent(data);
      Project project = getEvaluationContext().getProject();
      JBPopup popup = DebuggerUIUtil.createValuePopup(project, comp, null);
      JFrame frame = WindowManager.getInstance().getFrame(project);
      Dimension frameSize = frame.getSize();
      Dimension size = new Dimension(frameSize.width / 2, frameSize.height / 2);
      popup.setSize(size);
      if (comp instanceof Disposable) {
        Disposer.register(popup, (Disposable)comp);
      }
      callback.evaluated("");
      popup.show(new RelativePoint(frame, new Point(size.width / 2, size.height / 2)));
    });
  }
}
