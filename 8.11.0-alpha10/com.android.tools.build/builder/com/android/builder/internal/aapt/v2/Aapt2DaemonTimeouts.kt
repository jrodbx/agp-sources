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

package com.android.builder.internal.aapt.v2

import java.util.concurrent.TimeUnit

data class Aapt2DaemonTimeouts(
        val start: Long = 30,
        val startUnit: TimeUnit = TimeUnit.SECONDS,
        val compile: Long = 2,
        val compileUnit: TimeUnit = TimeUnit.MINUTES,
        val link: Long = 10,
        val linkUnit: TimeUnit = TimeUnit.MINUTES,
        val stop: Long = start,
        val stopUnit: TimeUnit = startUnit)