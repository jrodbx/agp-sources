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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/**
 * A source of time for measuring time intervals.
 *
 * The only operation provided by the time source is [markNow]. It returns a [TimeMark], which can be used to query the elapsed time later.
 */
@OptIn(ExperimentalTime::class)  // Only for Duration which is no longer experimental.
interface TimeSource {
    /**
     * Marks a point in time on this time source.
     *
     * The returned [TimeMark] instance encapsulates the captured time point and allows querying
     * the duration of time interval [elapsed][TimeMark.elapsedNow] from that point.
     */
    fun markNow(): TimeMark

    /** The basic time source available in the platform. */
    object Monotonic : TimeSource {
        override fun markNow(): ValueTimeMark = ValueTimeMark(System.currentTimeMillis())
    }

    /**
     * Represents a time point notched on a particular [TimeSource]. Remains bound to the time source it was taken from
     * and allows querying for the duration of time elapsed from that point (see the function [elapsedNow]).
     */
    interface TimeMark {
        /** Returns the amount of time passed from this mark measured with the time source from which this mark was taken. */
        fun elapsedNow(): Duration

        /** Returns a time mark on the same time source that is ahead of this time mark by the specified [duration]. */
        operator fun plus(duration: Duration): TimeMark = AdjustedTimeMark(this, duration)

        /** Returns a time mark on the same time source that is behind this time mark by the specified [duration]. */
        operator fun minus(duration: Duration): TimeMark = plus(-duration)

        /** Returns true if this time mark has passed according to the time source from which this mark was taken. */
        fun hasPassedNow(): Boolean = !elapsedNow().isNegative()

        /** Returns false if this time mark has not passed according to the time source from which this mark was taken. */
        fun hasNotPassedNow(): Boolean = elapsedNow().isNegative()
    }

    /**
     * A specialized [TimeMark] implemented as an inline value class wrapping a [Long].
     *
     * The operations [plus] and [minus] are also specialized to return [ValueTimeMark] type.
     */
    @JvmInline
    value class ValueTimeMark internal constructor(internal val millis: Long) : TimeMark {
        override fun elapsedNow(): Duration = (System.currentTimeMillis() - millis).milliseconds
        override fun plus(duration: Duration): ValueTimeMark = ValueTimeMark(millis + duration.inWholeMilliseconds)
        override fun minus(duration: Duration): ValueTimeMark = ValueTimeMark(millis - duration.inWholeMilliseconds)
        override fun hasPassedNow(): Boolean = !elapsedNow().isNegative()
        override fun hasNotPassedNow(): Boolean = elapsedNow().isNegative()
    }

    private class AdjustedTimeMark(val mark: TimeMark, val adjustment: Duration) : TimeMark {
        override fun elapsedNow(): Duration = mark.elapsedNow() - adjustment
        override fun plus(duration: Duration): TimeMark = AdjustedTimeMark(mark, adjustment + duration)
    }
}
