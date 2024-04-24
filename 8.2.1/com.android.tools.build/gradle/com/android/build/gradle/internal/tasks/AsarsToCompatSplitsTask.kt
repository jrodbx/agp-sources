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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getAapt2Executable
import com.android.build.gradle.internal.signing.SigningConfigData
import com.android.build.gradle.internal.tasks.Workers.withThreads
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.tasks.BuildPrivacySandboxSdkApks
import com.android.buildanalyzer.common.TaskCategory
import com.android.bundle.RuntimeEnabledSdkConfigProto
import com.android.bundle.RuntimeEnabledSdkConfigProto.SdkSplitPropertiesInheritedFromApp
import com.android.bundle.SdkMetadataOuterClass.SdkMetadata
import com.android.ide.common.workers.ExecutorServiceAdapter
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.tools.build.bundletool.androidtools.Aapt2Command
import com.android.tools.build.bundletool.commands.BuildSdkApksForAppCommand
import com.google.common.util.concurrent.MoreExecutors
import com.google.protobuf.util.JsonFormat
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinPool
import java.util.zip.ZipFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString

/*
 Task to invoke Bundletool for generating splits from all ASAR dependencies.
 Outputs a directory containing '*.apks'.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.APK_PACKAGING)
abstract class AsarsToCompatSplitsTask : NonIncrementalTask() {

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    @get:Optional
    abstract val versionCode: Property<Int?>

    @get:Input
    abstract val minSdk: Property<Int>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val runtimeConfigFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sdkArchives: ConfigurableFileCollection

    @get:Nested
    abstract val signingConfigData: Property<SigningConfigData>

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:OutputDirectory
    abstract val sdkSplits: DirectoryProperty

    @get:Internal
    abstract val tmpDir: DirectoryProperty

    @get:Internal
    abstract val projectFilepath: Property<String>

    @get:Internal
    val workerFacade: ExecutorServiceAdapter
        get() = withThreads(path, analyticsService.get())

    override fun doTaskAction() {
        val runtimeConfigs = getRuntimeEnabledConfigMap(runtimeConfigFile.get().asFile)

        BuildPrivacySandboxSdkApks.forEachInputFile(
                inputFiles = sdkArchives.files.map { it.toPath() },
                outputDirectory = sdkSplits.get().asFile.toPath(),
        ) { archive, subDirectory ->
            Files.createDirectory(subDirectory)
            val tmpDir = tmpDir.get().asFile
            tmpDir.mkdirs()
            val configFile = File(tmpDir, "config.json")

            val sdkMetadata = getSdkMetadataFromAsar(archive)

            val runtimeConfig = runtimeConfigs[sdkMetadata.packageName] ?: throw RuntimeException(
                    "Unable to determine runtimeConfig of ${archive.pathString} with ${sdkMetadata.packageName}")
            val inheritedAppProperties: SdkSplitPropertiesInheritedFromApp =
                    SdkSplitPropertiesInheritedFromApp.newBuilder()
                            .setPackageName(applicationId.get())
                            .setMinSdkVersion(minSdk.get())
                            .setVersionCode(versionCode.orNull ?: 1)
                            .setResourcesPackageId(runtimeConfig.resourcesPackageId)
                            .build();

            configFile.writeText(JsonFormat.printer().print(inheritedAppProperties))

            val apks = subDirectory.resolve("${archive.nameWithoutExtension}.apks")
            BuildSdkApksForAppCommand.builder()
                    .setSdkArchivePath(archive)
                    .setInheritedAppProperties(configFile.toPath())
                    .setAapt2Command(Aapt2Command.createFromExecutablePath(aapt2.getAapt2Executable()
                            .toFile()
                            .toPath()))
                    .setExecutorService(MoreExecutors.listeningDecorator(workerFacade.executor))
                    .setSigningConfiguration(createSigningConfig(signingConfigData.get()))
                    .setOutputFile(apks)
                    .build()
                    .execute()
        }
    }

    private fun getRuntimeEnabledConfigMap(protoFile: File)
            : Map<String, RuntimeEnabledSdkConfigProto.RuntimeEnabledSdk> {
        val runtimeConfigProto = RuntimeEnabledSdkConfigProto.RuntimeEnabledSdkConfig.parseFrom(
                protoFile.readBytes()
        )
        return runtimeConfigProto.runtimeEnabledSdkList.associateBy { it.packageName }
    }

    private fun getSdkMetadataFromAsar(archive: Path)
            : SdkMetadata {
        ZipFile(archive.toFile()).use { zip ->
            zip.getInputStream(zip.getEntry("SdkMetadata.pb")).use { protoBytes ->
                return SdkMetadata.parseFrom(protoBytes)
            }
        }
    }

    class CreationAction(creationConfig: ApplicationCreationConfig) :
            VariantTaskCreationAction<AsarsToCompatSplitsTask, ApplicationCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("asarToCompatSplitsFor")
        override val type: Class<AsarsToCompatSplitsTask>
            get() = AsarsToCompatSplitsTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<AsarsToCompatSplitsTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    AsarsToCompatSplitsTask::sdkSplits
            ).on(InternalArtifactType.SDK_SPLITS_APKS)
        }

        override fun configure(task: AsarsToCompatSplitsTask) {
            super.configure(task)
            task.tmpDir.set(creationConfig.paths.getIncrementalDir(name))
            task.applicationId.setDisallowChanges(creationConfig.applicationId)
            task.minSdk.setDisallowChanges(creationConfig.minSdk.apiLevel)
            task.versionCode.setDisallowChanges(creationConfig.outputs.getMainSplit().versionCode)
            creationConfig.services.initializeAapt2Input(task.aapt2)
            task.sdkArchives.setFrom(
                    creationConfig.variantDependencies.getArtifactFileCollection(
                            AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                            AndroidArtifacts.ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.ANDROID_PRIVACY_SANDBOX_SDK_ARCHIVE
                    )
            )
            val signingConfig = creationConfig.signingConfigImpl ?: throw IllegalStateException(
                    "No signing config configured for variant " + creationConfig.name
            )
            task.signingConfigData.set(
                    creationConfig.services.provider {
                        SigningConfigData.fromSigningConfig(signingConfig)
                    }
            )
            task.runtimeConfigFile.set(
                    creationConfig.artifacts.get(
                            InternalArtifactType.PRIVACY_SANDBOX_SDK_RUNTIME_CONFIG_FILE)
            )
            task.projectFilepath.set(
                    creationConfig.services.projectInfo.projectDirectory.asFile.absolutePath)
        }
    }
}
