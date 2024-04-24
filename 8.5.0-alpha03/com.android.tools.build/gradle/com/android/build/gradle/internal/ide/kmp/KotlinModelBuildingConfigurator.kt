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

package com.android.build.gradle.internal.ide.kmp

import com.android.SdkConstants
import com.android.Version
import com.android.build.api.component.impl.KmpAndroidTestImpl
import com.android.build.api.component.impl.KmpUnitTestImpl
import com.android.build.api.dsl.KotlinMultiplatformAndroidTarget
import com.android.build.gradle.internal.component.AndroidTestCreationConfig
import com.android.build.gradle.internal.component.KmpComponentCreationConfig
import com.android.build.gradle.internal.component.KmpCreationConfig
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.ide.proto.convert
import com.android.build.gradle.internal.ide.proto.setIfNotNull
import com.android.build.gradle.internal.ide.v2.ModelBuilder.Companion.getAgpFlags
import com.android.build.gradle.internal.ide.v2.TestInfoImpl
import com.android.build.gradle.internal.ide.v2.convertToExecution
import com.android.build.gradle.internal.lint.getLocalCustomLintChecksForModel
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.utils.getDesugarLibConfigFile
import com.android.build.gradle.internal.utils.getDesugaredMethods
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.core.ComponentType
import com.android.builder.errors.IssueReporter
import com.android.kotlin.multiplatform.ide.models.serialization.androidCompilationKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidSourceSetKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidTargetKey
import com.android.kotlin.multiplatform.models.AndroidCompilation
import com.android.kotlin.multiplatform.models.AndroidSourceSet
import com.android.kotlin.multiplatform.models.AndroidTarget
import com.android.kotlin.multiplatform.models.InstrumentedTestInfo
import com.android.kotlin.multiplatform.models.MainVariantInfo
import com.android.kotlin.multiplatform.models.SourceProvider
import com.android.kotlin.multiplatform.models.UnitTestInfo
import org.gradle.api.Project

/**
 * A singleton that is responsible for populating the android models sent along with the android
 * target, compilation and sourceSets. Data is added through [org.jetbrains.kotlin.tooling.core.HasMutableExtras.extras]
 * which the target, compilation and sourceSet implements.
 *
 * Checkout [androidTargetKey], [androidCompilationKey], [androidSourceSetKey] to see what data is
 * held in each model.
 */
object KotlinModelBuildingConfigurator {
    private fun KmpComponentCreationConfig.toType() = when (this) {
        is KmpCreationConfig -> AndroidCompilation.CompilationType.MAIN
        is KmpUnitTestImpl -> AndroidCompilation.CompilationType.UNIT_TEST
        is KmpAndroidTestImpl -> AndroidCompilation.CompilationType.INSTRUMENTED_TEST
        else -> throw IllegalArgumentException("Unknown type ${this::class.java}")
    }

    /**
     * As each [KmpComponentCreationConfig] corresponds to a kotlin compilation, this method
     * adds extra android-specific information to the android kotlin compilations. That includes,
     * the main compilation and the unitTest and instrumentedTest compilations if enabled.
     *
     * This method also adds android-specific data about the default sourceSet in each compilation,
     * mainly the android manifest location.
     */
    fun setupAndroidCompilations(
        components: List<KmpComponentCreationConfig>,
        testInstrumentationRunner: String?,
        testInstrumentationRunnerArguments: Map<String, String>
    ) {
        components.forEach { component ->
            val compilation = component.androidKotlinCompilation

            compilation.extras[androidCompilationKey] =
                AndroidCompilation.newBuilder()
                    .setType(component.toType())
                    .setDefaultSourceSetName(
                        compilation.defaultSourceSet.name
                    )
                    .setAssembleTaskName(
                        component.taskContainer.assembleTask.name
                    )
                    .setKotlinCompileTaskName(
                        component.androidKotlinCompilation.compileKotlinTaskName
                    )
                    .setIfNotNull(
                        (component as? KmpCreationConfig)?.toInfo(),
                        AndroidCompilation.Builder::setMainInfo
                    )
                    .setIfNotNull(
                        (component as? HostTestCreationConfig)?.toInfo(),
                        AndroidCompilation.Builder::setUnitTestInfo
                    )
                    .setIfNotNull(
                        (component as? AndroidTestCreationConfig)?.toInfo(
                            testInstrumentationRunner, testInstrumentationRunnerArguments
                        ),
                        AndroidCompilation.Builder::setInstrumentedTestInfo
                    )
                    .build()

            compilation.defaultSourceSet.extras[androidSourceSetKey] =
                AndroidSourceSet.newBuilder()
                    .setSourceProvider(
                        SourceProvider.newBuilder()
                            .setManifestFile(component.sources.manifestFile.get().convert())
                    )
                    .build()
        }
    }

    /**
     * This method attaches global information about the android target to the kotlin android target
     * object. The data sent here is global to the different android compilations created within the
     * target.
     */
    fun setupAndroidTargetModels(
        project: Project,
        mainVariant: KmpCreationConfig,
        androidTarget: KotlinMultiplatformAndroidTarget,
        projectOptions: ProjectOptions,
        issueReporter: IssueReporter
    ) {
        androidTarget.extras[androidTargetKey] =
            AndroidTarget.newBuilder()
                .setAgpVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .setProjectPath(project.path)
                .setRootBuildId(
                    (project.gradle.parent ?: project.gradle).rootProject.projectDir.convert()
                )
                .setBuildId(project.gradle.rootProject.projectDir.convert())
                .setBuildDir(project.layout.buildDirectory.get().asFile.convert())
                .setBuildToolsVersion(mainVariant.global.buildToolsRevision.toString())
                .setGroupId(
                    project.group.toString()
                )
                .addAllBootClasspath(
                    mainVariant.global.filteredBootClasspath.get().map { it.asFile.convert() }
                )
                .setTestInfo(
                    TestInfoImpl(
                        animationsDisabled = mainVariant.global.androidTestOptions.takeIf {
                            mainVariant.androidDeviceTest != null
                        }?.animationsDisabled ?: false,
                        execution = mainVariant.global.androidTestOptions.takeIf {
                            mainVariant.androidDeviceTest != null
                        }?.execution?.convertToExecution(),
                        additionalRuntimeApks = project
                            .configurations
                            .findByName(
                                SdkConstants.GRADLE_ANDROID_TEST_UTIL_CONFIGURATION
                            )?.files ?: listOf(),
                        instrumentedTestTaskName = mainVariant.androidDeviceTest?.taskContainer?.connectedTestTask?.name
                            ?: ""
                    ).convert()
                )
                .setFlags(
                    getAgpFlags(
                        variants = listOf(mainVariant),
                        projectOptions = projectOptions
                    ).convert()
                )
                .addAllLintChecksJars(
                    getLocalCustomLintChecksForModel(
                        project,
                        issueReporter
                    ).map { it.convert() }
                )
                .setIsCoreLibraryDesugaringEnabled(
                    mainVariant.isCoreLibraryDesugaringEnabledLintCheck
                )
                .addAllDesugarLibConfig(
                    if (mainVariant.isCoreLibraryDesugaringEnabledLintCheck) {
                        getDesugarLibConfigFile(project)
                    } else {
                        emptyList()
                    }.map { it.convert() }
                )
                .addAllDesugaredMethodsFiles(
                    getDesugaredMethods(
                        mainVariant.services,
                        mainVariant.isCoreLibraryDesugaringEnabledLintCheck,
                        mainVariant.minSdk,
                        mainVariant.global
                    ).files.map { it.convert() }
                )
                .setWithJava(mainVariant.withJava)
                .build()
    }

    private fun KmpCreationConfig.toInfo() =
        MainVariantInfo.newBuilder()
            .setNamespace(namespace.get())
            .setCompileSdkTarget(global.compileSdkHashString)
            .setMinSdkVersion(minSdk.convert())
            .setIfNotNull(
                maxSdk,
                MainVariantInfo.Builder::setMaxSdkVersion
            )
            .addAllProguardFiles(
                optimizationCreationConfig.proguardFiles.get().map { it.asFile.convert() }
            )
            .addAllConsumerProguardFiles(
                optimizationCreationConfig.consumerProguardFilePaths.map { it.convert() }
            )
            .setMinificationEnabled(
                optimizationCreationConfig.minifiedEnabled
            )
            .build()

    private fun HostTestCreationConfig.toInfo() =
        UnitTestInfo.newBuilder()
            .setNamespace(namespace.get())
            .setIfNotNull(
                global.mockableJarArtifact.files.singleOrNull()?.convert(),
                UnitTestInfo.Builder::setMockablePlatformJar
            )
            .setUnitTestTaskName(
                computeTaskNameInternal(ComponentType.UNIT_TEST_PREFIX)
            )
            .build()

    private fun AndroidTestCreationConfig.toInfo(
        testInstrumentationRunner: String?,
        testInstrumentationRunnerArguments: Map<String, String>
    ) =
        InstrumentedTestInfo.newBuilder()
            .setNamespace(namespace.get())
            .setIfNotNull(
                testInstrumentationRunner,
                InstrumentedTestInfo.Builder::setTestInstrumentationRunner
            )
            .setIfNotNull(
                signingConfig?.convert(),
                InstrumentedTestInfo.Builder::setSigningConfig
            )
            .putAllTestInstrumentationRunnerArguments(testInstrumentationRunnerArguments)
            .setAssembleTaskOutputListingFile(
                artifacts.get(InternalArtifactType.APK_IDE_REDIRECT_FILE).get().asFile.convert()
            )
            .build()
}
