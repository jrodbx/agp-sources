/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.dependencies

import com.android.build.gradle.internal.ide.DependenciesImpl
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.errors.IssueReporter
import com.android.builder.model.AndroidProject
import com.android.builder.model.level2.DependencyGraphs
import com.google.common.collect.ImmutableMap

interface DependencyGraphBuilder {

    /** Create a level 1 dependency list.  */
    fun createDependencies(
        variantScope: VariantScope,
        buildMapping: ImmutableMap<String, String>,
        issueReporter: IssueReporter
    ): DependenciesImpl

    /**
     * Create a level 4 dependency graph.
     *
     * @see AndroidProject#MODEL_LEVEL_4_NEW_DEP_MODEL
     */
    fun createLevel4DependencyGraph(
        variantScope: VariantScope,
        withFullDependency: Boolean,
        buildMapping: ImmutableMap<String, String>,
        issueReporter: IssueReporter
    ): DependencyGraphs
}


fun getDependencyGraphBuilder(): DependencyGraphBuilder {
    return ArtifactDependencyGraph()
}
