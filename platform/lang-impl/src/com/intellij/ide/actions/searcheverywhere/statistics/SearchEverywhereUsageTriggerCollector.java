// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.statistics;

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.internal.statistic.IdeActivityDefinition;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

@ApiStatus.Internal
public final class SearchEverywhereUsageTriggerCollector extends CounterUsagesCollector {

  private static final EventLogGroup GROUP = new EventLogGroup("searchEverywhere", 20);

  // this string will be used as ID for contributors from private
  // plugins that mustn't be sent in statistics
  @ApiStatus.Internal
  public static final String NOT_REPORTABLE_ID = "third.party";

  public static final StringEventField CONTRIBUTOR_ID_FIELD = EventFields.String("contributorID", Arrays.asList(
    "FileSearchEverywhereContributor",
    "SearchEverywhereContributor.All",
    "ClassSearchEverywhereContributor",
    "RecentFilesSEContributor",
    "ActionSearchEverywhereContributor",
    "SymbolSearchEverywhereContributor",
    "TopHitSEContributor",
    "RunConfigurationsSEContributor",
    "YAMLKeysSearchEverywhereContributor",
    "CommandsContributor",
    "third.party",
    "TextSearchContributor",
    "DbSETablesContributor",
    "UrlSearchEverywhereContributor",
    "Vcs.Git", "GitSearchEverywhereContributor",
    "RiderOnboardingSearchEverywhereContributor",
    "CalculatorSEContributor"
  ));

  private static final List<String> ourTabs = Arrays.asList("FileSearchEverywhereContributor",
                                                            "SearchEverywhereContributor.All",
                                                            "ClassSearchEverywhereContributor",
                                                            "ActionSearchEverywhereContributor",
                                                            "SymbolSearchEverywhereContributor",
                                                            "UrlSearchEverywhereContributor",
                                                            "DbSETablesContributor",
                                                            "TextSearchContributor",
                                                            "CalculatorSEContributor",
                                                            "Vcs.Git",
                                                            "third.party");
  public static final StringEventField CURRENT_TAB_FIELD = EventFields.String("currentTabId", ourTabs);

  public static final BooleanEventField IS_SPLIT = EventFields.Boolean("isSplit");

  public static final EventId3<String, AnActionEvent, Boolean> DIALOG_OPEN = GROUP.registerEvent("dialogOpen",
                                                                                                 CONTRIBUTOR_ID_FIELD,
                                                                                                 EventFields.InputEventByAnAction,
                                                                                                 IS_SPLIT);
  public static final VarargEventId TAB_SWITCHED = GROUP.registerVarargEvent("tabSwitched", CONTRIBUTOR_ID_FIELD,
                                                                             EventFields.InputEventByAnAction,
                                                                             EventFields.InputEventByMouseEvent,
                                                                             IS_SPLIT);
  public static final EventId1<AnActionEvent> GROUP_NAVIGATE = GROUP.registerEvent("navigateThroughGroups",
                                                                                   EventFields.InputEventByAnAction);
  public static final EventId1<Boolean> DIALOG_CLOSED = GROUP.registerEvent("dialogClosed", IS_SPLIT);
  public static final IntEventField SELECTED_ITEM_NUMBER = EventFields.Int("selectedItemNumber");
  public static final BooleanEventField HAS_ONLY_SIMILAR_ELEMENT = EventFields.Boolean("hasOnlySimilarElement");
  public static final BooleanEventField IS_ELEMENT_SEMANTIC = EventFields.Boolean("isElementSemantic");

  public static final VarargEventId CONTRIBUTOR_ITEM_SELECTED = GROUP.registerVarargEvent(
    "contributorItemChosen", CONTRIBUTOR_ID_FIELD, EventFields.Language, CURRENT_TAB_FIELD, SELECTED_ITEM_NUMBER,
    HAS_ONLY_SIMILAR_ELEMENT, IS_ELEMENT_SEMANTIC, IS_SPLIT
  );
  public static final EventId MORE_ITEM_SELECTED = GROUP.registerEvent("moreItemChosen");
  public static final IntEventField ITEM_NUMBER_BEFORE_MORE = EventFields.Int("itemsNumberBeforeMore");
  public static final BooleanEventField IS_ONLY_MORE = EventFields.Boolean("isOnlyMore");
  public static final VarargEventId MORE_ITEM_SHOWN = GROUP.registerVarargEvent("moreItemShown", ITEM_NUMBER_BEFORE_MORE, IS_ONLY_MORE);
  public static final EventId HAS_ONLY_SIMILAR_ITEM_SHOWN = GROUP.registerEvent("hasOnlySimilarItemShown");
  public static final EventId COMMAND_USED = GROUP.registerEvent("commandUsed");

  public static final VarargEventId COMMAND_COMPLETED = GROUP.registerVarargEvent("commandCompleted",
                                                                                  EventFields.InputEventByAnAction);
  public static final IntEventField TYPED_SYMBOL_KEYS = EventFields.Int("typedSymbolKeys");
  public static final IntEventField TYPED_NAVIGATION_KEYS = EventFields.Int("typedNavigationKeys");
  public static final LongEventField TIME_TO_FIRST_RESULT = EventFields.Long("timeToFirstResult");
  public static final StringEventField FIRST_TAB_ID = EventFields.String("firstTabId", ourTabs);
  public static final LongEventField TIME_TO_FIRST_RESULT_LAST_QUERY = EventFields.Long("timeToFirstResultLastQuery");
  public static final StringEventField LAST_TAB_ID = EventFields.String("lastTabId", ourTabs);
  public static final LongEventField DURATION_MS = EventFields.Long("durationMs");
  public static final LongEventField DURATION_FROM_ACTION_START_MS = EventFields.Long("durationFromActionStartMs");
  public static final LongEventField DURATION_TO_FIRST_RESULT_FROM_ACTION_START_MS = EventFields.Long("durationToFirstResultFromActionStartMs");
  public static final LongEventField DURATION_TO_FIRST_RESULT_LAST_QUERY_FROM_ACTION_START_MS = EventFields.Long("durationToFirstResultLastQueryFromActionStartMs");
  public static final BooleanEventField DIALOG_WAS_CANCELLED = EventFields.Boolean("dialogWasCancelled");
  public static final IntEventField ML_EXPERIMENT_VERSION = EventFields.Int("mlExperimentVersion");
  public static final IntEventField ML_EXPERIMENT_GROUP = EventFields.Int("mlExperimentGroup");
  public static final VarargEventId SESSION_FINISHED = GROUP.registerVarargEvent(
    "sessionFinished", TYPED_NAVIGATION_KEYS, TYPED_SYMBOL_KEYS,
    TIME_TO_FIRST_RESULT, FIRST_TAB_ID, TIME_TO_FIRST_RESULT_LAST_QUERY, LAST_TAB_ID, DURATION_MS,
    DURATION_FROM_ACTION_START_MS, DURATION_TO_FIRST_RESULT_FROM_ACTION_START_MS, DURATION_TO_FIRST_RESULT_LAST_QUERY_FROM_ACTION_START_MS,
    DIALOG_WAS_CANCELLED,
    ML_EXPERIMENT_GROUP, ML_EXPERIMENT_VERSION
  );

  public static final EventId1<Boolean> PREVIEW_SWITCHED = GROUP.registerEvent("previewSwitched", EventFields.Boolean("previewState"));
  public static final EventId1<Boolean> PREVIEW_CLOSED = GROUP.registerEvent("previewClosed", EventFields.Boolean("previewClosed"));

  public enum FuzzySearchResult {
    PROCESS_COMPLETE, PROCESS_STOPPED, EMPTY_PATTERN
  }

  public enum FuzzySearchType {
    FUZZY_FILE_SEARCH
  }

  public static final EnumEventField<FuzzySearchType> FUZZY_SEARCH_TYPE = EventFields.Enum("fuzzySearchType", FuzzySearchType.class);
  public static final IntEventField FUZZY_SEARCH_TOTAL_RESULTS = EventFields.Int("fuzzySearchTotalResults");
  public static final EnumEventField<FuzzySearchResult> FUZZY_SEARCH_RESULT =
    EventFields.Enum("fuzzySearchResult", FuzzySearchResult.class);
  public static final IdeActivityDefinition FUZZY_SEARCH_ACTIVITY =
    GROUP.registerIdeActivity("fuzzySearch", new EventField[]{FUZZY_SEARCH_TYPE},
                              new EventField[]{FUZZY_SEARCH_TOTAL_RESULTS, FUZZY_SEARCH_RESULT});

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  public static @NotNull String getReportableContributorID(@NotNull SearchEverywhereContributor<?> contributor) {
    //noinspection rawtypes
    Class<? extends SearchEverywhereContributor> clazz = contributor.getClass();
    PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfo(clazz);
    return pluginInfo.isDevelopedByJetBrains() ? contributor.getSearchProviderId() : NOT_REPORTABLE_ID;
  }

  @ApiStatus.Internal
  public static @NotNull boolean isReportable(@NotNull Object object) {
    //noinspection rawtypes
    Class<?> clazz = object.getClass();
    PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfo(clazz);
    return pluginInfo.isDevelopedByJetBrains();
  }
}
