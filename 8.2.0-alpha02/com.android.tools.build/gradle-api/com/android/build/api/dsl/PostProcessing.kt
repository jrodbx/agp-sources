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

package com.android.build.api.dsl

import org.gradle.api.Incubating
import java.io.File

/**
 * DSL object for configuring postProcessing: removing dead code, obfuscating etc.
 *
 * <p>This DSL is incubating and subject to change. To configure code and resource shrinkers,
 * Instead use the properties already available in the <a
 * href="com.android.build.gradle.internal.dsl.BuildType.html"><code>buildType</code></a> block.
 *
 * <p>To learn more, read <a
 * href="https://developer.android.com/studio/build/shrink-code.html">Shrink Your Code and
 * Resources</a>.
 */
@Incubating
interface PostProcessing {
    fun initWith(that: PostProcessing)

    var isRemoveUnusedCode: Boolean

    var isRemoveUnusedResources: Boolean

    var isObfuscate: Boolean

    var isOptimizeCode: Boolean

    fun setProguardFiles(proguardFiles: List<Any>)
    fun proguardFile(file: Any)
    fun proguardFiles(vararg files: Any)

    fun setTestProguardFiles(testProguardFiles: List<Any>)
    fun testProguardFile(file: Any)
    fun testProguardFiles(vararg files: Any)

    fun setConsumerProguardFiles(consumerProguardFiles: List<Any>)
    fun consumerProguardFile(file: Any)
    fun consumerProguardFiles(vararg files: Any)

    @Deprecated("This property no longer has any effect. R8 is always used.")
    var codeShrinker: String
}
