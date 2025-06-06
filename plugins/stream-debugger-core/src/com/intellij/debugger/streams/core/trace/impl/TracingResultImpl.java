// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.core.trace.impl;

import com.intellij.debugger.streams.core.lib.ResolverFactory;
import com.intellij.debugger.streams.core.resolve.ResolvedStreamCall;
import com.intellij.debugger.streams.core.resolve.ResolvedStreamChain;
import com.intellij.debugger.streams.core.resolve.ValuesOrderResolver;
import com.intellij.debugger.streams.core.resolve.impl.ResolvedIntermediateCallImpl;
import com.intellij.debugger.streams.core.resolve.impl.ResolvedStreamChainImpl;
import com.intellij.debugger.streams.core.resolve.impl.ResolvedTerminatorCallImpl;
import com.intellij.debugger.streams.core.trace.*;
import com.intellij.debugger.streams.core.wrapper.IntermediateStreamCall;
import com.intellij.debugger.streams.core.wrapper.StreamChain;
import com.intellij.debugger.streams.core.wrapper.TraceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Vitaliy.Bibaev
 */
public class TracingResultImpl implements TracingResult {
  private final TraceElement myStreamResult;
  private final List<TraceInfo> myTrace;
  private final boolean myIsResultException;
  private final StreamChain mySourceChain;

  TracingResultImpl(@NotNull StreamChain chain,
                    @NotNull TraceElement streamResult,
                    @NotNull List<TraceInfo> trace,
                    boolean isResultException) {
    myStreamResult = streamResult;
    myTrace = trace;
    mySourceChain = chain;
    myIsResultException = isResultException;
  }

  @Override
  public @NotNull TraceElement getResult() {
    return myStreamResult;
  }

  @Override
  public boolean exceptionThrown() {
    return myIsResultException;
  }

  @Override
  public @NotNull List<TraceInfo> getTrace() {
    return myTrace;
  }

  @Override
  public @NotNull ResolvedTracingResult resolve(@NotNull ResolverFactory resolverFactory) {
    assert myTrace.size() == mySourceChain.length();

    List<ValuesOrderResolver.Result> resolvedTraces =
      ContainerUtil.map(myTrace, x -> resolverFactory.getResolver(x.getCall().getName(), x.getCall().getType()).resolve(x));

    final TraceInfo firstCallTrace = myTrace.get(0);
    final List<IntermediateStreamCall> intermediateCalls = mySourceChain.getIntermediateCalls();

    final ResolvedStreamChainImpl.Builder chainBuilder = new ResolvedStreamChainImpl.Builder();
    final List<TraceElement> valuesBeforeFirstCall = TraceUtil.sortedByTime(firstCallTrace.getValuesOrderBefore().values());
    final FirstStateImpl firstState = new FirstStateImpl(valuesBeforeFirstCall, firstCallTrace.getCall(),
                                                         resolvedTraces.get(0).getDirectOrder());

    if (intermediateCalls.isEmpty()) {
      chainBuilder.setTerminator(buildResolvedTerminationCall(myTrace.get(0), firstState, resolvedTraces.get(0).getReverseOrder()));
    }
    else {
      final ArrayList<IntermediateStateImpl> states = new ArrayList<>();

      for (int i = 0; i < intermediateCalls.size() - 1; i++) {
        states.add(new IntermediateStateImpl(TraceUtil.sortedByTime(myTrace.get(i).getValuesOrderAfter().values()),
                                             intermediateCalls.get(i),
                                             intermediateCalls.get(i + 1),
                                             resolvedTraces.get(i).getReverseOrder(),
                                             resolvedTraces.get(i + 1).getDirectOrder()));
      }

      states.add(new IntermediateStateImpl(TraceUtil.sortedByTime(myTrace.get(myTrace.size() - 1).getValuesOrderBefore().values()),
                                           intermediateCalls.get(intermediateCalls.size() - 1),
                                           mySourceChain.getTerminationCall(),
                                           resolvedTraces.get(resolvedTraces.size() - 2).getReverseOrder(),
                                           resolvedTraces.get(resolvedTraces.size() - 1).getDirectOrder()));

      chainBuilder.addIntermediate(new ResolvedIntermediateCallImpl(intermediateCalls.get(0), firstState, states.get(0)));
      for (int i = 1; i < states.size(); i++) {
        chainBuilder.addIntermediate(new ResolvedIntermediateCallImpl(intermediateCalls.get(i), states.get(i - 1), states.get(i)));
      }

      chainBuilder.setTerminator(buildResolvedTerminationCall(myTrace.get(myTrace.size() - 1), states.get(states.size() - 1),
                                                              resolvedTraces.get(resolvedTraces.size() - 1).getReverseOrder()));
    }

    return new MyResolvedResult(chainBuilder.build());
  }

  private ResolvedStreamCall.Terminator buildResolvedTerminationCall(@NotNull TraceInfo terminatorTrace,
                                                                     @NotNull NextAwareState previousState,
                                                                     @NotNull Map<TraceElement, List<TraceElement>>
                                                                       terminationToPrevMapping) {
    final List<TraceElement> after = TraceUtil.sortedByTime(terminatorTrace.getValuesOrderAfter().values());
    final TerminationStateImpl terminatorState =
      new TerminationStateImpl(myStreamResult, previousState.getNextCall(), after, terminationToPrevMapping);
    return new ResolvedTerminatorCallImpl(mySourceChain.getTerminationCall(), previousState, terminatorState);
  }

  private class MyResolvedResult implements ResolvedTracingResult {

    private final @NotNull ResolvedStreamChain myChain;

    MyResolvedResult(@NotNull ResolvedStreamChain resolvedStreamChain) {
      myChain = resolvedStreamChain;
    }

    @Override
    public @NotNull ResolvedStreamChain getResolvedChain() {
      return myChain;
    }

    @Override
    public @NotNull StreamChain getSourceChain() {
      return mySourceChain;
    }

    @Override
    public boolean exceptionThrown() {
      return myIsResultException;
    }

    @Override
    public @NotNull TraceElement getResult() {
      return myStreamResult;
    }
  }
}
