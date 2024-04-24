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
import com.android.build.gradle.internal.api.LazyAndroidSourceSet
import com.android.builder.core.ComponentType
import com.android.builder.core.ComponentTypeImpl

/** Common parts of build type and product flavor data objects.  */
abstract class VariantDimensionData(
    val sourceSet: DefaultAndroidSourceSet,
    private val testFixturesSourceSet: LazyAndroidSourceSet?,
    private val androidTestSourceSet: LazyAndroidSourceSet?,
    private val unitTestSourceSet: LazyAndroidSourceSet?,
    lazySourceSetCreation: Boolean
) {
    init {
        // Initialize eagerly
        if (!lazySourceSetCreation) {
            testFixturesSourceSet?.get()
            androidTestSourceSet?.get()
            unitTestSourceSet?.get()
        }
    }

    private fun getNestedSourceSet(type: ComponentType) = when (type) {
        ComponentTypeImpl.TEST_FIXTURES -> testFixturesSourceSet
        ComponentTypeImpl.ANDROID_TEST -> androidTestSourceSet
        ComponentTypeImpl.UNIT_TEST -> unitTestSourceSet
        else -> {
            throw IllegalArgumentException(
                "Unknown component type $type"
            )
        }
    }

    fun getSourceSet(type: ComponentType): DefaultAndroidSourceSet? {
        return getNestedSourceSet(type)?.get()
    }

    /**
     * Get sourceSets for sync. This is different from [getSourceSet] as here we only return the
     * sourceSet if it was initialized during configuration. So, if it was disabled and never needed
     * by a consumer, we shouldn't send it to the model here.
     */
    fun getSourceSetForModel(type: ComponentType): DefaultAndroidSourceSet? {
        return getNestedSourceSet(type)?.takeIf { it.isInitialized() }?.get()
    }
}
