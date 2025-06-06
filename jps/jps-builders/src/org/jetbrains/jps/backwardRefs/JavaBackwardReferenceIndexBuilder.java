// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.backwardRefs;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ModuleChunk;
import org.jetbrains.jps.builders.*;
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor;
import org.jetbrains.jps.incremental.BuilderCategory;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ModuleBuildTarget;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class JavaBackwardReferenceIndexBuilder extends ModuleLevelBuilder {
  private static final Logger LOG = Logger.getInstance(JavaBackwardReferenceIndexBuilder.class);
  public static final String BUILDER_ID = "compiler.ref.index";
  private static final String MESSAGE_TYPE = "processed module";
  private final Set<ModuleBuildTarget> myCompiledTargets = ConcurrentHashMap.newKeySet();

  public JavaBackwardReferenceIndexBuilder() {
    super(BuilderCategory.CLASS_POST_PROCESSOR);
  }

  @Override
  public @NotNull String getPresentableName() {
    return JpsBuildBundle.message("builder.name.backward.references.indexer");
  }

  @Override
  public void buildStarted(CompileContext context) {
    JavaBackwardReferenceIndexWriter.initialize(context);
  }

  @Override
  public void buildFinished(CompileContext context) {
    if (JavaBackwardReferenceIndexWriter.getInstance() != null) {
      final BuildTargetIndex targetIndex = context.getProjectDescriptor().getBuildTargetIndex();
      for (JpsModule module : context.getProjectDescriptor().getProject().getModules()) {
        boolean allAreDummyOrCompiled = true;
        for (ModuleBasedTarget<?> target : targetIndex.getModuleBasedTargets(module, BuildTargetRegistry.ModuleTargetSelector.ALL)) {
          if (target instanceof ModuleBuildTarget && !myCompiledTargets.contains(target) && !targetIndex.isDummy(target)) {
            allAreDummyOrCompiled = false;
          }
        }
        if (allAreDummyOrCompiled) {
          context.processMessage(new CustomBuilderMessage(BUILDER_ID, MESSAGE_TYPE, module.getName()));
        }
      }
      myCompiledTargets.clear();
    }

    JavaBackwardReferenceIndexWriter.closeIfNeeded(false);
  }

  @Override
  public @NotNull List<String> getCompilableFileExtensions() {
    return Collections.emptyList();
  }

  @Override
  public ExitCode build(CompileContext context,
                        ModuleChunk chunk,
                        DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget> dirtyFilesHolder,
                        OutputConsumer outputConsumer) throws IOException {
    final JavaBackwardReferenceIndexWriter writer = JavaBackwardReferenceIndexWriter.getInstance();
    if (writer != null) {
      final Throwable cause = writer.getRebuildRequestCause();
      if (cause != null) {
        LOG.error("compiler reference index will be deleted", cause);
        JavaBackwardReferenceIndexWriter.closeIfNeeded(true);
      }

      if (dirtyFilesHolder.hasRemovedFiles()) {
        for (ModuleBuildTarget target : chunk.getTargets()) {
          writer.processDeleted(dirtyFilesHolder.getRemoved(target));
        }
      }

      for (ModuleBuildTarget target : chunk.getTargets()) {
        if (context.getScope().isWholeTargetAffected(target)) {
          myCompiledTargets.add(target);
        }
      }

      writer.force();
    }
    return null;
  }
}
