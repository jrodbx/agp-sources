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

package com.android.build.gradle.internal.plugins

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.dsl.AssetPackBundleExtension
import com.android.build.api.dsl.SigningConfig
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.errors.DeprecationReporterImpl
import com.android.build.gradle.internal.errors.SyncIssueReporterImpl
import com.android.build.gradle.internal.lint.LintFromMaven
import com.android.build.gradle.internal.profile.AnalyticsService
import com.android.build.gradle.internal.profile.NoOpAnalyticsService
import com.android.build.gradle.internal.res.Aapt2FromMaven.Companion.create
import com.android.build.gradle.internal.scope.ProjectInfo
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2ThreadPoolBuildService
import com.android.build.gradle.internal.services.AndroidLocationsBuildService
import com.android.build.gradle.internal.services.DslServicesImpl
import com.android.build.gradle.internal.services.ProjectServices
import com.android.build.gradle.internal.services.getBuildServiceName
import com.android.build.gradle.internal.tasks.AppMetadataTask
import com.android.build.gradle.internal.tasks.AssetPackPreBundleTask
import com.android.build.gradle.internal.tasks.FinalizeBundleTask
import com.android.build.gradle.internal.tasks.LinkManifestForAssetPackTask
import com.android.build.gradle.internal.tasks.PackageBundleTask
import com.android.build.gradle.internal.tasks.ProcessAssetPackManifestTask
import com.android.build.gradle.internal.tasks.ValidateSigningTask
import com.android.build.gradle.internal.tasks.factory.TaskConfigAction
import com.android.build.gradle.internal.tasks.factory.TaskFactoryImpl
import com.android.build.gradle.internal.tasks.populateAssetPacksConfigurations
import com.android.build.gradle.options.ProjectOptionService
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.SyncOptions
import com.android.builder.errors.IssueReporter
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File

/**
 * Plugin that allows to build asset-pack bundles.
 *
 * <p>Asset-pack bundle is supported by Google Play only.
 *
 * <p>Asset-pack bundle is a special kind of Android App Bundle that contains only subset of
 * on-demand / fast-follow asset-packs. This bundle can be used as a patch to a regular bundle and
 * allows to update asset-packs without requirement to release a new app version. This is mainly
 * valuable for game developers who whom updating assets (new levels, new textures) is more
 * frequent than code updates.
 */
class AssetPackBundlePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val projectOptions = ProjectOptionService.RegistrationAction(project)
            .execute()
            .get()
            .projectOptions

        val syncIssueHandler = SyncIssueReporterImpl(
            SyncOptions.getModelQueryMode(projectOptions),
            SyncOptions.getErrorFormatMode(projectOptions),
            project.logger
        )

        val deprecationReporter =
            DeprecationReporterImpl(syncIssueHandler, projectOptions, project.path)

        val projectServices = ProjectServices(
            syncIssueHandler,
            deprecationReporter,
            project.objects,
            project.logger,
            project.providers,
            project.layout,
            projectOptions,
            project.gradle.sharedServices,
            LintFromMaven.from(project, projectOptions, syncIssueHandler),
            create(project, projectOptions),
            project.gradle.startParameter.maxWorkerCount,
            ProjectInfo(project),
            project::file
        )
        registerServices(project, projectOptions)

        val dslServices = DslServicesImpl(
            projectServices,
            sdkComponents = projectServices.providerFactory.provider { null }
        )
        val extension =
            dslServices.newDecoratedInstance(AssetPackBundleExtension::class.java, dslServices)
        project.extensions.add(AssetPackBundleExtension::class.java, "bundle", extension)

        project.afterEvaluate {
            validateInput(projectServices.issueReporter, extension)
            createTasks(project, projectServices, extension)
        }
    }

    private fun registerServices(project: Project, projectOptions: ProjectOptions) {
        if (projectOptions.isAnalyticsEnabled) {
            AnalyticsService.RegistrationAction(project).execute()
        } else {
            project.gradle.sharedServices.registerIfAbsent(
                getBuildServiceName(AnalyticsService::class.java),
                NoOpAnalyticsService::class.java,
            ) {}
        }
        Aapt2ThreadPoolBuildService.RegistrationAction(project, projectOptions).execute()
        Aapt2DaemonBuildService.RegistrationAction(project, projectOptions).execute()
        SyncIssueReporterImpl.GlobalSyncIssueService.RegistrationAction(
            project,
            SyncOptions.getModelQueryMode(projectOptions),
            SyncOptions.getErrorFormatMode(projectOptions)
        )
            .execute()
        AndroidLocationsBuildService.RegistrationAction(project).execute()
        SdkComponentsBuildService.RegistrationAction(project, projectOptions).execute()
    }

    private fun validateInput(issueReporter: IssueReporter, extension: AssetPackBundleExtension) {
        val errors = arrayListOf<String>()
        if (extension.applicationId.isEmpty()) {
            errors.add("'applicationId' must be specified for asset pack bundle.")
        }
        if (extension.versionTag.isEmpty()) {
            errors.add("'versionTag' must be specified for asset pack bundle.")
        }
        if (extension.versionCodes.isEmpty()) {
            errors.add("Asset pack bundle must target at least one version code.")
        }
        if (extension.assetPacks.isEmpty()) {
            errors.add("Asset pack bundle must contain at least one asset pack.")
        }

        val signingConfig = extension.signingConfig
        if (signingConfig.isPresent()) {
            if (signingConfig.storeFile == null ||
                signingConfig.storePassword == null ||
                signingConfig.keyAlias == null ||
                signingConfig.keyPassword == null) {
                errors.add(
                    "Signing config is specified but incomplete. To make it complete " +
                        "'storeFile', 'storePassword', 'keyAlias', 'keyPassword' must be specified."
                )
            }
        }

        if (errors.isNotEmpty()) {
            issueReporter.reportWarning(
                IssueReporter.Type.GENERIC,
                errors.joinToString(separator = "\n"),
                multilineMsg = errors
            )
        }
    }

    private fun createTasks(
        project: Project,
        projectServices: ProjectServices,
        extension: AssetPackBundleExtension
    ) {
        val assetPackFilesConfiguration =
            project.configurations.maybeCreate("assetPackFiles")
        val assetPackManifestConfiguration =
            project.configurations.maybeCreate("assetPackManifest")
        populateAssetPacksConfigurations(
            project,
            projectServices.issueReporter,
            extension.assetPacks,
            assetPackFilesConfiguration,
            assetPackManifestConfiguration
        )

        val tasks = TaskFactoryImpl(project.tasks)
        val artifacts = ArtifactsImpl(project, "global")

        tasks.register(AppMetadataTask.CreationForAssetPackBundleAction(artifacts, projectServices.projectOptions))

        tasks.register(
            ProcessAssetPackManifestTask.CreationForAssetPackBundleAction(
                artifacts,
                extension.applicationId,
                assetPackManifestConfiguration.incoming.artifacts
            )
        )

        tasks.register(
            LinkManifestForAssetPackTask.CreationForAssetPackBundleAction(
                artifacts,
                projectServices,
                extension.compileSdk!!
            )
        )

        tasks.register(
            AssetPackPreBundleTask.CreationForAssetPackBundleAction(
                artifacts,
                assetPackFilesConfiguration
            )
        )

        tasks.register(
            PackageBundleTask.CreationForAssetPackBundleAction(
                projectServices,
                artifacts,
                extension
            )
        )

        if (extension.signingConfig.isPresent()) {
            tasks.register(
                ValidateSigningTask.CreationForAssetPackBundleAction(
                    artifacts,
                    extension.signingConfig
                )
            )
        }

        tasks.register(
            FinalizeBundleTask.CreationForAssetPackBundleAction(
                projectServices,
                artifacts,
                extension.signingConfig,
                extension.signingConfig.isPresent()
            )
        )

        tasks.register(
            "bundle",
            null,
            object : TaskConfigAction<Task> {
                override fun configure(task: Task) {
                    task.description = "Assembles asset pack bundle for asset only updates"
                    task.dependsOn(artifacts.get(SingleArtifact.BUNDLE))
                }
            }
        )
    }
}

private fun SigningConfig.isPresent(): Boolean {
    return this.storeFile != null ||
            this.storePassword != null ||
            this.keyAlias != null ||
            this.keyPassword != null
}
