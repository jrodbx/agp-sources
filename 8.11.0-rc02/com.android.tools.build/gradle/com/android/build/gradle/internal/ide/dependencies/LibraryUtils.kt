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

import com.android.build.gradle.internal.attributes.VariantAttr
import com.android.build.gradle.internal.testFixtures.isLibraryTestFixturesCapability
import com.android.build.gradle.internal.testFixtures.isProjectTestFixturesCapability
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult

fun ResolvedArtifactResult.getVariantName(): String? {
    return variant.attributes.getAttribute(VariantAttr.ATTRIBUTE)?.name
}

/**
 * Checks if the resolved artifact is produced from a local project with testFixtures capability.
 */
fun ResolvedArtifactResult.hasProjectTestFixturesCapability(): Boolean {
    if (id.componentIdentifier !is ProjectComponentIdentifier) {
        return false
    }
    return variant.capabilities.any {
        it.isProjectTestFixturesCapability(
            (id.componentIdentifier as ProjectComponentIdentifier).projectName
        )
    }
}

/**
 * Checks if the resolved artifact is coming from an external library with testFixtures capability.
 */
fun ResolvedArtifactResult.hasLibraryTestFixturesCapability(): Boolean {
    if (id.componentIdentifier !is ModuleComponentIdentifier) {
        return false
    }
    return variant.capabilities.any {
        it.isLibraryTestFixturesCapability(
            libraryName = (id.componentIdentifier as ModuleComponentIdentifier).module
        )
    }
}
