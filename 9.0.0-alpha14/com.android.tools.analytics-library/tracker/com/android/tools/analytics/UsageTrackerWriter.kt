/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.google.protobuf.GeneratedMessageV3
import com.google.wireless.android.play.playlog.proto.ClientAnalytics
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import java.io.Flushable

/**
 * UsageTrackerWriter is an api to report usage of features. This data is used to improve future
 * versions of Android Studio and related tools.
 *
 * The tracker has an API to logDetails usage (in the form of protobuf messages). A separate system
 * called the Analytics Publisher takes the logs and sends them to Google's servers for analysis.
 */
abstract class UsageTrackerWriter<T : GeneratedMessageV3.Builder<T>> : AutoCloseable, Flushable {

  open fun scheduleJournalTimeout(maxJournalTime: Long) {}

  /** Logs usage data provided in the [AndroidStudioEvent]. */
  fun logNow(studioEvent: T) {
    logAt(AnalyticsSettings.dateProvider.now().time, studioEvent)
  }

  /** Logs usage data provided in the [AndroidStudioEvent] with provided event time. */
  fun logAt(eventTimeMs: Long, studioEvent: T) {
    processMessage(eventTimeMs, studioEvent)
    logDetails(
      ClientAnalytics.LogEvent.newBuilder()
        .setEventTimeMs(eventTimeMs)
        .setSourceExtension(studioEvent.build().toByteString())
    )
  }

  /**
   * Logs usage data provided in the [ClientAnalytics.LogEvent]. Normally using {#log} is preferred
   * please talk to this code's author if you need [.logDetails] instead.
   */
  abstract fun logDetails(logEvent: ClientAnalytics.LogEvent.Builder)

  abstract fun processMessage(eventTimeMs: Long, studioEvent: T)
}
