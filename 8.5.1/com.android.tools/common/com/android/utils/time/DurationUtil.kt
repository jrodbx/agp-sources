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

import java.util.concurrent.TimeUnit
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class) // For DurationUnit which is no longer experimental as of 1.6
fun TimeUnit.toDurationUnit(): DurationUnit = when (this) {
    TimeUnit.NANOSECONDS -> DurationUnit.NANOSECONDS
    TimeUnit.MICROSECONDS -> DurationUnit.MICROSECONDS
    TimeUnit.MILLISECONDS -> DurationUnit.MILLISECONDS
    TimeUnit.SECONDS -> DurationUnit.SECONDS
    TimeUnit.MINUTES -> DurationUnit.MINUTES
    TimeUnit.HOURS -> DurationUnit.HOURS
    TimeUnit.DAYS -> DurationUnit.DAYS
}
