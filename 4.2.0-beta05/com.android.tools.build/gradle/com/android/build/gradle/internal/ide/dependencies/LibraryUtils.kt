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

@file:JvmName("LibraryUtils")
package com.android.build.gradle.internal.ide.dependencies

import com.android.build.api.attributes.VariantAttr
import com.android.build.gradle.internal.ide.DependenciesImpl
import com.android.builder.model.AndroidLibrary
import com.android.builder.model.AndroidProject
import com.android.builder.model.Dependencies
import com.google.common.collect.Lists
import org.gradle.api.artifacts.result.ResolvedArtifactResult

fun clone(dependencies: Dependencies, modelLevel: Int): Dependencies {
    if (modelLevel >= AndroidProject.MODEL_LEVEL_4_NEW_DEP_MODEL) {
        return DependenciesImpl.EMPTY
    }

    // these items are already ready for serializable, all we need to clone is
    // the Dependencies instance.
    val libraries = emptyList<AndroidLibrary>()
    val javaLibraries = Lists.newArrayList(dependencies.javaLibraries)
    val projects = emptyList<Dependencies.ProjectIdentifier>()

    return DependenciesImpl(
        libraries,
        javaLibraries,
        projects,
        Lists.newArrayList(dependencies.runtimeOnlyClasses)
    )
}

fun ResolvedArtifactResult.getVariantName(): String? {
    return variant.attributes.getAttribute(VariantAttr.ATTRIBUTE)?.name
}
