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

@file:JvmName("TestFixturesUtil")

package com.android.build.gradle.internal.testFixtures

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.capabilities.Capability

/**
 * The following constants should match Gradle's internal constants from
 * [org.gradle.internal.component.external.model.TestFixturesSupport].
 */
const val testFixturesClassifier = "test-fixtures"
const val testFixturesFeatureName = "testFixtures"

/**
 * A hack to construct a test fixtures capability instance for [project].
 *
 * TODO: remove once gradle provides an API for creating project capability instances
 * ([issue](https://github.com/gradle/gradle/issues/16839)).
 */
fun getTestFixturesCapabilityForProject(project: Project): Capability {
    val dependencyWithTestFixturesCapability =
        project.dependencies.testFixtures(project) as ProjectDependency
    return dependencyWithTestFixturesCapability.requestedCapabilities.first()
}

/**
 * A testFixtures component of a project will have the capability (group = project.group,
 * name = project.name + "-test-fixtures", version = project.version)
 * When the capability is cloned into an immutable capability instance, the `null` version is
 * converted into a string with value "unspecified".
 * See [DefaultDependencyHandler.testFixtures](https://github.com/gradle/gradle/blob/master/subprojects/dependency-management/src/main/java/org/gradle/api/internal/artifacts/dsl/dependencies/DefaultDependencyHandler.java)
 * to know how testFixtures capability is created.
 */
fun Capability.isProjectTestFixturesCapability(projectName: String) =
     name == "$projectName-$testFixturesClassifier"

/**
 * A testFixtures component of a library will have the capability (group = library.group,
 * name = library.name + "-test-fixtures", version = null)
 * When the capability is cloned into an immutable capability instance, the `null` version is
 * converted into a string with value "unspecified".
 * See [DefaultDependencyHandler.testFixtures](https://github.com/gradle/gradle/blob/master/subprojects/dependency-management/src/main/java/org/gradle/api/internal/artifacts/dsl/dependencies/DefaultDependencyHandler.java)
 * to know how testFixtures capability is created.
 */
fun Capability.isLibraryTestFixturesCapability(libraryName: String) =
    name == "$libraryName-$testFixturesClassifier"
