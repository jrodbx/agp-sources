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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.Optimization
import org.gradle.api.Incubating

// TODO(b/267309622): Move to gradle-api
@Incubating
interface KmpOptimization: Optimization {
    /**
     * Specifies the ProGuard configuration files that the plugin should use.
     *
     * There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     * `proguard-android-optimize.txt` is identical to `proguard-android.txt`,
     * except with optimizations enabled. You can use [getDefaultProguardFile(String)]
     * to return the full path of the files.
     *
     * @return an object that contains a collection of files.
     */
     @get:Incubating
     val proguard: ConfigurableFiles

     /**
     * The collection of proguard rule files to use for the test APK.
     */
     @get:Incubating
     val testProguard: ConfigurableFiles

     /**
     * The collection of proguard rule files for consumers of the library to use.
     */
     @get:Incubating
     val consumerProguard: ConfigurableFiles

    /**
     * Specifies whether to enable code shrinking for this build type.
     *
     * By default, when you enable code shrinking by setting this property to `true`,
     * the Android plugin uses ProGuard.
     *
     * To learn more, read
     * [Shrink Your Code and Resources](https://developer.android.com/studio/build/shrink-code.html).
     */
     @get:Incubating
     @set:Incubating
     var isMinifyEnabled: Boolean

    /**
     * Publishing consumer proguard rules as part of a kmp library is an opt-in feature.
     * By default, consumer proguard rules will not be published.
     *
     * To enable it, set this property to `true`
     */
     @get:Incubating
     @set:Incubating
     var enableConsumerProguardRulePublishing: Boolean
}
