/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkInternalArtifactType
import com.android.build.gradle.internal.privaysandboxsdk.PrivacySandboxSdkVariantScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.configureVariantProperties
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.errors.IssueReporter
import com.android.utils.TraceUtils.simpleId
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.attributes.Usage
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

/* Task to verify the state of the Privacy Sandbox specific configurations are correct. */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.VERIFICATION)
abstract class PrivacySandboxValidateConfigurationTask : NonIncrementalTask() {

    @get:Input
    abstract val includeConfiguration: Property<ResolvedComponentResult>

    @get:Input
    abstract val requiredConfiguration: Property<ResolvedComponentResult>

    @get:Input
    abstract val optionalConfiguration: Property<ResolvedComponentResult>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val asarFiles: FileCollection get() = sdkArchives.artifactFiles

    private lateinit var sdkArchives: ArtifactCollection

    /**
     * Output directory to for task to report up-to-date, contents will always be empty. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    override fun doTaskAction() {
        val includeDependencies = includeConfiguration.get().dependencies
        val requiredDependencies = requiredConfiguration.get().dependencies
        val optionalDependencies = optionalConfiguration.get().dependencies
        val asarProducingDependencies = sdkArchives.artifacts.map { it.variant.owner }
        val optionalAndRequiredDeps: Set<DependencyResult> = optionalDependencies + requiredDependencies

        val sdksDeclaredInInclude = asarProducingDependencies.filter {
            it in includeDependencies.map { (it as ResolvedDependencyResult).selected.id }
        }
        if (sdksDeclaredInInclude.any()) {
            error("${sdksDeclaredInInclude.joinToString()} " +
                    "must be defined in 'optionalSdk' or 'requiredSdk' configurations only.")
        }

        val undeclaredSdks = asarProducingDependencies
                .minus(optionalAndRequiredDeps.map { (it as ResolvedDependencyResult).selected.id }
                        .toSet())
        if (undeclaredSdks.isNotEmpty()) {
            error("${undeclaredSdks.joinToString()} " +
                    "must also be defined in 'optionalSdk' or 'requiredSdk' configurations.")
        }

    }

    class CreationAction(val creationConfig: PrivacySandboxSdkVariantScope)
        : TaskCreationAction<PrivacySandboxValidateConfigurationTask>() {

        override val name: String
            get() = "validatePrivacySandboxSdkConfiguration"
        override val type: Class<PrivacySandboxValidateConfigurationTask>
            get() = PrivacySandboxValidateConfigurationTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrivacySandboxValidateConfigurationTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    PrivacySandboxValidateConfigurationTask::outputDirectory
            )
                    .on(PrivacySandboxSdkInternalArtifactType.VALIDATE_PRIVACY_SANDBOX_SDK_CONFIGURATIONS)
        }

        override fun configure(task: PrivacySandboxValidateConfigurationTask) {
            task.configureVariantProperties("", task.project.gradle.sharedServices)

            val includeConfiguration = task.project.configurations.getByName("include")
            val requiredConfiguration = task.project.configurations.getByName("requiredSdk")
            val optionalConfiguration = task.project.configurations.getByName("optionalSdk")

            val sdksDeclaredInIncludeAndSdkConfigurations =
                    (requiredConfiguration.incoming.dependencies +
                            optionalConfiguration.incoming.dependencies)
                            .intersect(includeConfiguration.incoming.dependencies)
            if (sdksDeclaredInIncludeAndSdkConfigurations.isNotEmpty()) {
                val dependencyPlural =
                        if (sdksDeclaredInIncludeAndSdkConfigurations.size > 1) "dependencies" else "dependency"
                creationConfig.services.issueReporter
                        .reportError(IssueReporter.Type.EXCEPTION, IllegalStateException(
                                "'include' configuration can not contains dependencies found" +
                                        " in 'requiredSdk' or 'optionalSdk'. " +
                                        "Recommended Action: Remove the following $dependencyPlural from the 'include' configuration: " +
                                        sdksDeclaredInIncludeAndSdkConfigurations.joinToString { it.name }))
            }

            task.includeConfiguration.setDisallowChanges(
                    includeConfiguration.incoming.resolutionResult.rootComponent
            )
            task.requiredConfiguration.setDisallowChanges(
                    requiredConfiguration.incoming.resolutionResult.rootComponent
            )
            task.optionalConfiguration.setDisallowChanges(
                    optionalConfiguration.incoming.resolutionResult.rootComponent
            )

            task.sdkArchives = creationConfig.dependencies.getArtifactCollection(
                    Usage.JAVA_RUNTIME,
                    creationConfig.mergeSpec,
                    AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE
            )
        }
    }
}
