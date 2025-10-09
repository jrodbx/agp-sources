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

import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * UsageTracker is an api to report usage of features. This data is used to improve future versions
 * of Android Studio and related tools.
 *
 * The tracker has an API to logDetails usage (in the form of protobuf messages). A separate system
 * called the Analytics Publisher takes the logs and sends them to Google's servers for analysis.
 */
object UsageTracker {

  private val gate = Any()
  private val LOG = Logger.getLogger(UsageTracker.javaClass.name)

  var initialized = false
    private set

  private var exceptionThrown = false

  @VisibleForTesting @JvmStatic var sessionId = UUID.randomUUID().toString()

  @JvmStatic
  @VisibleForTesting
  var writer: UsageTrackerWriter<AndroidStudioEvent.Builder> = NullUsageTracker
  private var isTesting: Boolean = false

  /**
   * Indicates whether this UsageTracker has a maximum size at which point logs need to be flushed.
   * Zero or less indicates no maximum size at which to flush.
   */
  /*
   * Sets a maximum size at which point logs need to be flushed. Zero or less indicates no
   * flushing until @{link #close()} is called.
   */
  @JvmStatic var maxJournalSize: Int = 0

  /**
   * Indicates whether this UsageTracker has a timeout at which point logs need to be flushed. Zero
   * or less indicates no timeout is set.
   *
   * @return timeout in nano-seconds.
   */
  @JvmStatic
  var maxJournalTime: Long = 0
    private set

  /**
   * The version specified for this UsageTracker. This version when specified is used to populate
   * the product_details.version field of AndroidStudioEvent at time of logging As the version of
   * the product generating the event can be different of the version uploading the event.
   */
  @JvmStatic var version: String? = null

  @JvmStatic
  /** Set when Android Studio is running in development mode. */
  var ideaIsInternal = false

  /** IDE brand specified for this UsageTracker. */
  @JvmStatic
  var ideBrand: AndroidStudioEvent.IdeBrand = AndroidStudioEvent.IdeBrand.UNKNOWN_IDE_BRAND

  /**
   * Gets the global writer to the provided tracker writer so tests can provide their own
   * UsageTrackerWriter implementation. NOTE: Should only be used from Usage Tracker tests.
   */
  @JvmStatic
  val writerForTest: UsageTrackerWriter<AndroidStudioEvent.Builder>
    @VisibleForTesting
    get() {
      synchronized(gate) {
        return writer
      }
    }

  /**
   * Sets a timeout at which point logs need to be flushed. Zero or less indicates no timeout should
   * be used.
   */
  @JvmStatic
  fun setMaxJournalTime(duration: Long, unit: TimeUnit) {
    runIfUsageTrackerUsable {
      maxJournalTime = unit.toNanos(duration)
      writer.scheduleJournalTimeout(maxJournalTime)
    }
  }

  @JvmStatic
  private fun runIfUsageTrackerUsable(callback: () -> Unit) {
    var throwable: Throwable? = null
    synchronized(gate) {
      if (exceptionThrown) {
        return
      }
      ensureInitialized()
      try {
        callback()
      } catch (t: Throwable) {
        exceptionThrown = true
        throwable = t
      }
    }
    if (throwable != null) {
      try {
        LOG.log(Level.SEVERE, throwable) { "UsageTracker call failed" }
      } catch (ignored: Throwable) {}
    }
  }

  /** Logs usage data provided in the @{link AndroidStudioEvent}. */
  @JvmStatic
  fun log(studioEvent: AndroidStudioEvent.Builder) {
    runIfUsageTrackerUsable { writer.logNow(studioEvent) }
  }

  /** Logs usage data provided in the @{link AndroidStudioEvent}. */
  @JvmStatic
  fun log(studioEvent: AndroidStudioEvent) {
    runIfUsageTrackerUsable { writer.logNow(studioEvent.toBuilder()) }
  }

  /** Logs usage data provided in the @{link AndroidStudioEvent} with provided event time. */
  @JvmStatic
  fun log(eventTimeMs: Long, studioEvent: AndroidStudioEvent.Builder) {
    runIfUsageTrackerUsable { writer.logAt(eventTimeMs, studioEvent) }
  }

  private fun ensureInitialized() {
    if (!initialized && java.lang.Boolean.getBoolean("idea.is.internal")) {
      // Android Studio Developers: If you hit this exception, you're trying to log metrics before
      // our metrics system has been initialized. Please reach out to the owners of this code
      // to figure out how best to do your logging instead of sending it into the void.
      throw RuntimeException("call to UsageTracker before initialization")
    }
  }

  /**
   * Initializes a [UsageTrackerWriter] for use throughout this process based on user opt-in and
   * other settings.
   */
  @JvmStatic
  fun initialize(
    scheduler: ScheduledExecutorService
  ): UsageTrackerWriter<AndroidStudioEvent.Builder> {
    if (isTesting) {
      // @coverage:off
      return writer
      // @coverage:on
    }
    synchronized(gate) {
      val oldInstance = writer
      initializeTrackerWriter(scheduler)
      try {
        oldInstance.close()
      } catch (ex: Exception) {
        throw RuntimeException("Unable to close usage tracker", ex)
      }

      initialized = true
      return writer
    }
  }

  /**
   * Compared with [initialize], this function avoids re-initialize [UsageTracker] when it is
   * already initialized.
   *
   * Note this function should not be used by Studio because Studio needs to be able to
   * re-initialize in the same process if the user changes the opt in settings.
   */
  @JvmStatic
  fun initializeIfNotPresent(
    scheduler: ScheduledExecutorService
  ): UsageTrackerWriter<AndroidStudioEvent.Builder> {
    synchronized(gate) {
      if (initialized) {
        return writer
      }
      initializeTrackerWriter(scheduler)

      initialized = true
      return writer
    }
  }

  /** initializes or updates AnalyticsSettings into a disabled state. */
  @JvmStatic
  fun disable() {
    deinitialize()
    initialized = true
  }

  @JvmStatic
  fun deinitialize() {
    synchronized(gate) {
      initialized = false
      try {
        // The writer may have pending events which will be dropped by close
        // call flush() to write them before closing.
        writer.flush()
        writer.close()
      } catch (ex: Exception) {
        throw RuntimeException("Unable to close usage tracker", ex)
      } finally {
        writer = NullUsageTracker
      }
    }
  }

  /**
   * Sets the global writer to the provided tracker so tests can provide their own UsageTracker
   * implementation. NOTE: Should only be used from tests.
   */
  @VisibleForTesting
  @JvmStatic
  fun setWriterForTest(
    tracker: UsageTrackerWriter<AndroidStudioEvent.Builder>
  ): UsageTrackerWriter<AndroidStudioEvent.Builder> {
    synchronized(gate) {
      isTesting = true
      initialized = true
      exceptionThrown = false
      val old = writer
      writer = tracker
      return old
    }
  }

  /**
   * resets the global writer to the null usage tracker, to clean state in tests. NOTE: Should only
   * be used from tests.
   */
  @VisibleForTesting
  @JvmStatic
  fun cleanAfterTesting() {
    isTesting = false
    writer = NullUsageTracker
    initialized = false
    exceptionThrown = false
  }

  private fun initializeTrackerWriter(scheduler: ScheduledExecutorService) {
    if (AnalyticsSettings.optedIn) {
      try {
        writer = AnonymousUsageTrackerWriter(scheduler, Paths.get(AnalyticsPaths.spoolDirectory))
      } catch (ex: RuntimeException) {
        writer = NullUsageTracker
        throw ex
      }
    } else {
      writer = NullUsageTracker
    }
  }

  var listener: (event: AndroidStudioEvent.Builder) -> Unit = {}
}
