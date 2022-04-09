/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.api.variant

/**
 * Application specific variant object that contains properties that will determine the variant's
 * build flow.
 *
 * For example, an application variant may have minification on or off, or have a different
 * minSdkVersion from the other variants.
 *
 * All these properties must be resolved during configuration time as [org.gradle.api.Task]
 * representing the variant build flows must be created.
 */
interface ApplicationVariantBuilder : VariantBuilder,
    HasAndroidTestBuilder,
    HasTestFixturesBuilder {

    val debuggable: Boolean

    /** Specify whether to include SDK dependency information in APKs and Bundles. */
    val dependenciesInfo: DependenciesInfoBuilder
}
