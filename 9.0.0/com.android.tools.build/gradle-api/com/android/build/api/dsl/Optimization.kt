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
import org.gradle.api.provider.SetProperty

/**
 *  DSL object for configurations aimed for optimizing build process(e.g. speed, correctness). This
 *  DSL object is applicable to buildTypes and productFlavors.
 */
@Incubating
interface Optimization {

    /**
     * Configure keep rules inherited from external library dependencies
     */
    @Incubating
    fun keepRules(action: KeepRules.() -> Unit)

    /**
     * Configure baseline profile properties
     */
    @Incubating
    fun baselineProfile(action: BaselineProfile.() -> Unit)

    /**
     * Specifies whether to enable code shrinking and resource optimization.
     * Property works for applications only. For other types it will be ignored
     *
     * By default, value is false to avoid runtime issue.
     * When enabled the Android plugin uses R8 for optimization.
     *
     * Flavours merge this property with "or" rule. That means having this enable=true
     * sticks.
    */
    @get:Incubating
    @set:Incubating
    var enable: Boolean

    /**
     * Specifies what packages are included for optimization.
     * Those may be local code packages and external libraries packages.
     * You can include
     *  - classes for a single package, like "com.example.app.*"
     *  - classes from package and all subpackages, like "androidx.**"
     *  - everything "**"
     *
     * Default (convention) value is "**"
     */
    @get:Incubating
    val packageScope: SetProperty<String>
}
