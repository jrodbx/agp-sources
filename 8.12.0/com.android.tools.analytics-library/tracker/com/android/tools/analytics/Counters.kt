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

import java.lang.management.ManagementFactory
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/** The thread-safe registry of [Counter]'s. */
object Counters {

  private val counters = HashMap<String, Counter>()

  // Returns a snapshot of the collection of all ever accessed counters.
  @JvmStatic fun getAll(): Collection<Counter> = synchronized(counters) { counters.values.toList() }

  // Gets or creates a new [Counter] named [name].
  @JvmStatic
  fun get(name: String): Counter =
    synchronized(counters) { counters.getOrPut(name) { Counter(name) } }
}

/**
 * A named counter tracking:
 * - the total number of calls measured,
 * - the maximum time spent executing the measured code
 * - the total time spent executing the measured code. Time is measured as both wall and cpu time.
 */
class Counter internal constructor(val name: String) {

  private val totalCpu = AtomicLong()
  private val totalWall = AtomicLong()
  private val maxCpu = AtomicLong()
  private val maxWall = AtomicLong()
  private val count = AtomicInteger()

  val totalWallNanos: Long
    get() = totalWall.get()

  val maxWallNanos: Long
    get() = maxWall.get()

  val totalCount: Int
    get() = count.get()

  // Runs [block] and records the time spent executing it.
  fun <R> timeCallable(block: Callable<R>): R = time { block.call() }

  // Runs [block] and records the time spent executing it.
  fun timeRunnable(block: Runnable) = time { block.run() }

  // Resets the counter.
  // Note, the counters are reset individually, so that if a call to this method coincides with a
  // call to [time] method
  // it may result in partially recorded results.
  fun reset() {
    totalWall.set(0)
    maxWall.set(0)
    count.set(0)
  }

  private inline fun <R> time(block: () -> R): R {
    val startWall = currentTimeNano

    return try {
      block()
    } finally {
      val deltaWall = currentTimeNano - startWall
      totalWall.addAndGet(deltaWall)
      maxWall.updateAndGet { max(it, deltaWall) }
      count.incrementAndGet()
    }
  }

  override fun toString(): String = buildString {
    val totalCount = count.get()
    if (totalCount > 0) {
      val avgWallMicros = (totalWallNanos / totalCount) / 100 / 10.0
      val maxWallMicros = maxWallNanos / 100 / 10.0
      val totalWallMillis = totalWallNanos / 100_000 / 10.0
      append("Counter: ", name, " ")
      append("Count: ", totalCount, " ")
      append("AvgWall: ", avgWallMicros, "μs ")
      append("TotalWall: ", totalWallMillis, "ms ")
      append("MaxWall: ", maxWallMicros, "μs ")
      appendln()
    }
  }
}

private val threadMx = ManagementFactory.getThreadMXBean()
private val currentTimeNano: Long
  get() = System.nanoTime()
