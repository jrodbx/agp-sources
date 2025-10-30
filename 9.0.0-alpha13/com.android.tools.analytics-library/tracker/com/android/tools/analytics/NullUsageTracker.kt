/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
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

/**
 * A [UsageTracker] that does not report any logs. Used when the user opts-out of reporting usage
 * analytics to Google.
 */
abstract class NullUsageTrackerBase<T : GeneratedMessageV3.Builder<T>> : UsageTrackerWriter<T>() {

  override fun logDetails(logEvent: ClientAnalytics.LogEvent.Builder) {}

  override fun close() {}

  override fun flush() {}

  override fun processMessage(eventTimeMs: Long, studioEvent: T) {}
}

object NullUsageTracker : NullUsageTrackerBase<AndroidStudioEvent.Builder>() {}
