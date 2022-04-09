/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.build.api.dsl.BuildType
import com.android.build.gradle.internal.api.DefaultAndroidSourceSet

/**
 * Class containing a BuildType and associated data (Sourceset for instance).
 *
 * This generated during DSL execution and is used for variant creation
 */
class BuildTypeData<BuildTypeT : BuildType>(
    val buildType: BuildTypeT,
    sourceSet: DefaultAndroidSourceSet,
    testFixturesSourceSet: DefaultAndroidSourceSet?,
    androidTestSourceSet: DefaultAndroidSourceSet?,
    unitTestSourceSet: DefaultAndroidSourceSet?
) : VariantDimensionData(sourceSet, testFixturesSourceSet, androidTestSourceSet, unitTestSourceSet)
