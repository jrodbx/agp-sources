/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.analytics

import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind
import com.google.wireless.android.sdk.stats.AndroidStudioEventLoggedIn
import com.google.wireless.android.sdk.stats.AppLinksAssistantEvent
import com.google.wireless.android.sdk.stats.AppLinksAssistantEventLoggedIn
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEvent.AiInsightSource
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEventLoggedIn
import com.google.wireless.android.sdk.stats.AppQualityInsightsUsageEventLoggedIn.AiInsightSource as AiInsightSourceLoggedIn
import com.google.wireless.android.sdk.stats.DirectAccessUsageEvent
import com.google.wireless.android.sdk.stats.DirectAccessUsageEventLoggedIn
import com.google.wireless.android.sdk.stats.PlayPolicyInsightsUsageEvent
import com.google.wireless.android.sdk.stats.PlayPolicyInsightsUsageEventLoggedIn
import com.google.wireless.android.sdk.stats.PromptLibraryEvent
import com.google.wireless.android.sdk.stats.PromptLibraryEventLoggedIn
import com.google.wireless.android.sdk.stats.SmlChatBotEvent
import com.google.wireless.android.sdk.stats.SmlChatBotEventLoggedIn
import com.google.wireless.android.sdk.stats.SmlCompletionEvent
import com.google.wireless.android.sdk.stats.SmlCompletionEventLoggedIn
import com.google.wireless.android.sdk.stats.SmlConfigurationEvent
import com.google.wireless.android.sdk.stats.SmlConfigurationEventLoggedIn
import com.google.wireless.android.sdk.stats.SmlTransformEvent
import com.google.wireless.android.sdk.stats.SmlTransformEventLoggedIn
import com.google.wireless.android.sdk.stats.StudioCoreGeminiActionsEvent
import com.google.wireless.android.sdk.stats.StudioCoreGeminiActionsEventLoggedIn
import com.google.wireless.android.sdk.stats.StudioLabsEvent
import com.google.wireless.android.sdk.stats.StudioLabsEventLoggedIn
import com.google.wireless.android.sdk.stats.TestScenarioEvent
import com.google.wireless.android.sdk.stats.TestScenarioEventLoggedIn
import com.google.wireless.android.sdk.stats.UIActionStats
import com.google.wireless.android.sdk.stats.UIActionStatsLoggedIn

object EventTranslator {

  val ACTION_CLASS_NAMES: Set<String> =
    setOf(
      "GenerateComposePreviewAction",
      "GenerateComposePreviewsForFileAction",
      "GenerateComposeSampleDataAction",
      "SendPreviewToStudioBotAction",
    )

  fun translate(event: AndroidStudioEvent.Builder): AndroidStudioEventLoggedIn.Builder? {
    val builder = AndroidStudioEventLoggedIn.newBuilder()
    when (event.kind) {
      EventKind.APP_LINKS_ASSISTANT_STATS -> {
        builder.appLinksAssistantEvent =
          translateAppLinksAssistantEvent(event.appLinksAssistantEvent)
      }

      EventKind.APP_QUALITY_INSIGHTS_USAGE -> {
        builder.appQualityInsightsUsageEvent =
          translateAppQualityInsightUsageEvent(event.appQualityInsightsUsageEvent)
      }

      EventKind.DIRECT_ACCESS_USAGE_EVENT -> {
        builder.directAccessUsageEvent =
          translateDirectAccessUsageEvent(event.directAccessUsageEvent)
      }

      EventKind.SML_COMPLETION_EVENT -> {
        builder.smlCompletionEvent = translateSmlCompletionEvent(event.smlCompletionEvent)
      }

      EventKind.SML_CODE_TRANSFORMATION_EVENT -> {
        builder.smlTransformEvent = translateSmlTransformEvent(event.smlTransformEvent)
      }

      EventKind.SML_CHATBOT_EVENT -> {
        builder.smlChatBotEvent = translateSmlChatBotEvent(event.smlChatBotEvent)
      }

      EventKind.SML_CONFIGURATION_EVENT -> {
        builder.smlConfigurationEvent = translateSmlConfigurationEvent(event.smlConfigurationEvent)
      }

      EventKind.TEST_SCENARIO_EVENT -> {
        builder.testScenarioEvent = translateTestScenarioEvent(event.testScenarioEvent)
      }

      EventKind.STUDIO_CORE_GEMINI_ACTIONS -> {
        builder.androidStudioCoreGeminiActionsEvent =
          translateStudioCoreGeminiActionsEvent(event.androidStudioCoreGeminiActionsEvent)
      }

      EventKind.STUDIO_LABS_EVENT -> {
        builder.studioLabsEvent = translateStudioLabsEvent(event.studioLabsEvent)
      }

      EventKind.PROMPT_LIBRARY_EVENT -> {
        builder.promptLibraryEvent = translatePromptLibraryEvent(event.promptLibraryEvent)
      }

      EventKind.PLAY_POLICY_INSIGHTS_USAGE_EVENT -> {
        builder.playPolicyInsightsUsageEvent =
          translatePlayPolicyInsightsUsageEvent(event.playPolicyInsightsUsageEvent)
      }

      EventKind.STUDIO_UI_ACTION_STATS -> {
        builder.uiActionStats = translateUIActionStats(event.uiActionStats)
      }

      else -> {
        return null
      }
    }
    return builder
  }

  private fun translateAppQualityInsightUsageEvent(
    event: AppQualityInsightsUsageEvent
  ): AppQualityInsightsUsageEventLoggedIn {
    val builder = AppQualityInsightsUsageEventLoggedIn.newBuilder()
    if (event.hasInsightFetchDetails()) {
      val detailsBuilder = AppQualityInsightsUsageEventLoggedIn.InsightFetchDetails.newBuilder()
      if (event.insightFetchDetails.hasSource()) {
        detailsBuilder.source =
          when (event.insightFetchDetails.source) {
            AiInsightSource.AI_INSIGHT_SOURCE_STUDIO_BOT ->
              AiInsightSourceLoggedIn.AI_INSIGHT_SOURCE_STUDIO_BOT

            AiInsightSource.AI_INSIGHT_SOURCE_CRASHLYTICS_TITAN ->
              AiInsightSourceLoggedIn.AI_INSIGHT_SOURCE_CRASHLYTICS_TITAN

            else -> AiInsightSourceLoggedIn.UNKNOWN_SOURCE
          }
      }
      builder.insightFetchDetails = detailsBuilder.build()
    }
    return builder.build()
  }

  private fun translateAppLinksAssistantEvent(
    event: AppLinksAssistantEvent
  ): AppLinksAssistantEventLoggedIn {
    val builder = AppLinksAssistantEventLoggedIn.newBuilder()
    if (event.hasEventSource()) {
      builder.eventSource =
        when (event.eventSource) {
          AppLinksAssistantEvent.EventSource.NEW_LINK_CREATION_SIDE_PANEL ->
            AppLinksAssistantEventLoggedIn.EventSource.NEW_LINK_CREATION_SIDE_PANEL

          AppLinksAssistantEvent.EventSource.LAUNCH_APP_LINKS_ASSISTANT ->
            AppLinksAssistantEventLoggedIn.EventSource.LAUNCH_APP_LINKS_ASSISTANT

          else -> AppLinksAssistantEventLoggedIn.EventSource.NEW_LINK_CREATION_SIDE_PANEL
        }
    }
    if (event.hasValidationSummary()) {
      builder.validationSummary =
        AppLinksAssistantEventLoggedIn.ValidationSummary.getDefaultInstance()
    }
    if (event.hasIntentFilterFix()) {
      builder.intentFilterFix = AppLinksAssistantEventLoggedIn.IntentFilterFix.getDefaultInstance()
    }
    return builder.build()
  }

  private fun translateDirectAccessUsageEvent(
    event: DirectAccessUsageEvent
  ): DirectAccessUsageEventLoggedIn {
    val builder = DirectAccessUsageEventLoggedIn.newBuilder()
    if (event.hasType()) {
      builder.type =
        when (event.type) {
          DirectAccessUsageEvent.DirectAccessUsageEventType.RESERVE_DEVICE ->
            DirectAccessUsageEventLoggedIn.DirectAccessUsageEventType.RESERVE_DEVICE

          DirectAccessUsageEvent.DirectAccessUsageEventType.CONNECT_DEVICE ->
            DirectAccessUsageEventLoggedIn.DirectAccessUsageEventType.CONNECT_DEVICE

          DirectAccessUsageEvent.DirectAccessUsageEventType.STREAM_STARTED ->
            DirectAccessUsageEventLoggedIn.DirectAccessUsageEventType.STREAM_STARTED

          DirectAccessUsageEvent.DirectAccessUsageEventType.EXTEND_RESERVATION ->
            DirectAccessUsageEventLoggedIn.DirectAccessUsageEventType.EXTEND_RESERVATION

          DirectAccessUsageEvent.DirectAccessUsageEventType.END_RESERVATION ->
            DirectAccessUsageEventLoggedIn.DirectAccessUsageEventType.END_RESERVATION

          DirectAccessUsageEvent.DirectAccessUsageEventType.DISCONNECT_DEVICE ->
            DirectAccessUsageEventLoggedIn.DirectAccessUsageEventType.DISCONNECT_DEVICE

          DirectAccessUsageEvent.DirectAccessUsageEventType.SERVICE_DEPRECATION ->
            DirectAccessUsageEventLoggedIn.DirectAccessUsageEventType.SERVICE_DEPRECATION

          DirectAccessUsageEvent.DirectAccessUsageEventType.OEM_LAB_DIALOG ->
            DirectAccessUsageEventLoggedIn.DirectAccessUsageEventType.OEM_LAB_DIALOG

          else -> DirectAccessUsageEventLoggedIn.DirectAccessUsageEventType.UNKNOWN_EVENT
        }
    }
    if (event.hasReserveDeviceDetails()) {
      val detailsBuilder = DirectAccessUsageEventLoggedIn.ReserveDeviceDetails.newBuilder()
      if (event.reserveDeviceDetails.hasSuccess()) {
        detailsBuilder.success = event.reserveDeviceDetails.success
      }
      builder.reserveDeviceDetails = detailsBuilder.build()
    }
    if (event.hasConnectDeviceDetails()) {
      val detailsBuilder = DirectAccessUsageEventLoggedIn.ConnectDeviceDetails.newBuilder()
      if (event.connectDeviceDetails.hasSuccess()) {
        detailsBuilder.success = event.connectDeviceDetails.success
      }
      builder.connectDeviceDetails = detailsBuilder.build()
    }
    if (event.hasStreamStartedDetails()) {
      val detailsBuilder = DirectAccessUsageEventLoggedIn.StreamStartedDetails.newBuilder()
      if (event.streamStartedDetails.hasSuccess()) {
        detailsBuilder.success = event.streamStartedDetails.success
      }
      builder.streamStartedDetails = detailsBuilder.build()
    }
    return builder.build()
  }

  private fun translateSmlCompletionEvent(event: SmlCompletionEvent): SmlCompletionEventLoggedIn {
    val builder = SmlCompletionEventLoggedIn.newBuilder()
    when {
      event.hasAggregate() -> {
        val aggregateBuilder = SmlCompletionEventLoggedIn.CompletionAggregateEvent.newBuilder()
        if (event.aggregate.hasCompletionsShown()) {
          aggregateBuilder.completionsShown = event.aggregate.completionsShown
        }
        if (event.aggregate.hasCompletionsAccepted()) {
          aggregateBuilder.completionsAccepted = event.aggregate.completionsAccepted
        }
        builder.aggregate = aggregateBuilder.build()
      }
    }
    return builder.build()
  }

  private fun translateSmlTransformEvent(event: SmlTransformEvent): SmlTransformEventLoggedIn {
    val builder = SmlTransformEventLoggedIn.newBuilder()
    when {
      event.hasRequest() ->
        builder.request = SmlTransformEventLoggedIn.TransformRequest.getDefaultInstance()

      event.hasResponse() ->
        builder.response = SmlTransformEventLoggedIn.TransformResponse.getDefaultInstance()

      event.hasShown() ->
        builder.shown = SmlTransformEventLoggedIn.TransformShown.getDefaultInstance()

      event.hasAccepted() ->
        builder.accepted = SmlTransformEventLoggedIn.TransformAccepted.getDefaultInstance()
    }
    if (event.hasTransformKind()) {
      builder.transformKind =
        when (event.transformKind) {
          SmlTransformEvent.TransformKind.CUSTOM -> SmlTransformEventLoggedIn.TransformKind.CUSTOM
          SmlTransformEvent.TransformKind.DOCUMENT ->
            SmlTransformEventLoggedIn.TransformKind.DOCUMENT

          SmlTransformEvent.TransformKind.MULTIMODAL_COMPOSE_PREVIEW ->
            SmlTransformEventLoggedIn.TransformKind.MULTIMODAL_COMPOSE_PREVIEW

          SmlTransformEvent.TransformKind.GENERATE_COMPOSE_PREVIEW ->
            SmlTransformEventLoggedIn.TransformKind.GENERATE_COMPOSE_PREVIEW

          SmlTransformEvent.TransformKind.GENERATE_INSIGHT_SUGGESTED_FIX ->
            SmlTransformEventLoggedIn.TransformKind.GENERATE_INSIGHT_SUGGESTED_FIX

          else -> SmlTransformEventLoggedIn.TransformKind.UNKNOWN
        }
    }
    return builder.build()
  }

  private fun translateSmlChatBotEvent(event: SmlChatBotEvent): SmlChatBotEventLoggedIn {
    val builder = SmlChatBotEventLoggedIn.newBuilder()
    when {
      event.hasResponse() -> {
        val responseBuilder = SmlChatBotEventLoggedIn.BotResponse.newBuilder()
        if (event.response.hasChatMode()) {
          responseBuilder.chatMode =
            when (event.response.chatMode) {
              SmlChatBotEvent.ChatMode.CHAT -> SmlChatBotEventLoggedIn.ChatMode.CHAT
              SmlChatBotEvent.ChatMode.AGENT_MODE -> SmlChatBotEventLoggedIn.ChatMode.AGENT_MODE
              SmlChatBotEvent.ChatMode.VERSION_UPGRADE_AGENT ->
                SmlChatBotEventLoggedIn.ChatMode.VERSION_UPGRADE_AGENT

              else -> SmlChatBotEventLoggedIn.ChatMode.OTHER_MODE
            }
        }
        builder.response = responseBuilder.build()
      }

      event.hasActionInvoked() -> {
        val actionInvokedBuilder = SmlChatBotEventLoggedIn.ActionInvoked.newBuilder()
        if (event.actionInvoked.hasAction()) {
          actionInvokedBuilder.action =
            when (event.actionInvoked.action) {
              SmlChatBotEvent.Action.MOVE_TO_EDITOR -> SmlChatBotEventLoggedIn.Action.MOVE_TO_EDITOR
              SmlChatBotEvent.Action.MOVE_TO_CARET -> SmlChatBotEventLoggedIn.Action.MOVE_TO_CARET
              SmlChatBotEvent.Action.MOVE_TO_NEW_FILE ->
                SmlChatBotEventLoggedIn.Action.MOVE_TO_NEW_FILE

              SmlChatBotEvent.Action.ADD_DEPENDENCY -> SmlChatBotEventLoggedIn.Action.ADD_DEPENDENCY
              SmlChatBotEvent.Action.BROWSE_TOPIC -> SmlChatBotEventLoggedIn.Action.BROWSE_TOPIC
              SmlChatBotEvent.Action.EXPLORE_IN_PLAYGROUND ->
                SmlChatBotEventLoggedIn.Action.EXPLORE_IN_PLAYGROUND

              SmlChatBotEvent.Action.MERGE_MANIFEST -> SmlChatBotEventLoggedIn.Action.MERGE_MANIFEST
              SmlChatBotEvent.Action.MERGE_SUGGESTION ->
                SmlChatBotEventLoggedIn.Action.MERGE_SUGGESTION

              SmlChatBotEvent.Action.INSERT_RESOURCES ->
                SmlChatBotEventLoggedIn.Action.INSERT_RESOURCES

              SmlChatBotEvent.Action.INSERT_NAME_SUGGESTIONS ->
                SmlChatBotEventLoggedIn.Action.INSERT_NAME_SUGGESTIONS

              SmlChatBotEvent.Action.COPY_BUTTON -> SmlChatBotEventLoggedIn.Action.COPY_BUTTON
              SmlChatBotEvent.Action.COPY_MANUAL -> SmlChatBotEventLoggedIn.Action.COPY_MANUAL
              else -> SmlChatBotEventLoggedIn.Action.INVALID
            }
        }
        builder.actionInvoked = actionInvokedBuilder.build()
      }
    }
    return builder.build()
  }

  private fun translateSmlConfigurationEvent(
    event: SmlConfigurationEvent
  ): SmlConfigurationEventLoggedIn {
    val builder = SmlConfigurationEventLoggedIn.newBuilder()
    if (event.hasSmlAvailable()) builder.smlAvailable = event.smlAvailable
    if (event.hasBotOnboardingStarted()) builder.botOnboardingStarted = event.botOnboardingStarted
    if (event.hasBotOnboardingCompleted())
      builder.botOnboardingCompleted = event.botOnboardingCompleted
    if (event.hasCompletionEnabled()) builder.completionEnabled = event.completionEnabled
    if (event.hasTransformEnabled()) builder.transformEnabled = event.transformEnabled
    if (event.hasProjectContextEnabled())
      builder.projectContextEnabled = event.projectContextEnabled
    if (event.hasAgentAutoAcceptEnabled())
      builder.agentAutoAcceptEnabled = event.agentAutoAcceptEnabled
    if (event.hasProductVariant()) {
      builder.productVariant =
        when (event.productVariant) {
          SmlConfigurationEvent.SmlProductVariant.PRODUCT_VARIANT_FREE ->
            SmlConfigurationEventLoggedIn.SmlProductVariant.PRODUCT_VARIANT_FREE

          SmlConfigurationEvent.SmlProductVariant.PRODUCT_VARIANT_BUSINESS ->
            SmlConfigurationEventLoggedIn.SmlProductVariant.PRODUCT_VARIANT_BUSINESS

          SmlConfigurationEvent.SmlProductVariant.PRODUCT_VARIANT_DASHER_FREE ->
            SmlConfigurationEventLoggedIn.SmlProductVariant.PRODUCT_VARIANT_DASHER_FREE

          SmlConfigurationEvent.SmlProductVariant.PRODUCT_VARIANT_DASHER_BUSINESS ->
            SmlConfigurationEventLoggedIn.SmlProductVariant.PRODUCT_VARIANT_DASHER_BUSINESS

          else -> SmlConfigurationEventLoggedIn.SmlProductVariant.PRODUCT_VARIANT_UNKNOWN
        }
    }
    return builder.build()
  }

  private fun translateTestScenarioEvent(event: TestScenarioEvent): TestScenarioEventLoggedIn {
    val builder = TestScenarioEventLoggedIn.newBuilder()
    when {
      event.hasRequest() -> {
        builder.request = TestScenarioEventLoggedIn.TestScenarioRequest.getDefaultInstance()
      }

      event.hasTestScenarioResult() -> {
        val resultBuilder = TestScenarioEventLoggedIn.TestScenarioResult.newBuilder()
        if (event.testScenarioResult.hasMisformattedResponseCount()) {
          resultBuilder.misformattedResponseCount =
            event.testScenarioResult.misformattedResponseCount
        }
        if (event.testScenarioResult.hasGenerationType()) {
          resultBuilder.generationType =
            when (event.testScenarioResult.generationType) {
              TestScenarioEvent.GenerationType.NEW_FILE ->
                TestScenarioEventLoggedIn.GenerationType.NEW_FILE

              TestScenarioEvent.GenerationType.EXISTING_FILE ->
                TestScenarioEventLoggedIn.GenerationType.EXISTING_FILE

              else -> TestScenarioEventLoggedIn.GenerationType.GENERATION_TYPE_UNDEFINED
            }
        }
        if (event.testScenarioResult.hasNumAccept()) {
          resultBuilder.numAccept = event.testScenarioResult.numAccept
        }
        if (event.testScenarioResult.hasNumDecline()) {
          resultBuilder.numDecline = event.testScenarioResult.numDecline
        }
        builder.testScenarioResult = resultBuilder.build()
      }
    }
    return builder.build()
  }

  private fun translateStudioCoreGeminiActionsEvent(
    event: StudioCoreGeminiActionsEvent
  ): StudioCoreGeminiActionsEventLoggedIn {
    val builder = StudioCoreGeminiActionsEventLoggedIn.newBuilder()
    if (event.hasAction()) {
      builder.action =
        when (event.action) {
          StudioCoreGeminiActionsEvent.Action.RENAME_VARIABLE ->
            StudioCoreGeminiActionsEventLoggedIn.Action.RENAME_VARIABLE

          StudioCoreGeminiActionsEvent.Action.RETHINK_VARIABLE_NAMES ->
            StudioCoreGeminiActionsEventLoggedIn.Action.RETHINK_VARIABLE_NAMES

          StudioCoreGeminiActionsEvent.Action.SUGGEST_COMMIT_MESSAGE ->
            StudioCoreGeminiActionsEventLoggedIn.Action.SUGGEST_COMMIT_MESSAGE

          else -> StudioCoreGeminiActionsEventLoggedIn.Action.UNKNOWN
        }
    }
    if (event.hasResultsCount()) builder.resultsCount = event.resultsCount
    if (event.hasResultsTaken()) builder.resultsTaken = event.resultsTaken
    return builder.build()
  }

  private fun translateStudioLabsEvent(event: StudioLabsEvent): StudioLabsEventLoggedIn {
    val builder = StudioLabsEventLoggedIn.newBuilder()
    if (event.hasPageInteraction()) {
      builder.pageInteraction =
        when (event.pageInteraction) {
          StudioLabsEvent.PageInteraction.OPENED -> StudioLabsEventLoggedIn.PageInteraction.OPENED
          StudioLabsEvent.PageInteraction.APPLY_BUTTON_CLICKED ->
            StudioLabsEventLoggedIn.PageInteraction.APPLY_BUTTON_CLICKED

          StudioLabsEvent.PageInteraction.CANCEL_BUTTON_CLICKED ->
            StudioLabsEventLoggedIn.PageInteraction.CANCEL_BUTTON_CLICKED

          StudioLabsEvent.PageInteraction.OK_BUTTON_CLICKED ->
            StudioLabsEventLoggedIn.PageInteraction.OK_BUTTON_CLICKED

          else -> StudioLabsEventLoggedIn.PageInteraction.UNKNOWN_INTERACTION
        }
    }
    return builder.build()
  }

  private fun translatePromptLibraryEvent(event: PromptLibraryEvent): PromptLibraryEventLoggedIn {
    val builder = PromptLibraryEventLoggedIn.newBuilder()
    if (event.hasUpdate()) {
      val updateBuilder = PromptLibraryEventLoggedIn.Update.newBuilder()
      if (event.update.hasPromptsInLibrary())
        updateBuilder.promptsInLibrary = event.update.promptsInLibrary
      if (event.update.hasRulesCount()) updateBuilder.rulesCount = event.update.rulesCount
      if (event.update.hasBuiltinsOverridesCount()) {
        updateBuilder.builtinsOverridesCount = event.update.builtinsOverridesCount
      }
      if (event.update.hasUserPromptsCount())
        updateBuilder.userPromptsCount = event.update.userPromptsCount
      builder.update = updateBuilder.build()
    }
    if (event.hasInvoke()) {
      builder.invoke = PromptLibraryEventLoggedIn.Invoke.getDefaultInstance()
    }
    return builder.build()
  }

  private fun translatePlayPolicyInsightsUsageEvent(
    event: PlayPolicyInsightsUsageEvent
  ): PlayPolicyInsightsUsageEventLoggedIn {
    val builder = PlayPolicyInsightsUsageEventLoggedIn.newBuilder()
    if (event.hasType()) {
      builder.type =
        when (event.type) {
          PlayPolicyInsightsUsageEvent.PlayPolicyInsightsUsageEventType.SERVICE_DEPRECATION ->
            PlayPolicyInsightsUsageEventLoggedIn.PlayPolicyInsightsUsageEventType
              .SERVICE_DEPRECATION

          PlayPolicyInsightsUsageEvent.PlayPolicyInsightsUsageEventType.BATCH_INSPECTION ->
            PlayPolicyInsightsUsageEventLoggedIn.PlayPolicyInsightsUsageEventType.BATCH_INSPECTION

          else ->
            PlayPolicyInsightsUsageEventLoggedIn.PlayPolicyInsightsUsageEventType.UNKNOWN_EVENT
        }
    }
    return builder.build()
  }

  private fun translateUIActionStats(event: UIActionStats): UIActionStatsLoggedIn? {
    val actionClassName = event.actionClassName?.takeIf { it in ACTION_CLASS_NAMES } ?: return null
    val builder = UIActionStatsLoggedIn.newBuilder()
    builder.actionClassName = actionClassName
    return builder.build()
  }
}
