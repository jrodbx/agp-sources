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

package com.android.build.gradle.internal.utils

import com.android.build.gradle.internal.dependency.AndroidXDependencySubstitution

/**
 * An AndroidX dependency with group and module name (without a version). It also contains
 * information about the group and module name of its corresponding pre-AndroidX dependency.
 */
data class AndroidXDependency(
    val group: String,
    val module: String,
    val oldGroup: String,
    val oldModule: String
) {

    companion object {

        /**
         * Creates an AndroidX dependency instance corresponding to the group and module name of
         * a pre-AndroidX dependency.
         */
        @JvmStatic
        fun fromPreAndroidXDependency(oldGroup: String, oldModule: String): AndroidXDependency {
            val androidXDependencyString =
                AndroidXDependencySubstitution.androidXMappings["$oldGroup:$oldModule"]!!
            val groupModuleVersion = androidXDependencyString.split(':')
            return AndroidXDependency(
                group = groupModuleVersion[0],
                module = groupModuleVersion[1],
                oldGroup = oldGroup,
                oldModule = oldModule
            )
        }
    }
}