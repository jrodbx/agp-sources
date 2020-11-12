/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.variant

import org.gradle.api.Incubating
import org.gradle.api.file.RegularFileProperty

/**
 * Variant specific settings for creating dex files.
 */
@Incubating
interface Dexing {

    /**
     * Text file that specifies additional classes that will be compiled into the main dex file.
     *
     * Classes specified in the file are appended to the main dex classes computed using
     * `aapt`.
     *
     * If set, the file should contain one class per line, in the following format:
     * `com/example/MyClass.class`
     *
     * Initialized from DSL [com.android.build.api.dsl.VariantDimension.multiDexKeepFile]
     */
    val multiDexKeepFile: RegularFileProperty

    /**
     * Text file with additional ProGuard rules to be used to determine which classes are compiled
     * into the main dex file.
     *
     * If set, rules from this file are used in combination with the default rules used by the
     * build system.
     *
     * Initialized from DSL [com.android.build.api.dsl.VariantDimension.multiDexKeepProguard]
     */
    val multiDexKeepProguard: RegularFileProperty
}
