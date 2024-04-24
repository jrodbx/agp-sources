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

import org.gradle.api.Incubating

/**
 * Options to build a [KotlinMultiplatformAndroidCompilation] object.
 */
@Incubating
interface KotlinMultiplatformAndroidCompilationBuilder {
    /**
     * The name of the compilation object. The name can be used later to access the compilation
     * object using
     * ```
     * kotlin {
     *   androidLibrary {
     *     compilations.getByName("main") {
     *       // configure compilation
     *     }
     *   }
     * }
     * ```
     *
     * @see [org.jetbrains.kotlin.gradle.plugin.KotlinCompilation]
     */
    @get:Incubating
    @set:Incubating
    var compilationName: String

    /**
     * The name of the sourceSet that is used in the compilation as the default sourceSet to compile.
     * The sourceSet created will be located at `$projectDir/src/$sourceSetName`.
     *
     * The sourceSet name can be used later to access the sourceSet object using
     * ```
     * kotlin {
     *   sourceSets.getByName("androidMain") {
     *     // configure sourceSet
     *   }
     * }
     * ```
     *
     * @see [org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet]
     */
    @get:Incubating
    @set:Incubating
    var defaultSourceSetName: String

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
    @get:Incubating
    @set:Incubating
    var sourceSetTreeName: String?
}
