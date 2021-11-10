/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.databinding.tool.DataBindingBuilder
import com.android.SdkConstants
import com.android.build.api.artifact.SingleArtifact
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.LIBRARY_AND_LOCAL_JARS_JNI
import com.android.build.gradle.internal.scope.getOutputPath
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.LintModelMetadataTask
import com.android.build.gradle.internal.tasks.VariantAwareTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.BuilderConstants
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.bundling.ZipEntryCompression
import java.util.Locale

/** Custom Zip task to allow archive name to be set lazily. */
abstract class BundleAar : Zip(), VariantAwareTask {

    @Internal
    override lateinit var variantName: String

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val localAarDeps: ConfigurableFileCollection

    @get:Input
    lateinit var projectPath: String
        private set

    @get:Input
    val hasLocalAarDeps: Boolean
        get() {
            val hasLocalAarDependencies = localAarDeps.files.isNotEmpty()
            if (hasLocalAarDependencies) {
                throw RuntimeException(
                    "Direct local .aar file dependencies are not supported when building an AAR. " +
                            "The resulting AAR would be broken because the classes and Android " +
                            "resources from any local .aar file dependencies would not be " +
                            "packaged in the resulting AAR. Previous versions of the Android " +
                            "Gradle Plugin produce broken AARs in this case too (despite not " +
                            "throwing this error). The following direct local .aar file " +
                            "dependencies of the $projectPath project caused this error: " +
                            localAarDeps.files.joinToString { it.absolutePath }
                )
            }
            return hasLocalAarDependencies
        }

    abstract class BaseCreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<BundleAar, ComponentCreationConfig>(
        creationConfig
    ) {
        override val type: Class<BundleAar>
            get() = BundleAar::class.java

        override fun configure(
            task: BundleAar
        ) {
            super.configure(task)

            val artifacts = creationConfig.artifacts
            val buildFeatures = creationConfig.buildFeatures

            // There should never be duplicates.
            task.duplicatesStrategy = DuplicatesStrategy.FAIL
            // Make the AAR reproducible. Note that we package several zips inside the AAR, so all of
            // those need to be reproducible too before we can switch this on.
            // https://issuetracker.google.com/67597902
            task.isReproducibleFileOrder = true
            task.isPreserveFileTimestamps = false

            if (buildFeatures.dataBinding && creationConfig.androidResourcesEnabled) {
                task.from(
                    task.project.provider {
                        creationConfig.artifacts.get(InternalArtifactType.DATA_BINDING_ARTIFACT) },
                    prependToCopyPath(DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR)
                )
                task.from(
                    creationConfig.artifacts.get(
                        InternalArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT),
                    prependToCopyPath(
                        DataBindingBuilder.DATA_BINDING_CLASS_LOG_ROOT_FOLDER_IN_AAR
                    )
                )
            }

            task.from(
                artifacts.get(
                    InternalArtifactType.COMPILE_SYMBOL_LIST))
            task.from(
                artifacts.get(InternalArtifactType.PACKAGED_RES),
                prependToCopyPath(SdkConstants.FD_RES)
            )
            if (!creationConfig.services.projectInfo.getExtension().aaptOptions.namespaced) {
                // In non-namespaced projects bundle the library manifest straight to the AAR.
                task.from(artifacts.get(SingleArtifact.MERGED_MANIFEST))
            } else {
                // In namespaced projects the bundled manifest needs to have stripped resource
                // references for backwards compatibility.
                task.from(artifacts.get(
                    InternalArtifactType.NON_NAMESPACED_LIBRARY_MANIFEST))
                task.from(artifacts.get(InternalArtifactType.RES_STATIC_LIBRARY))
            }
            if (creationConfig.androidResourcesEnabled) {
                task.from(artifacts.get(InternalArtifactType.PUBLIC_RES))
            }
            task.from(artifacts.get(InternalArtifactType.ANNOTATIONS_ZIP))
            task.from(artifacts.get(InternalArtifactType.AAR_MAIN_JAR))
            task.from(
                artifacts.get(InternalArtifactType.AAR_LIBS_DIRECTORY),
                prependToCopyPath(SdkConstants.LIBS_FOLDER)
            )
            task.from(
                creationConfig.artifacts.get(InternalArtifactType.LIBRARY_ASSETS),
                prependToCopyPath(SdkConstants.FD_ASSETS))
            task.from(
                artifacts.get(InternalArtifactType.AAR_METADATA)
            ) {
                it.rename(
                    AarMetadataTask.AAR_METADATA_FILE_NAME,
                    AarMetadataTask.AAR_METADATA_ENTRY_PATH
                )
            }
            task.localAarDeps.from(
                creationConfig.variantScope.getLocalFileDependencies {
                    it.name.toLowerCase(Locale.US).endsWith(SdkConstants.DOT_AAR)
                }
            )
            task.projectPath = task.project.path
        }
    }

    /**
     * Package artifacts similar to [LibraryCreationAction] without aidl, merged proguard,
     * renderscript, jni, lint jar, prefab package.
     */
    class TestFixturesCreationAction(
        creationConfig: ComponentCreationConfig
    ) : BaseCreationAction(creationConfig) {

        override val name: String
            get() = computeTaskName("bundle", "Aar")

        override fun handleProvider(
            taskProvider: TaskProvider<BundleAar>
        ) {
            super.handleProvider(taskProvider)

            val propertyProvider = { task : BundleAar ->
                val property = task.project.objects.fileProperty()
                property.set(task.archiveFile)
                property
            }
            creationConfig.artifacts.setInitialProvider(taskProvider, propertyProvider)
                .on(SingleArtifact.AAR)
        }

        override fun configure(
            task: BundleAar
        ) {
            super.configure(task)
            task.description = "Assembles a bundle containing the testFixtures in ${creationConfig.name}."

            task.archiveFileName.set(creationConfig.outputs.getMainSplit().outputFileName)
            task.destinationDirectory.set(creationConfig.paths.aarLocation)
            task.archiveExtension.set(BuilderConstants.EXT_LIB_ARCHIVE)
        }
    }

    class TestFixturesLocalLintCreationAction(
        creationConfig: ComponentCreationConfig
    ) : BaseCreationAction(creationConfig) {

        override val name: String
            get() = computeTaskName("bundle", "LocalLintAar")

        override fun handleProvider(
            taskProvider: TaskProvider<BundleAar>
        ) {
            super.handleProvider(taskProvider)

            val propertyProvider = { task: BundleAar ->
                val property = task.project.objects.fileProperty()
                property.set(task.archiveFile)
                property
            }
            creationConfig.artifacts
                .setInitialProvider(taskProvider, propertyProvider)
                .on(InternalArtifactType.LOCAL_AAR_FOR_LINT)
        }

        override fun configure(
            task: BundleAar
        ) {
            super.configure(task)
            task.description = "Assembles a bundle containing the testFixtures in ${creationConfig.name}."

            val outputFile =
                InternalArtifactType.LOCAL_AAR_FOR_LINT
                    .getOutputPath(
                        creationConfig.artifacts.buildDirectory,
                        creationConfig.name,
                        "out.aar"
                    )
            task.archiveFileName.set(outputFile.name)
            task.destinationDirectory.set(outputFile.parentFile)

            // No need to compress this archive because it's just an intermediate artifact.
            task.entryCompression = ZipEntryCompression.STORED

            task.from(creationConfig.artifacts.get(InternalArtifactType.LINT_MODEL_METADATA)) {
                it.rename(
                    LintModelMetadataTask.LINT_MODEL_METADATA_FILE_NAME,
                    LintModelMetadataTask.LINT_MODEL_METADATA_ENTRY_PATH
                )
            }
        }
    }

    /**
     * Creation action to produce a local .aar file which is used when running lint on a downstream
     * module.
     */
    class LibraryLocalLintCreationAction(
        creationConfig: ComponentCreationConfig
    ) : AbstractLibraryCreationAction(creationConfig) {

        override val name: String
            get() = computeTaskName("bundle", "LocalLintAar")

        override fun handleProvider(taskProvider: TaskProvider<BundleAar>) {
            super.handleProvider(taskProvider)

            val propertyProvider = { task: BundleAar ->
                val property = task.project.objects.fileProperty()
                property.set(task.archiveFile)
                property
            }
            creationConfig.artifacts
                .setInitialProvider(taskProvider, propertyProvider)
                .on(InternalArtifactType.LOCAL_AAR_FOR_LINT)
        }

        override fun configure(task: BundleAar) {
            super.configure(task)

            val outputFile =
                InternalArtifactType.LOCAL_AAR_FOR_LINT
                    .getOutputPath(
                        creationConfig.artifacts.buildDirectory,
                        creationConfig.name,
                        "out.aar"
                    )
            task.archiveFileName.set(outputFile.name)
            task.destinationDirectory.set(outputFile.parentFile)

            // No need to compress this archive because it's just an intermediate artifact.
            task.entryCompression = ZipEntryCompression.STORED

            task.from(creationConfig.artifacts.get(InternalArtifactType.LINT_MODEL_METADATA)) {
                it.rename(
                    LintModelMetadataTask.LINT_MODEL_METADATA_FILE_NAME,
                    LintModelMetadataTask.LINT_MODEL_METADATA_ENTRY_PATH
                )
            }
        }
    }

    class LibraryCreationAction(
        creationConfig: ComponentCreationConfig
    ) : AbstractLibraryCreationAction(creationConfig) {

        override val name: String
            get() = computeTaskName("bundle", "Aar")

        override fun handleProvider(
            taskProvider: TaskProvider<BundleAar>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.bundleLibraryTask = taskProvider

            val propertyProvider = { task: BundleAar ->
                val property = task.project.objects.fileProperty()
                property.set(task.archiveFile)
                property
            }
            creationConfig.artifacts.setInitialProvider(taskProvider, propertyProvider)
                .on(SingleArtifact.AAR)
        }

        override fun configure(task: BundleAar) {
            super.configure(task)

            task.archiveFileName.set(creationConfig.outputs.getMainSplit().outputFileName)
            task.destinationDirectory.set(creationConfig.paths.aarLocation)
            task.archiveExtension.set(BuilderConstants.EXT_LIB_ARCHIVE)
        }
    }

    abstract class AbstractLibraryCreationAction(
        creationConfig: ComponentCreationConfig
    ) : BaseCreationAction(creationConfig){

        override fun configure(
            task: BundleAar
        ) {
            super.configure(task)

            val artifacts = creationConfig.artifacts
            val buildFeatures = creationConfig.buildFeatures

            task.description = "Assembles a bundle containing the library in ${creationConfig.name}."

            if (buildFeatures.aidl) {
                task.from(
                    creationConfig.artifacts.get(
                        InternalArtifactType.AIDL_PARCELABLE
                    ),
                    prependToCopyPath(SdkConstants.FD_AIDL)
                )
            }

            task.from(artifacts.get(
                InternalArtifactType.MERGED_CONSUMER_PROGUARD_FILE))

            if (buildFeatures.renderScript) {
                task.from(
                    artifacts.get(InternalArtifactType.RENDERSCRIPT_HEADERS),
                    prependToCopyPath(SdkConstants.FD_RENDERSCRIPT)
                )
            }
            task.from(
                artifacts.get(LIBRARY_AND_LOCAL_JARS_JNI),
                prependToCopyPath(SdkConstants.FD_JNI)
            )
            task.from(creationConfig.artifacts.get(InternalArtifactType.LINT_PUBLISH_JAR))

            if (buildFeatures.prefabPublishing) {
                task.from(
                    artifacts.get(InternalArtifactType.PREFAB_PACKAGE),
                    prependToCopyPath(SdkConstants.FD_PREFAB_PACKAGE)
                )
            }
            if (creationConfig.services.projectOptions[BooleanOption.ENABLE_ART_PROFILES]) {
                task.from(
                        creationConfig.artifacts.get(InternalArtifactType.LIBRARY_ART_PROFILE)
                )
            }
        }
    }

    companion object {
        private fun prependToCopyPath(pathSegment: String) = Action { copySpec: CopySpec ->
            copySpec.eachFile { fileCopyDetails: FileCopyDetails ->
                fileCopyDetails.relativePath =
                    fileCopyDetails.relativePath.prepend(pathSegment)
            }
        }
    }
}
