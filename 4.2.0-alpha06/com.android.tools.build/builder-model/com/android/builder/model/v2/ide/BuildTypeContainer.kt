/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.builder.model.v2.ide

import com.android.builder.model.v2.dsl.BuildType

/**
 * A Container of all the data related to [BuildType].
 *
 * @since 4.2
 */
interface BuildTypeContainer {
    /**
     * The Build Type itself.
     *
     * @return the build type
     */
    val buildType: BuildType

    /**
     * The associated sources of the build type.
     *
     * @return the build type source provider.
     */
    val sourceProvider: SourceProvider

    val androidTestSourceProvider: SourceProvider?
    val unitTestSourceProvider: SourceProvider?
}