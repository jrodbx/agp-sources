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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.DynamicFeatureBuildType
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.api.dsl.TestBuildType

/**
 * To appease the groovy dynamic method dispatch so that
 * initWith with an instance of the Gradle decorated [BuildType]_Decorated can be passed to
 * initWith in groovy (finding `fun initWith(that: InternalBuildType): BuildType`)
 *
 * ```
 * Tooling Model       New DSL API
 *   BuildType          BuildType
 *       \                 /        below class/iterface implements/extends above
 *        InternalBuildType
 *             |
 *        ...._Decorated (Gradle decorated subclass)
 * ```
 *
 * I'm not sure why, but the groovy dispatch can't handle disambiguating the three methods when
 * InternalBuildType is a class, but can when everything is an interface.
 */
interface InternalBuildType :
    ApplicationBuildType,
    LibraryBuildType,
    DynamicFeatureBuildType,
    TestBuildType,
    com.android.builder.model.BuildType
