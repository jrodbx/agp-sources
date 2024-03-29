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
     * Don't build the runtime classpath. If true [ArtifactDependencies.runtimeDependencies] will
     * be null. If false the resulting [VariantDependencies] will contain [ArtifactDependencies]
     * with the [ArtifactDependencies.runtimeDependencies] populated.
     *
     * Note: this only ensures that the model builder doesn't request the runtime classpath
     * it does not ensure that it isn't resolved indirectly or through other models.
     * e.g. "android.dependency.useConstraints" may cause building the compile classpath to also
     * resolve the runtime classpath.
     */
    var dontBuildRuntimeClasspath: Boolean
}
