// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IReparseableElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks.GrBlockImpl;

public abstract class GrCodeBlockElementType extends IReparseableElementType implements ICompositeElementType {

  private final boolean isInsideSwitch;

  protected GrCodeBlockElementType(String debugName, boolean isInsideSwitch) {
    super(debugName, GroovyLanguage.INSTANCE);
    this.isInsideSwitch = isInsideSwitch;
  }

  @Override
  public @NotNull ASTNode createCompositeNode() {
    return createNode(null);
  }

  @Override
  public abstract @NotNull GrBlockImpl createNode(final CharSequence text);

  @Override
  public boolean isParsable(@NotNull CharSequence buffer, @NotNull Language fileLanguage, @NotNull Project project) {
    return GroovyParserUtils.isBlockParseable(buffer);
  }

  public boolean isInsideSwitch() {
    return isInsideSwitch;
  }
}
