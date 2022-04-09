/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.ProguardFiles
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.pipeline.StreamFilter
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.APK_MAPPING
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.PROJECT
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.FILTERED_PROGUARD_RULES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.GENERATED_PROGUARD_FILE
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.core.VariantType
import com.google.common.base.Preconditions
import com.google.common.collect.Sets
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Base class for tasks that consume ProGuard configuration files.
 *
 * We use this type to configure ProGuard and the R8 consistently, using the same
 * code.
 */
@DisableCachingByDefault
abstract class ProguardConfigurableTask(
    private val projectLayout: ProjectLayout
) : NonIncrementalTask() {

    @get:Input
    abstract val variantType: Property<VariantType>

    @get:Input
    abstract val includeFeaturesInScopes: Property<Boolean>

    @get:Optional
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testedMappingFile: ConfigurableFileCollection

    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resources: ConfigurableFileCollection

    @get:Classpath
    abstract val referencedClasses: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val referencedResources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val extractedDefaultProguardFile: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configurationFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val mappingFile: RegularFileProperty

    /**
     * Users can have access to the default proguard file location through the
     * VariantDimension.getDefaultProguardFile API.
     * These files are not available during the configuration phase as they are extracted by the
     * ExtractProguardFile during execution.
     * However, the Variant API will allow to override these files and therefore, there is a need
     * to identify the default file location in the incoming list of proguard file and swap them
     * with the final location from [InternalArtifactType.DEFAULT_PROGUARD_FILES]
     */
    internal fun reconcileDefaultProguardFile(
        incomingProguardFile: FileCollection,
        extractedDefaultProguardFile: Provider<Directory>
    ): Collection<File> {

        // if this is not a base module, there should not be any default proguard files so just
        // return.
        if (!variantType.get().isBaseModule) {
            return incomingProguardFile.files
        }

        // get the default proguard files default locations.
        val defaultFiles = ProguardFiles.KNOWN_FILE_NAMES.map { name ->
            ProguardFiles.getDefaultProguardFile(
                name,
                projectLayout.buildDirectory
            )
        }

        return incomingProguardFile.files.map { proguardFile ->
            // if the file is a default proguard file, swap its location with the directory
            // where the final artifacts are.
            if (defaultFiles.contains(proguardFile)) {
               extractedDefaultProguardFile.get().file(proguardFile.name).asFile
            } else {
                proguardFile
            }
        }
    }

    abstract class CreationAction<TaskT : ProguardConfigurableTask, CreationConfigT: ConsumableCreationConfig>
    @JvmOverloads
    internal constructor(
        creationConfig: CreationConfigT,
        private val isTestApplication: Boolean = false,
        private val addCompileRClass: Boolean
    ) : VariantTaskCreationAction<TaskT, CreationConfigT>(
        creationConfig
    ) {

        private val includeFeaturesInScopes: Boolean = creationConfig.variantScope.consumesFeatureJars()
        protected val variantType: VariantType = creationConfig.variantType
        private val testedConfig = creationConfig.testedConfig

        // Override to make this true in proguard
        protected open val defaultObfuscate: Boolean = false

        // These filters assume a file can't be class and resources at the same time.
        private val referencedClasses: FileCollection

        private val referencedResources: FileCollection

        private val classes: FileCollection

        private val resources: FileCollection

        @Suppress("DEPRECATION") // Legacy support (b/195153220)
        protected val inputScopes: MutableSet<com.android.build.api.transform.QualifiedContent.ScopeType> =
            when {
                variantType.isAar -> mutableSetOf(
                    com.android.build.api.transform.QualifiedContent.Scope.PROJECT,
                    InternalScope.LOCAL_DEPS
                )
                includeFeaturesInScopes -> mutableSetOf(
                    com.android.build.api.transform.QualifiedContent.Scope.PROJECT,
                    com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS,
                    com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES,
                    InternalScope.FEATURES
                )
                else -> mutableSetOf(
                    com.android.build.api.transform.QualifiedContent.Scope.PROJECT,
                    com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS,
                    com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES
                )
            }

        init {
            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            val referencedScopes: Set<com.android.build.api.transform.QualifiedContent.Scope> = run {
                val set = Sets.newHashSetWithExpectedSize<com.android.build.api.transform.QualifiedContent.Scope>(5)
                if (variantType.isAar) {
                    set.add(com.android.build.api.transform.QualifiedContent.Scope.SUB_PROJECTS)
                    set.add(com.android.build.api.transform.QualifiedContent.Scope.EXTERNAL_LIBRARIES)
                }

                if (variantType.isTestComponent) {
                    set.add(com.android.build.api.transform.QualifiedContent.Scope.TESTED_CODE)
                }

                set.add(com.android.build.api.transform.QualifiedContent.Scope.PROVIDED_ONLY)

                Sets.immutableEnumSet(set)
            }

            // Check for overlap in scopes
            Preconditions.checkState(
                referencedScopes.intersect(inputScopes).isEmpty(),
                """|Referenced and non-referenced inputs must not overlap.
                   |Referenced scope: ${referencedScopes}
                   |Non referenced scopes: ${inputScopes}
                   |Overlap: ${referencedScopes.intersect(inputScopes)}
                """.trimMargin()
            )

            val transformManager = creationConfig.transformManager
            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            classes = transformManager
                .getPipelineOutputAsFileCollection(createStreamFilter(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES, inputScopes))

            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            resources = transformManager
                .getPipelineOutputAsFileCollection(createStreamFilter(com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES, inputScopes))

            // Consume non referenced inputs
            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            transformManager.consumeStreams(inputScopes, setOf(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES, com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES))

            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            referencedClasses = transformManager
                .getPipelineOutputAsFileCollection(
                    createStreamFilter(com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES, referencedScopes.toMutableSet())
                )

            @Suppress("DEPRECATION") // Legacy support (b/195153220)
            referencedResources = transformManager
                .getPipelineOutputAsFileCollection(
                    createStreamFilter(com.android.build.api.transform.QualifiedContent.DefaultContentType.RESOURCES, referencedScopes.toMutableSet())
                )
        }

        override fun handleProvider(
            taskProvider: TaskProvider<TaskT>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts
                .setInitialProvider(taskProvider,
                ProguardConfigurableTask::mappingFile)
                .on(SingleArtifact.OBFUSCATION_MAPPING_FILE)
        }

        override fun configure(
            task: TaskT
        ) {
            super.configure(task)

            if (testedConfig is ConsumableCreationConfig && testedConfig.minifiedEnabled) {
                task.testedMappingFile.from(
                    testedConfig
                        .artifacts
                        .get(SingleArtifact.OBFUSCATION_MAPPING_FILE)
                )
            } else if (isTestApplication) {
                task.testedMappingFile.from(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                        COMPILE_CLASSPATH,
                        ALL,
                        APK_MAPPING
                    )
                )
            }

            task.variantType.set(variantType)

            task.includeFeaturesInScopes.set(includeFeaturesInScopes)

            task.classes.from(classes)

            if (addCompileRClass) {
                task.classes.from(
                        creationConfig
                                .artifacts
                                .get(InternalArtifactType.COMPILE_R_CLASS_JAR)
                )
            }

            task.resources.from(resources)

            task.referencedClasses.from(referencedClasses)

            task.referencedResources.from(referencedResources)

            task.extractedDefaultProguardFile.set(
                creationConfig.globalScope.globalArtifacts.get(InternalArtifactType.DEFAULT_PROGUARD_FILES))

            applyProguardRules(task, creationConfig, task.testedMappingFile, testedConfig)
        }

        private fun applyProguardRules(
            task: ProguardConfigurableTask,
            creationConfig: ConsumableCreationConfig,
            inputProguardMapping: FileCollection?,
            testedConfig: VariantCreationConfig?
        ) {
            when {
                testedConfig != null -> {
                    // This is an androidTest variant inside an app/library.
                    applyProguardDefaultsForTest()

                    // All -dontwarn rules for test dependencies should go in here:
                    val configurationFiles = task.project.files(
                        creationConfig.proguardFiles,
                        creationConfig.variantDependencies.getArtifactFileCollection(
                            RUNTIME_CLASSPATH,
                            ALL,
                            FILTERED_PROGUARD_RULES
                        )
                    )
                    task.configurationFiles.from(configurationFiles)
                }
                creationConfig.variantType.isForTesting && !creationConfig.variantType.isTestComponent -> {
                    // This is a test-only module and the app being tested was obfuscated with ProGuard.
                    applyProguardDefaultsForTest()

                    // All -dontwarn rules for test dependencies should go in here:
                    val configurationFiles = task.project.files(
                        creationConfig.proguardFiles,
                        creationConfig.variantDependencies.getArtifactFileCollection(
                            RUNTIME_CLASSPATH,
                            ALL,
                            FILTERED_PROGUARD_RULES
                        )
                    )
                    task.configurationFiles.from(configurationFiles)
                }
                else -> // This is a "normal" variant in an app/library.
                    applyProguardConfigForNonTest(task, creationConfig)
            }

            // To set up a dependency on the producer task, the actual proguard files are computed
            // through variantScope
            task.configurationFiles.from(
                creationConfig.globalScope.globalArtifacts.get(
                    InternalArtifactType.DEFAULT_PROGUARD_FILES)
            )

            if (inputProguardMapping != null) {
                task.dependsOn(inputProguardMapping)
            }
        }

        private fun applyProguardDefaultsForTest() {
            // Don't remove any code in tested app.
            // Obfuscate is disabled by default.
            // It is enabled in Proguard since it would ignore the mapping file otherwise.
            // R8 does not have that issue, so we disable obfuscation when running R8.
            setActions(PostprocessingFeatures(false, defaultObfuscate, false))

            keep("class * {*;}")
            keep("interface * {*;}")
            keep("enum * {*;}")
            keepAttributes()
        }

        private fun applyProguardConfigForNonTest(
            task: ProguardConfigurableTask,
            creationConfig: ConsumableCreationConfig
        ) {
            val variantDslInfo = creationConfig.variantDslInfo

            val postprocessingFeatures = creationConfig.variantScope.postprocessingFeatures
            postprocessingFeatures?.let { setActions(postprocessingFeatures) }

            val aaptProguardFile =
                if (task.includeFeaturesInScopes.get()) {
                    creationConfig.artifacts.get(
                        InternalArtifactType.MERGED_AAPT_PROGUARD_FILE)
                } else {
                    creationConfig.artifacts.get(
                        InternalArtifactType.AAPT_PROGUARD_FILE
                    )
                }

            val configurationFiles = task.project.files(
                creationConfig.proguardFiles,
                aaptProguardFile,
                creationConfig.artifacts.get(GENERATED_PROGUARD_FILE),
                creationConfig.variantDependencies.getArtifactFileCollection(
                    RUNTIME_CLASSPATH,
                    ALL,
                    FILTERED_PROGUARD_RULES
                )
            )

            if (task.includeFeaturesInScopes.get()) {
                addFeatureProguardRules(creationConfig, configurationFiles)
            }
            task.configurationFiles.from(configurationFiles)

            if (creationConfig.variantType.isAar) {
                keep("class **.R")
                keep("class **.R$* {*;}")
            }

            if (variantDslInfo.isTestCoverageEnabled) {
                // when collecting coverage, don't remove the JaCoCo runtime
                keep("class com.vladium.** {*;}")
                keep("class org.jacoco.** {*;}")
                keep("interface org.jacoco.** {*;}")
                dontWarn("org.jacoco.**")
            }
        }

        private fun addFeatureProguardRules(
            creationConfig: ConsumableCreationConfig,
            configurationFiles: ConfigurableFileCollection
        ) {
            configurationFiles.from(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    REVERSE_METADATA_VALUES,
                    PROJECT,
                    FILTERED_PROGUARD_RULES
                )
            )
        }

        protected abstract fun keep(keep: String)

        protected abstract fun keepAttributes()

        protected abstract fun dontWarn(dontWarn: String)

        protected abstract fun setActions(actions: PostprocessingFeatures)

        /**
         *  Convenience function. Returns a StreamFilter that checks for the given contentType and a
         *  nonempty intersection with the given set of Scopes .
         */
        @Suppress("DEPRECATION") // Legacy support (b/195153220)
        private fun createStreamFilter(
            desiredType: com.android.build.api.transform.QualifiedContent.ContentType,
            desiredScopes: MutableSet<in com.android.build.api.transform.QualifiedContent.ScopeType>
        ): StreamFilter {
            return StreamFilter { contentTypes, scopes ->
                desiredType in contentTypes && desiredScopes.intersect(scopes).isNotEmpty()
            }
        }
    }

}
