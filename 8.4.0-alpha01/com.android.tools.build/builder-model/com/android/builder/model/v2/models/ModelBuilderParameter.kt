/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.builder.model.v2.models

import com.android.builder.model.v2.ide.ArtifactDependencies

/**
 * The parameter for ModelBuilder to specify what to sync.
 *
 * This interface is implemented and instantiated on the fly by Gradle when using
 * [org.gradle.tooling.BuildController.findModel]
 */
interface ModelBuilderParameter {

    /**
     * The name of the variant for which to return [VariantDependencies]
     */
    var variantName: String

    /**
     * Don't build the runtime classpath for the main artifact. If true
     * [ArtifactDependencies.runtimeDependencies] will be null for
     * [VariantDependencies.mainArtifact]
     *
     * If false the resulting [VariantDependencies] will contain [ArtifactDependencies]
     * with the [ArtifactDependencies.runtimeDependencies] populated.
     *
     * Note: this only ensures that the model builder doesn't request the runtime classpath
     * it does not ensure that it isn't resolved indirectly or through other models.
     * e.g. "android.dependency.useConstraints" may cause building the compile classpath to also
     * partially resolve the runtime classpath.
     */
    var dontBuildRuntimeClasspath: Boolean

    /**
     * Don't build the runtime classpath for the unit test artifact. If true
     * [ArtifactDependencies.runtimeDependencies] will be null for
     * [VariantDependencies.unitTestArtifact].
     *
     * See [dontBuildRuntimeClasspath] for additional details.
     */
    var dontBuildUnitTestRuntimeClasspath: Boolean

    /**
     * Don't build the runtime classpath for the unit test artifact. If true
     * [ArtifactDependencies.runtimeDependencies] will be null for
     * [VariantDependencies.androidTestArtifact].
     *
     * See [dontBuildRuntimeClasspath] for additional details.
     */
    var dontBuildAndroidTestRuntimeClasspath: Boolean

    /**
     * Don't build the runtime classpath for the unit test artifact. If true
     * [ArtifactDependencies.runtimeDependencies] will be null for
     * [VariantDependencies.testFixturesArtifact].
     *
     * See [dontBuildRuntimeClasspath] for additional details.
     */
    var dontBuildTestFixtureRuntimeClasspath: Boolean
}

/**
 * Utility enum for pre-set values for which runtime classpaths to build in the model builder.
 */
enum class ClasspathParameterConfig(
    private val mainRuntime: Boolean,
    private val unitTestRuntime: Boolean,
    private val androidTestRuntime: Boolean,
    private val testFixturesRuntime: Boolean,
) {
    ALL(true, true, true, true),
    ANDROID_TEST_ONLY(false, false, true, false),
    NONE(false, false, false, false);

    fun applyTo(param: ModelBuilderParameter) {
        param.dontBuildRuntimeClasspath = !mainRuntime
        param.dontBuildUnitTestRuntimeClasspath = !unitTestRuntime
        param.dontBuildAndroidTestRuntimeClasspath = !androidTestRuntime
        param.dontBuildTestFixtureRuntimeClasspath = !testFixturesRuntime
    }
}
