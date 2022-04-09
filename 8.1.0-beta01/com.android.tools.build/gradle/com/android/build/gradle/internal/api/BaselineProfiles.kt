/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.api

import java.io.File

class BaselineProfiles {
    companion object {

        /**
         * Returns true if the file should be merged as part of the human readable art profile
         * merging.
         */
        fun shouldBeMerged(file: File): Boolean =
            file.name != StartupProfileFileName

        /**
         * File name for files containing human readable instructions for the baseline binary
         * generation.
         */
        const val BaselineProfileFileName = "baseline-prof.txt"

        /**
         * File name for files containing list of classes used during startup to optimize dex layout
         * during R8 minification.
         */
        const val StartupProfileFileName = "startup-prof.txt"
    }
}
