/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.build.gradle.internal

import com.android.build.gradle.internal.api.DefaultAndroidSourceSet
import com.android.builder.core.ComponentType
import com.google.common.base.Preconditions

/** Common parts of build type and product flavor data objects.  */
abstract class VariantDimensionData(
    val sourceSet: DefaultAndroidSourceSet,
    val testFixturesSourceSet: DefaultAndroidSourceSet?,
    private val androidTestSourceSet: DefaultAndroidSourceSet?,
    private val unitTestSourceSet: DefaultAndroidSourceSet?
) {
    fun getTestSourceSet(type: ComponentType): DefaultAndroidSourceSet? {
        Preconditions.checkState(
            type.isTestComponent,
            "Unknown test variant type $type"
        )

        return if (type.isApk) {
            androidTestSourceSet
        } else {
            unitTestSourceSet
        }
    }
}
