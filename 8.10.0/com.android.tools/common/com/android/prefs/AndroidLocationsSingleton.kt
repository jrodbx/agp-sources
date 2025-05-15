/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.prefs

import com.android.utils.EnvironmentProvider
import com.android.utils.StdLogger

/**
 * Singleton class for [AbstractAndroidLocations]
 *
 * This is to be used in stand alone tools command line tools or Studio.
 *
 * This should not be used in the Android Gradle Plugin. See the special implementation
 * of [AbstractAndroidLocations] for AGP.
 */
object AndroidLocationsSingleton: AbstractAndroidLocations(
        environmentProvider = EnvironmentProvider.DIRECT,
        logger = StdLogger(StdLogger.Level.VERBOSE),
        silent = true
)
