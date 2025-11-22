/*
 * Copyright (C) 2023 The Android Open Source Project
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

/**
 * Options to build a [KotlinMultiplatformAndroidCompilation] object.
 */
interface KotlinMultiplatformAndroidCompilationBuilder {
    /**
     * The name of the sourceSet tree that would be used to infer the dependencies between
     * sourceSets. For example, setting the sourceSetTreeName to `test` means that compilation will
     * include the `commonTest` sourceSet, and setting it to `integrationTest` means that the
     * compilation will include the `commonIntegrationTest` sourceSet if exists.
     *
     * Setting this value to `null` means that this compilation is not part of any sourceSet trees
     * and the kotlin plugin will not include any common test sourceSets in this compilation.
     *
     * @see [org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension.targetHierarchy]
     */
    var sourceSetTreeName: String?
}
