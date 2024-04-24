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

package com.android.build.gradle.internal.dependency

import com.android.build.api.component.impl.KmpAndroidTestImpl
import com.android.build.api.component.impl.KmpUnitTestImpl
import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import com.android.builder.core.ComponentType
import com.android.ide.common.gradle.Version
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

private const val KOTLIN_MODULE_GROUP = "org.jetbrains.kotlin"
private const val KOTLIN_TEST_ROOT_MODULE_NAME = "kotlin-test"
private val kotlin150Version = Version.parse("1.5.0")

/**
 * Configure capability of the `org.jetbrains.kotlin:kotlin-test` artifact to resolve to the proper
 * artifact depending on the test platform configured. So when users declare a dependency on
 * `kotlin-test` from their common sourceSets, the unitTest configurations would be able to resolve
 * it to `junit` or `testng`.
 *
 * @note: This is a workaround until the kotlin plugin provides a proper API for us to either
 *  declare out test tasks, or to configure the test framework directly.
 */
internal fun configureKotlinTestDependencyForUnitTestCompilation(
    project: Project,
    unitTestComponent: KmpUnitTestImpl,
    kotlinMultiplatformExtension: KotlinMultiplatformExtension,
) {
    configureKotlinTestDependency(
        project,
        unitTestComponent.androidKotlinCompilation,
        kotlinMultiplatformExtension,
        testFrameworkCapabilityProvider = project.tasks.named(
            unitTestComponent.computeTaskName(ComponentType.UNIT_TEST_PREFIX)
        ).map {
            testFrameworkCapabilityOf(testFrameworkOf(it as Test?))
        }
    )
}

/**
 * Configure capability of the `org.jetbrains.kotlin:kotlin-test` artifact to resolve to the proper
 * artifact depending on the test platform configured. So when users declare a dependency on
 * `kotlin-test` from their common sourceSets, the instrumentedTest configurations would be able to\
 * resolve it to `kotlin-test-junit`.
 *
 * @note: This is a workaround until the kotlin plugin provides a proper API for us to either
 *  declare out test tasks, or to configure the test framework directly.
 */
internal fun configureKotlinTestDependencyForInstrumentedTestCompilation(
    project: Project,
    instrumentedTestComponent: KmpAndroidTestImpl,
    kotlinMultiplatformExtension: KotlinMultiplatformExtension,
) {
    configureKotlinTestDependency(
        project,
        instrumentedTestComponent.androidKotlinCompilation,
        kotlinMultiplatformExtension,
        testFrameworkCapabilityProvider = instrumentedTestComponent.services.provider {
            testFrameworkCapabilityOf("junit")
        }
    )
}

private fun configureKotlinTestDependency(
    project: Project,
    compilation: KotlinMultiplatformAndroidCompilation,
    kotlinMultiplatformExtension: KotlinMultiplatformExtension,
    testFrameworkCapabilityProvider: Provider<String>
) {
    listOf(
        compilation.apiConfigurationName,
        compilation.implementationConfigurationName,
        compilation.compileOnlyConfigurationName,
        compilation.runtimeOnlyConfigurationName
    ).forEach {
        project.configurations.getByName(it).maybeAddTestDependencyCapability(
            project.dependencies,
            testFrameworkCapabilityProvider,
        ) {
            kotlinMultiplatformExtension.coreLibrariesVersion
        }
    }

    compilation.kotlinSourceSets.forEach { sourceSet ->
        listOf(
            sourceSet.apiConfigurationName,
            sourceSet.implementationConfigurationName,
            sourceSet.compileOnlyConfigurationName,
            sourceSet.runtimeOnlyConfigurationName
        ).forEach {
            project.configurations.getByName(it).maybeAddTestDependencyCapability(
                project.dependencies,
                testFrameworkCapabilityProvider,
            ) {
                kotlinMultiplatformExtension.coreLibrariesVersion
            }
        }
    }
}

private fun Configuration.maybeAddTestDependencyCapability(
    dependencyHandler: DependencyHandler,
    testFrameworkCapabilityProvider: Provider<String>,
    coreLibrariesVersion: () -> String
) {
    withDependencies { dependencies ->
        val testRootDependency =
            allDependencies.matching { dependency ->
                dependency !is ProjectDependency
            }.singleOrNull { dependency ->
                dependency.group == KOTLIN_MODULE_GROUP &&
                        dependency.name == KOTLIN_TEST_ROOT_MODULE_NAME
            }

        if (testRootDependency != null) {
            val depVersion = testRootDependency.version ?: coreLibrariesVersion()
            if (Version.parse(depVersion) < kotlin150Version) return@withDependencies

            dependencies.addLater(
                testFrameworkCapabilityProvider.map { capability ->
                    dependencyHandler
                        .create(
                            "$KOTLIN_MODULE_GROUP:$KOTLIN_TEST_ROOT_MODULE_NAME:$depVersion"
                        )
                        .apply {
                            (this as ExternalDependency).capabilities {
                                it.requireCapability(capability)
                            }
                        }
                }
            )
        }
    }
}

private fun testFrameworkOf(testTask: Test?): String = when (testTask?.options) {
    is JUnitOptions -> "junit"
    is JUnitPlatformOptions -> "junit5"
    is TestNGOptions -> "testng"
    else -> // failed to detect, fallback to junit
        "junit"
}

private fun testFrameworkCapabilityOf(testFramework: String) =
    "$KOTLIN_MODULE_GROUP:$KOTLIN_TEST_ROOT_MODULE_NAME-framework-$testFramework"
