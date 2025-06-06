// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.local;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.execution.process.LocalPtyOptions;
import com.intellij.execution.target.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LocalTargetEnvironment extends TargetEnvironment {
  private final Map<UploadRoot, UploadableVolume> myUploadVolumes = new HashMap<>();
  private final Map<DownloadRoot, DownloadableVolume> myDownloadVolumes = new HashMap<>();
  private final Map<TargetPortBinding, ResolvedPortBinding> myTargetPortBindings = new HashMap<>();
  private final Map<LocalPortBinding, ResolvedPortBinding> myLocalPortBindings = new HashMap<>();

  public LocalTargetEnvironment(@NotNull LocalTargetEnvironmentRequest request) {
    super(request);

    for (UploadRoot uploadRoot : request.getUploadVolumes()) {
      final Path targetRoot;
      if (uploadRoot.getTargetRootPath() instanceof TargetPath.Persistent) {
        targetRoot = Paths.get(((TargetPath.Persistent)uploadRoot.getTargetRootPath()).getAbsolutePath());
      }
      else {
        targetRoot = uploadRoot.getLocalRootPath().toAbsolutePath();
      }
      myUploadVolumes.put(uploadRoot, new LocalVolume(uploadRoot.getLocalRootPath(), targetRoot));
    }

    for (DownloadRoot downloadRoot : request.getDownloadVolumes()) {
      final Path localRoot;
      if (downloadRoot.getLocalRootPath() != null) {
        localRoot = downloadRoot.getLocalRootPath();
      }
      else {
        try {
          localRoot = FileUtil.createTempDirectory("intellij-target", "", true).toPath();
        }
        catch (IOException err) {
          // TODO add `throws` to the methods signature, maybe convert to the factory.
          throw new IllegalStateException(err);
        }
      }
      final Path targetRoot;
      if (downloadRoot.getTargetRootPath() instanceof TargetPath.Persistent) {
        targetRoot = Paths.get(((TargetPath.Persistent)downloadRoot.getTargetRootPath()).getAbsolutePath());
      }
      else {
        targetRoot = localRoot;
      }
      myDownloadVolumes.put(downloadRoot, new LocalVolume(localRoot, targetRoot));
    }

    for (TargetPortBinding targetPortBinding : request.getTargetPortBindings()) {
      int theOnlyPort = targetPortBinding.getTarget();
      if (targetPortBinding.getLocal() != null && !targetPortBinding.getLocal().equals(theOnlyPort)) {
        throw new UnsupportedOperationException("TCP port forwarding for the local target is not implemented. " +
                                                "Please use the same port number for both local and target ports.");
      }
      myTargetPortBindings.put(targetPortBinding, getResolvedPortBinding(theOnlyPort));
    }

    for (LocalPortBinding localPortBinding : request.getLocalPortBindings()) {
      int theOnlyPort = localPortBinding.getLocal();
      if (localPortBinding.getTarget() != null && !localPortBinding.getTarget().equals(theOnlyPort)) {
        throw new UnsupportedOperationException("TCP port forwarding for the local target is not implemented. " +
                                                "Please use the same port number for both local and target ports.");
      }
      myLocalPortBindings.put(localPortBinding, getResolvedPortBinding(theOnlyPort));
    }
  }

  @Override
  public @NotNull LocalTargetEnvironmentRequest getRequest() {
    return (LocalTargetEnvironmentRequest)super.getRequest();
  }

  private static @NotNull ResolvedPortBinding getResolvedPortBinding(int port) {
    HostPort hostPort = new HostPort("127.0.0.1", port);
    return new ResolvedPortBinding(hostPort, hostPort);
  }

  @Override
  public @NotNull Map<UploadRoot, UploadableVolume> getUploadVolumes() {
    return Collections.unmodifiableMap(myUploadVolumes);
  }

  @Override
  public @NotNull Map<DownloadRoot, DownloadableVolume> getDownloadVolumes() {
    return Collections.unmodifiableMap(myDownloadVolumes);
  }

  @Override
  public @NotNull Map<TargetPortBinding, ResolvedPortBinding> getTargetPortBindings() {
    return Collections.unmodifiableMap(myTargetPortBindings);
  }

  @Override
  public @NotNull Map<LocalPortBinding, ResolvedPortBinding> getLocalPortBindings() {
    return Collections.unmodifiableMap(myLocalPortBindings);
  }

  @Override
  public @NotNull TargetPlatform getTargetPlatform() {
    return TargetPlatform.CURRENT;
  }

  @Override
  public @NotNull Process createProcess(@NotNull TargetedCommandLine commandLine, @NotNull ProgressIndicator indicator) throws ExecutionException {
    return createGeneralCommandLine(commandLine).createProcess();
  }

  public @NotNull GeneralCommandLine createGeneralCommandLine(@NotNull TargetedCommandLine commandLine) throws CantRunException {
    try {
      PtyOptions ptyOption = commandLine.getPtyOptions();
      LocalPtyOptions localPtyOptions = ptyOption != null ? LocalTargets.toLocalPtyOptions(ptyOption) : null;
      GeneralCommandLine generalCommandLine;
      if (localPtyOptions != null) {
        PtyCommandLine ptyCommandLine = new PtyCommandLine(commandLine.collectCommandsSynchronously());
        ptyCommandLine.withOptions(localPtyOptions);
        generalCommandLine = ptyCommandLine;
      }
      else {
        generalCommandLine = new GeneralCommandLine(commandLine.collectCommandsSynchronously());
      }
      generalCommandLine.withParentEnvironmentType(getRequest().getParentEnvironmentType());
      String inputFilePath = commandLine.getInputFilePath();
      if (inputFilePath != null) {
        generalCommandLine.withInput(new File(inputFilePath));
      }
      generalCommandLine.withCharset(commandLine.getCharset());
      String workingDirectory = commandLine.getWorkingDirectory();
      if (workingDirectory != null) {
        generalCommandLine.withWorkDirectory(workingDirectory);
      }
      generalCommandLine.withEnvironment(commandLine.getEnvironmentVariables());
      generalCommandLine.setRedirectErrorStream(commandLine.isRedirectErrorStream());
      return generalCommandLine;
    }
    catch (ExecutionException e) {
      throw new CantRunException(e.getMessage(), e);
    }
  }

  @Override
  public void shutdown() {
    //
  }

  private static final class LocalVolume implements UploadableVolume, DownloadableVolume {
    private final Path myLocalRoot;
    private final Path myTargetRoot;
    private final boolean myReal;

    private LocalVolume(@NotNull Path localRoot, @NotNull Path targetRoot) {
      myLocalRoot = localRoot;
      myTargetRoot = targetRoot;
      // Checking for local root existence is a workaround for tests like JavaCommandLineTest that check paths of imaginary files.
      myReal = isReal(localRoot, targetRoot);
    }

    private static boolean isReal(Path localRoot, Path targetRoot) {
      if (localRoot.equals(targetRoot)) {
        return false;
      }

      Path realLocalRoot;

      try {
        realLocalRoot = localRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
      }
      catch (FileNotFoundException e) {
        return false;
      }
      catch (IOException e) {
        return true;
      }

      try {
        return !realLocalRoot.equals(targetRoot.toRealPath(LinkOption.NOFOLLOW_LINKS));
      }
      catch (IOException e) {
        return true;
      }
    }

    @Override
    public @NotNull Path getLocalRoot() {
      return myLocalRoot;
    }

    @Override
    public @NotNull String getTargetRoot() {
      return myTargetRoot.toString();
    }

    @Override
    public @NotNull String resolveTargetPath(@NotNull String relativePath) throws IOException {
      if (myReal) {
        File targetFile = myTargetRoot.resolve(relativePath).toFile().getCanonicalFile();
        return targetFile.toString();
      }
      else {
        // myLocalRoot used there intentionally, because it could contain a relative path that some test expects to get.
        return FileUtil.toCanonicalPath(FileUtil.join(myLocalRoot.toString(), relativePath)).replace('/', File.separatorChar);
      }
    }

    @Override
    public void upload(@NotNull String relativePath,
                       @NotNull TargetProgressIndicator targetProgressIndicator) throws IOException {
      if (myReal) {
        File targetFile = myTargetRoot.resolve(relativePath).toFile().getCanonicalFile();
        FileUtil.copyFileOrDir(myLocalRoot.resolve(relativePath).toFile().getCanonicalFile(), targetFile);
      }
    }

    @Override
    public void download(@NotNull String relativePath, @NotNull ProgressIndicator progressIndicator) throws IOException {
      if (myReal) {
        FileUtil.copyFileOrDir(
          myTargetRoot.resolve(relativePath).toFile().getCanonicalFile(),
          myLocalRoot.resolve(relativePath).toFile().getCanonicalFile());
      }
    }
  }
}
