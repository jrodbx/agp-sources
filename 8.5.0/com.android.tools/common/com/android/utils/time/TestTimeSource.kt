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
package com.android.utils.time

import com.android.utils.time.TimeSource.TimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class) // Duration is not experimental as of 1.6
class TestTimeSource : TimeSource {
    var nowMs = 0L

    override fun markNow(): TimeMark = TestTimeMark(this, nowMs)

    /** Retrieves the current reading of this [TestTimeSource]. */
    fun read(): Long = nowMs

    /**
     * Advances the current reading value of this time source by the specified [duration].
     *
     * Only millisecond-level precision will be observed.
     */
    operator fun plusAssign(duration: Duration) {
        nowMs += duration.inWholeMilliseconds
    }

    private class TestTimeMark(private val source: TestTimeSource, private val markedMs: Long) :  TimeMark {
        override fun elapsedNow(): Duration = (source.read() - markedMs).milliseconds
    }
}
