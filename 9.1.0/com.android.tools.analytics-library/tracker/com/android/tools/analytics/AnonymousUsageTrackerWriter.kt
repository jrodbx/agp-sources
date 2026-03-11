/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting
import com.google.protobuf.Message.Builder
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ProductDetails
import java.nio.file.Path
import java.util.concurrent.ScheduledExecutorService

@VisibleForTesting
class AnonymousUsageTrackerWriter(scheduler: ScheduledExecutorService, spoolLocation: Path) :
  JournalingUsageTracker(scheduler, spoolLocation) {

  override fun processEvent(studioEvent: AndroidStudioEvent.Builder): Builder? {
    AnonymousUsageTrackerWriter.processEvent(studioEvent)
    return super.processEvent(studioEvent)
  }

  companion object {
    fun processEvent(studioEvent: AndroidStudioEvent.Builder) {
      studioEvent.studioSessionId = UsageTracker.sessionId
      studioEvent.ideBrand = UsageTracker.ideBrand

      if (UsageTracker.version != null && !studioEvent.hasProductDetails()) {
        studioEvent.setProductDetails(ProductDetails.newBuilder().setVersion(UsageTracker.version!!))
      }

      if (UsageTracker.ideaIsInternal) {
        studioEvent.ideaIsInternal = true
      }

      UsageTracker.listener(studioEvent)
    }
  }
}
