// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.psi.util.JavaMultiReleaseUtil;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public final class PsiElementFinderImpl extends PsiElementFinder implements DumbAware {
  private final Project myProject;
  private final JavaFileManager myFileManager;

  //used for extension point instantiation
  public PsiElementFinderImpl(Project project) {
    myProject = project;
    myFileManager = JavaFileManager.getInstance(project);
  }

  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (skipIndices()) {
      return null;
    }
    return myFileManager.findClass(qualifiedName, scope);
  }

  private boolean skipIndices() {
    DumbService dumbService = DumbService.getInstance(myProject);
    return dumbService.isAlternativeResolveEnabled();
  }

  @Override
  public PsiClass @NotNull [] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    if (skipIndices()) {
      return PsiClass.EMPTY_ARRAY;
    }
    return myFileManager.findClasses(qualifiedName, scope);
  }

  @Override
  public boolean hasClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope, @NotNull Predicate<PsiClass> filter) {
    if (skipIndices()) {
      return false;
    }
    return myFileManager.hasClass(qualifiedName, scope, filter);
  }

  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    return myFileManager.findPackage(qualifiedName);
  }

  @Override
  public PsiPackage @NotNull [] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final Map<String, PsiPackage> packagesMap = new HashMap<>();
    final String qualifiedName = psiPackage.getQualifiedName();
    for (PsiDirectory dir : psiPackage.getDirectories(scope)) {
      PsiDirectory[] subDirs = dir.getSubdirectories();
      for (PsiDirectory subDir : subDirs) {
        if (JavaMultiReleaseUtil.getVersion(subDir.getVirtualFile()) != null) {
          continue;
        }
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(subDir);
        if (aPackage != null) {
          final String subQualifiedName = aPackage.getQualifiedName();
          if (subQualifiedName.startsWith(qualifiedName) && !packagesMap.containsKey(subQualifiedName)) {
            packagesMap.put(aPackage.getQualifiedName(), aPackage);
          }
        }
      }
    }

    packagesMap.remove(qualifiedName);    // avoid SOE caused by returning a package as a subpackage of itself
    return packagesMap.values().toArray(PsiPackage.EMPTY_ARRAY);
  }

  @Override
  public PsiClass @NotNull [] getClasses(@NotNull PsiPackage psiPackage, final @NotNull GlobalSearchScope scope) {
    return getClasses(null, psiPackage, scope);
  }

  @Override
  public PsiClass @NotNull [] getClasses(@Nullable String shortName,
                                         @NotNull PsiPackage psiPackage,
                                         @NotNull GlobalSearchScope scope) {
    List<PsiClass> list = null;
    String packageName = psiPackage.getQualifiedName();
    for (PsiDirectory dir : psiPackage.getDirectories(scope)) {
      PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(dir, scope);
      list = processClasses(shortName, classes, list, packageName);
    }
    for (PsiFile file : psiPackage.getIndividualFiles(scope)) {
      if (!(file instanceof PsiClassOwner)) continue;
      PsiClass[] classes = ((PsiClassOwner)file).getClasses();
      list = processClasses(shortName, classes, list, packageName);
    }
    if (list == null) {
      return PsiClass.EMPTY_ARRAY;
    }

    if (list.size() > 1) {
      if (shortName != null) {
        ContainerUtil.quickSort(list, PsiClassUtil.createScopeComparator(scope));
      } else {
        ContainerUtil.quickSort(list, Comparator.comparing(PsiClass::getName, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(PsiClassUtil.createScopeComparator(scope)));
      }
    }

    return list.toArray(PsiClass.EMPTY_ARRAY);
  }

  private static @Nullable List<PsiClass> processClasses(@Nullable String shortName,
                                                         PsiClass @NotNull [] classes,
                                                         @Nullable List<PsiClass> list,
                                                         @NotNull String packageName) {
    if (classes.length == 0) return list;
    if (list == null) list = new ArrayList<>();
    for (PsiClass aClass : classes) {
      // class file can be located in wrong place inside file system
      String qualifiedName = aClass.getQualifiedName();
      if (qualifiedName != null) qualifiedName = StringUtil.getPackageName(qualifiedName);
      if (Comparing.strEqual(qualifiedName, packageName)) {
        if (shortName == null || shortName.equals(aClass.getName())) list.add(aClass);
      }
    }
    return list;
  }

  @Override
  public @NotNull Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    Set<String> names = null;
    FileIndexFacade facade = FileIndexFacade.getInstance(myProject);
    for (PsiDirectory dir : psiPackage.getDirectories(scope)) {
      for (PsiFile file : dir.getFiles(scope)) {
        if (file instanceof PsiClassOwner && file.getViewProvider().getLanguages().size() == 1) {
          VirtualFile vFile = file.getVirtualFile();
          if (vFile != null &&
              !(file instanceof PsiCompiledElement) &&
              !facade.isInSourceContent(vFile) &&
              (!scope.isForceSearchingInLibrarySources() || !StubTreeLoader.getInstance().canHaveStub(vFile))) {
            continue;
          }

          Set<String> inFile =
            file instanceof PsiClassOwnerEx ? ((PsiClassOwnerEx)file).getClassNames() : getClassNames(((PsiClassOwner)file).getClasses());

          if (inFile.isEmpty()) continue;
          if (names == null) names = new HashSet<>();
          names.addAll(inFile);
        }
      }

    }
    return names == null ? Collections.emptySet() : names;
  }

  @Override
  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           final @NotNull GlobalSearchScope scope,
                                           final @NotNull Processor<? super PsiDirectory> consumer,
                                           boolean includeLibrarySources) {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    return PackageIndex.getInstance(myProject)
      .getDirsByPackageName(psiPackage.getQualifiedName(), includeLibrarySources)
      .forEach(new ReadActionProcessor<VirtualFile>() {
        @Override
        public boolean processInReadAction(final VirtualFile dir) {
          if (scope.contains(dir)) {
            PsiDirectory psiDir = psiManager.findDirectory(dir);
            if (psiDir != null && !consumer.process(psiDir)) return false;
          }
          return true;
        }
      });
  }

  @Override
  public boolean processPackageFiles(@NotNull PsiPackage psiPackage,
                                     final @NotNull GlobalSearchScope scope,
                                     final @NotNull Processor<? super PsiFile> consumer) {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    return PackageIndex.getInstance(myProject)
      .getFilesByPackageName(psiPackage.getQualifiedName())
      .forEach(new ReadActionProcessor<VirtualFile>() {
        @Override
        public boolean processInReadAction(final VirtualFile dir) {
          if (scope.contains(dir)) {
            PsiFile psiFile = psiManager.findFile(dir);
            if (psiFile != null && !consumer.process(psiFile)) return false;
          }
          return true;
        }
      });
  }
}
