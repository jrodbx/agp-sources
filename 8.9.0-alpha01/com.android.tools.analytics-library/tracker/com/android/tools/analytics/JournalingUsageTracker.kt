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
import com.google.wireless.android.play.playlog.proto.ClientAnalytics
import java.io.IOException
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * a UsageTracker that uses a spool to journal the logs tracked. This tracker can be used in both
 * long-running as well as short-lived command-line tooling to track usage analytics. All analytics
 * get written out to a well-known spool location and will be processed by a separate service in
 * Android Studio for publication. Normal usage calls [UsageTracker.getInstance] to get access to
 * the UsageTracker. This will automatically be set to the correct instance based on the user
 * choosing to opt-in to reporting usage analytics to Google or not.
 *
 * Spool files are binary files protobuf using delimited streams
 * https://developers.google.com/protocol-buffers/docs/techniques#streaming
 *
 * For unittests please use TestUsageTracker. Only for integration tests that need .trk files to be
 * generated, use the JournalingUsageTracker.
 */
@VisibleForTesting
class JournalingUsageTracker
/**
 * Creates an instance of JournalingUsageTracker. Ensures spool location is available and locks the
 * first journaling file.
 *
 * @param scheduler used for scheduling writing logs and closing & starting new files on
 *   timeout/size limits.
 */
(
  /** Gets the scheduler used by this tracker. */
  val scheduler: ScheduledExecutorService,
  private val spoolLocation: Path,
) : UsageTrackerWriter() {

  // lock for blocking flush calls. To avoid deadlocks, the order of
  // locking is: flushLock (if needed), gate.
  private val flushLock = ReentrantLock()
  private val gate = Any()
  private var lock: FileLock? = null
  private var channel: FileChannel? = null
  private var outputStream: OutputStream? = null
  private var currentLogCount = 0
  private var journalTimeout: ScheduledFuture<*>? = null
  private var scheduleVersion = 0

  @Volatile private var state = State.Open
  private val flushScheduled = AtomicBoolean(false)
  private val pendingEvents: Queue<ClientAnalytics.LogEvent.Builder> =
    ConcurrentLinkedQueue<ClientAnalytics.LogEvent.Builder>()

  private enum class State {
    Open,
    Closed,
    Broken,
  }

  init {
    try {
      newTrackFile()
    } catch (e: IOException) {
      throw RuntimeException("Unable to initialize first usage tracking spool file", e)
    }
  }

  /** Creates a new track file with a guid name (for uniqueness) and locks it for writing. */
  @Throws(IOException::class)
  private fun newTrackFile() {
    val spoolFile = Paths.get(spoolLocation.toString(), UUID.randomUUID().toString() + ".trk")
    Files.createDirectories(spoolFile.parent)

    channel =
      FileChannel.open(
        spoolFile,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.DSYNC,
      )
    outputStream = Channels.newOutputStream(channel!!)

    try {
      lock = channel!!.tryLock()
    } catch (e: OverlappingFileLockException) {
      closeTrackFile()
      throw IOException("Unable to lock usage tracking spool file", e)
    }

    if (lock == null) {
      closeTrackFile()
      throw IOException("Unable to lock usage tracking spool file, file already locked")
    }
    currentLogCount = 0
  }

  /** Closes the track file currently open for writing. */
  @Throws(IOException::class)
  private fun closeTrackFile() {
    var ex: IOException? = null

    try {
      lock?.release()
    } catch (e: IOException) {
      ex = e
    }

    lock = null

    try {
      channel?.close()
    } catch (e: IOException) {
      if (ex == null) {
        ex = e
      } else {
        ex.addSuppressed(e)
      }
    }

    channel = null

    try {
      outputStream?.close()
    } catch (e: IOException) {
      if (ex == null) {
        ex = e
      } else {
        ex.addSuppressed(e)
      }
    }

    outputStream = null

    // Rethrow first encountered exception, if any.
    if (ex != null) {
      throw ex
    }
  }

  override fun logDetails(logEvent: ClientAnalytics.LogEvent.Builder) {
    if (state != State.Open) {
      return
    }
    pendingEvents.add(logEvent)
    scheduleFlush()
  }

  /**
   * Writes any pending events to the currently open file.
   *
   * @throws IOException on failure, and the UsageTracker will be closed in those cases.
   */
  override fun flush() {
    flushLock.withLock { flushImpl() }
    // If there are new events to handle, schedule a new flush event
    if (pendingEvents.isNotEmpty()) {
      scheduleFlush()
    }
  }

  /** Schedules a flush if one is not running and not scheduled yet. */
  private fun scheduleFlush() {
    if (!flushLock.isLocked && flushScheduled.compareAndSet(false, true)) {
      scheduler.submit {
        try {
          tryFlush()
        } finally {
          flushScheduled.set(false)
        }
      }
    }
  }

  /** Triggers flush if one is not currently running. */
  private fun tryFlush() {
    if (!flushLock.tryLock()) return

    try {
      flushImpl()
    } finally {
      flushLock.unlock()
    }
    // If there are new events to handle, schedule a new flush event
    if (pendingEvents.isNotEmpty()) {
      scheduleFlush()
    }
  }

  private fun flushImpl() {
    while (true) {
      synchronized(gate) {
        val logEvent = pendingEvents.poll() ?: return
        if (state != State.Open) {
          return
        }
        try {
          logEvent.build().writeDelimitedTo(outputStream!!)
        } catch (exception: IOException) {
          closeAsBroken()
          throw IOException("Failed to write log event", exception)
        }

        currentLogCount++
        if (UsageTracker.maxJournalSize in 1..currentLogCount) {
          switchTrackFile()
          if (journalTimeout != null) {
            // Reset the max journal time as we just reset the logs.
            scheduleJournalTimeout(UsageTracker.maxJournalTime)
          }
        }
      }
    }
  }

  private fun closeAsBroken() {
    try {
      close()
    } catch (ignored: Exception) {}

    state = State.Broken
  }

  /**
   * Closes the trackfile currently used for writing and creates a brand new one and opens that one
   * for writing. <br></br>
   *
   * @return `true` when succeeds, otherwise `false`. If there was an error during the switch,
   *   JournalingUsageTracker is left in `Broken` state and stops logging/reporting any new events.
   */
  private fun switchTrackFile(): Boolean {
    try {
      closeTrackFile()
      newTrackFile()
      return true
    } catch (e: IOException) {
      closeAsBroken()
      return false
    }
  }

  /**
   * Closes the UsageTracker (closes current tracker file, disables scheduling of timeout, drops any
   * pending logs and disables new logs from being posted).
   */
  @Throws(Exception::class)
  override fun close() {
    synchronized(gate) {
      state = State.Closed
      this.journalTimeout?.cancel(false)
      closeTrackFile()
    }
  }

  /** Schedules a timeout at which point the journal will be */
  override fun scheduleJournalTimeout(maxJournalTime: Long) {
    val currentScheduleVersion = ++scheduleVersion
    journalTimeout?.cancel(false)
    journalTimeout =
      scheduler.schedule(
        {
          synchronized(gate) {
            if (state != State.Open) {
              return@schedule
            }
            if (currentLogCount > 0) {
              switchTrackFile()
            }
            // only schedule next beat if we're still the authority.
            if (scheduleVersion == currentScheduleVersion) {
              scheduleJournalTimeout(maxJournalTime)
            }
          }
        },
        maxJournalTime,
        TimeUnit.NANOSECONDS,
      )
  }
}
