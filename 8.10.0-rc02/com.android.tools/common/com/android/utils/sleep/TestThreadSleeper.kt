/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.utils.sleep

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime

/** Implementation of [ThreadSleeper] for use in tests. */
class TestThreadSleeper: ThreadSleeper() {
    private val sleeps: MutableList<Pair<Long, Int>> = mutableListOf()
    override fun doSleep(millis: Long, nanos: Int) {
        sleeps.add(millis to nanos)
    }

    val sleepArguments: List<Pair<Long, Int>>
        get() = sleeps

    @OptIn(ExperimentalTime::class) // For Duration which is no longer experimental
    @get:JvmSynthetic
    val sleepDurations: List<Duration>
        get() = sleeps.map { (m, n) -> m.milliseconds + n.nanoseconds }

    @OptIn(ExperimentalTime::class) // For Duration which is no longer experimental
    @get:JvmSynthetic
    val totalTimeSlept: Duration
        get() = if (sleeps.isEmpty()) Duration.ZERO else sleepDurations.reduce(Duration::plus)

    @OptIn(ExperimentalTime::class) // For Duration which is no longer experimental
    val totalMillisecondsSlept: Long
        get() = totalTimeSlept.inWholeMilliseconds

    fun reset() {
        sleeps.clear()
    }
}
