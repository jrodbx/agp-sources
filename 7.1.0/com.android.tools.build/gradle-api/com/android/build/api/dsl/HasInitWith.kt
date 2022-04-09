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

/**
 * For build types and product flavors, they can be initialized from the current state of another.
 *
 * This can be useful to save repeating configuration in the build file when two build types
 * or flavors should be almost identical.
 *
 * Custom extensions attached to build type and product flavor should implement this,
 * so that they can also be initialized when Android Gradle plugin initialized the build type or
 * product flavor too.
 * Note that this will only work with Android Gradle Plugin 7.1 or higher, for older versions
 * this will have no effect.
 */
@Incubating
interface HasInitWith<T: Any> {
    /** Copy the properties of the given build type, product flavor or custom extension to this */
    fun initWith(that: T)
}
