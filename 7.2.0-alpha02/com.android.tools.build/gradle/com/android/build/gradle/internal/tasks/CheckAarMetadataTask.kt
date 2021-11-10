/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.SdkConstants.AAR_FORMAT_VERSION_PROPERTY
import com.android.SdkConstants.AAR_METADATA_VERSION_PROPERTY
import com.android.SdkConstants.MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY
import com.android.SdkConstants.MIN_COMPILE_SDK_PROPERTY
import com.android.Version
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.parseTargetHash
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.repository.GradleVersion
import com.android.repository.Revision
import com.android.sdklib.SdkVersionInfo
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File
import java.io.Serializable
import java.util.Properties

/** A task that reads the dependencies' AAR metadata files and checks for compatibility */
@CacheableTask
abstract class CheckAarMetadataTask : NonIncrementalTask() {

    // Dummy output allows this task to be up-to-date, and it provides a means of making other tasks
    // depend on this task.
    @get:OutputDirectory
    abstract val dummyOutputDirectory: DirectoryProperty

    @VisibleForTesting
    @get:Internal
    internal lateinit var aarMetadataArtifacts: ArtifactCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val aarMetadataFiles: FileCollection
        get() = aarMetadataArtifacts.artifactFiles

    @get:Input
    abstract val aarFormatVersion: Property<String>

    @get:Input
    abstract val aarMetadataVersion: Property<String>

    @get:Input
    abstract val compileSdkVersion: Property<String>

    @get:Input
    abstract val agpVersion: Property<String>

    override fun doTaskAction() {
        workerExecutor.noIsolation().submit(
            CheckAarMetadataWorkAction::class.java
        ) {
            it.aarMetadataArtifacts.addAll(
                aarMetadataArtifacts.artifacts.map { artifact ->
                    AarMetadataArtifact(
                        artifact.file,
                        when (val id = artifact.id.componentIdentifier) {
                            is LibraryBinaryIdentifier -> id.projectPath
                            is ModuleComponentIdentifier -> "${id.group}:${id.module}:${id.version}"
                            is ProjectComponentIdentifier -> id.projectPath
                            else -> id.displayName
                        }
                    )
                }
            )
            it.aarFormatVersion.set(aarFormatVersion)
            it.aarMetadataVersion.set(aarMetadataVersion)
            it.compileSdkVersion.set(compileSdkVersion)
            it.agpVersion.set(agpVersion)
            it.projectPath.set(projectPath)
        }
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<CheckAarMetadataTask, ComponentCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("check", "AarMetadata")

        override val type: Class<CheckAarMetadataTask>
            get() = CheckAarMetadataTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<CheckAarMetadataTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts
                .setInitialProvider(taskProvider, CheckAarMetadataTask::dummyOutputDirectory)
                .on(InternalArtifactType.AAR_METADATA_CHECK)
        }

        override fun configure(task: CheckAarMetadataTask) {
            super.configure(task)

            task.aarMetadataArtifacts =
                creationConfig.variantDependencies.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.AAR_METADATA
                )
            task.aarFormatVersion.setDisallowChanges(AarMetadataTask.AAR_FORMAT_VERSION)
            task.aarMetadataVersion.setDisallowChanges(AarMetadataTask.AAR_METADATA_VERSION)
            task.compileSdkVersion.setDisallowChanges(
                creationConfig.globalScope.extension.compileSdkVersion
                    ?: throw RuntimeException("compileSdkVersion is not specified.")
            )
            task.agpVersion.setDisallowChanges(Version.ANDROID_GRADLE_PLUGIN_VERSION)
        }
    }
}

/** [WorkAction] to check AAR metadata files */
abstract class CheckAarMetadataWorkAction: WorkAction<CheckAarMetadataWorkParameters> {

    override fun execute() {
        val errorMessages: MutableList<String> =
            mutableListOf("One or more issues found when checking AAR metadata values:")
        parameters.aarMetadataArtifacts.get().forEach {
            checkAarMetadataArtifact(it, errorMessages)
        }
        if (errorMessages.size > 1) {
            throw RuntimeException(errorMessages.joinToString(separator = "\n\n"))
        }
    }

    /** Checks the aarMetadataArtifact and add any errors to be reported to errorMessages */
    private fun checkAarMetadataArtifact(
        aarMetadataArtifact: AarMetadataArtifact,
        errorMessages: MutableList<String>
    ) {
        val aarMetadataFile = aarMetadataArtifact.file
        val displayName = aarMetadataArtifact.displayName
        val aarMetadataReader = AarMetadataReader(aarMetadataFile)

        // check aarFormatVersion
        val aarFormatVersion = aarMetadataReader.aarFormatVersion
        if (aarFormatVersion == null) {
            errorMessages.add(
                """
                    A dependency's AAR metadata (${AarMetadataTask.AAR_METADATA_ENTRY_PATH}) does
                    not specify an $AAR_FORMAT_VERSION_PROPERTY value, which is a required value.
                    Dependency: $displayName.
                    AAR metadata file: ${aarMetadataFile.absolutePath}.
                    """.trimIndent()
            )
        } else {
            try {
                val majorAarVersion = Revision.parseRevision(aarFormatVersion).major
                val maxMajorAarVersion =
                    Revision.parseRevision(parameters.aarFormatVersion.get())
                        .major
                if (majorAarVersion > maxMajorAarVersion) {
                    errorMessages.add(
                        """
                            The $AAR_FORMAT_VERSION_PROPERTY ($aarFormatVersion) specified in a
                            dependency's AAR metadata (${AarMetadataTask.AAR_METADATA_ENTRY_PATH})
                            is not compatible with this version of the Android Gradle Plugin.
                            Please upgrade to a newer version of the Android Gradle Plugin.
                            Dependency: $displayName.
                            AAR metadata file: ${aarMetadataFile.absolutePath}.
                            """.trimIndent()
                    )
                }
            } catch (e: NumberFormatException) {
                errorMessages.add(
                    """
                        A dependency's AAR metadata (${AarMetadataTask.AAR_METADATA_ENTRY_PATH})
                        has an invalid $AAR_FORMAT_VERSION_PROPERTY value.
                        ${e.message}
                        Dependency: $displayName.
                        AAR metadata file: ${aarMetadataFile.absolutePath}.
                        """.trimIndent()
                )
            }
        }

        // check aarMetadataVersion
        val aarMetadataVersion = aarMetadataReader.aarMetadataVersion
        if (aarMetadataVersion == null) {
            errorMessages.add(
                """
                    A dependency's AAR metadata (${AarMetadataTask.AAR_METADATA_ENTRY_PATH}) does
                    not specify an $AAR_METADATA_VERSION_PROPERTY value, which is a required value.
                    Dependency: $displayName.
                    AAR metadata file: ${aarMetadataFile.absolutePath}.
                    """.trimIndent()
            )
        } else {
            try {
                val majorAarMetadataVersion = Revision.parseRevision(aarMetadataVersion).major
                val maxMajorAarMetadataVersion =
                    Revision.parseRevision(parameters.aarMetadataVersion.get())
                        .major
                if (majorAarMetadataVersion > maxMajorAarMetadataVersion) {
                    errorMessages.add(
                        """
                            The $AAR_METADATA_VERSION_PROPERTY ($aarMetadataVersion) specified in a
                            dependency's AAR metadata (${AarMetadataTask.AAR_METADATA_ENTRY_PATH})
                            is not compatible with this version of the Android Gradle Plugin.
                            Please upgrade to a newer version of the Android Gradle Plugin.
                            Dependency: $displayName.
                            AAR metadata file: ${aarMetadataFile.absolutePath}.
                            """.trimIndent()
                    )
                }
            } catch (e: NumberFormatException) {
                errorMessages.add(
                    """
                        A dependency's AAR metadata (${AarMetadataTask.AAR_METADATA_ENTRY_PATH})
                        has an invalid $AAR_METADATA_VERSION_PROPERTY value.
                        ${e.message}
                        Dependency: $displayName.
                        AAR metadata file: ${aarMetadataFile.absolutePath}.
                        """.trimIndent()
                )
            }
        }

        // check compileSdkVersion
        val minCompileSdk = aarMetadataReader.minCompileSdk
        if (minCompileSdk != null) {
            val minCompileSdkInt = minCompileSdk.toIntOrNull()
            if (minCompileSdkInt == null) {
                errorMessages.add(
                    """
                        A dependency's AAR metadata (${AarMetadataTask.AAR_METADATA_ENTRY_PATH})
                        has an invalid $MIN_COMPILE_SDK_PROPERTY value. $MIN_COMPILE_SDK_PROPERTY
                        must be an integer.
                        Dependency: $displayName.
                        AAR metadata file: ${aarMetadataFile.absolutePath}.
                        """.trimIndent()
                )
            } else {
                val compileSdkVersion = parameters.compileSdkVersion.get()
                val compileSdkVersionInt = getApiIntFromString(compileSdkVersion)
                if (minCompileSdkInt > compileSdkVersionInt) {
                    // TODO(b/199900566) - change compileSdkVersion to compileSdk for AGP 8.0.
                    errorMessages.add(
                        """
                            Dependency '$displayName' requires 'compileSdkVersion' to be set to $minCompileSdk or higher.
                            Compilation target for module '${parameters.projectPath.get()}' is '$compileSdkVersion'.
                            """.trimIndent()
                    )
                }
            }
        }

        // check agpVersion
        val minAgpVersion = aarMetadataReader.minAgpVersion
        if (minAgpVersion != null) {
            val parsedMinAgpVersion =
                GradleVersion.tryParseStableAndroidGradlePluginVersion(minAgpVersion)
            if (parsedMinAgpVersion == null) {
                errorMessages.add(
                    """
                        A dependency's AAR metadata (${AarMetadataTask.AAR_METADATA_ENTRY_PATH})
                        has an invalid $MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY value
                        ($minAgpVersion). $MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY must be a a
                        stable AGP version, formatted with major, minor, and micro values (for
                        example "4.0.0").
                        Dependency: $displayName.
                        AAR metadata file: ${aarMetadataFile.absolutePath}.
                        """.trimIndent()
                )
            } else {
                val parsedAgpVersion =
                    GradleVersion.parseAndroidGradlePluginVersion(parameters.agpVersion.get())
                if (parsedMinAgpVersion > parsedAgpVersion) {
                    errorMessages.add(
                        """
                            Dependency '$displayName' requires an Android Gradle Plugin version of $minAgpVersion or higher.
                            The Android Gradle Plugin version used for this build is ${parameters.agpVersion.get()}.
                            """.trimIndent()
                    )
                }
            }
        }
    }

    /**
     * Return the [Int] API version, given a string representation, or
     * [SdkVersionInfo.HIGHEST_KNOWN_API] + 1 if an unknown version.
     */
    private fun getApiIntFromString(sdkVersion: String): Int {
        val compileData = parseTargetHash(sdkVersion)
        if (compileData.apiLevel != null) {
            // this covers normal compileSdk + addons.
            return compileData.apiLevel
        }

        if (compileData.codeName != null) {
            return SdkVersionInfo.getApiByPreviewName(compileData.codeName, true)
        }

        // this should not happen since the target hash should be valid (this is running inside a
        // task).
        throw RuntimeException("Unsupported target hash: $sdkVersion")
    }
}

/** [WorkParameters] for [CheckAarMetadataWorkAction] */
abstract class CheckAarMetadataWorkParameters: WorkParameters {
    abstract val aarMetadataArtifacts: ListProperty<AarMetadataArtifact>
    abstract val aarFormatVersion: Property<String>
    abstract val aarMetadataVersion: Property<String>
    abstract val compileSdkVersion: Property<String>
    abstract val agpVersion: Property<String>
    abstract val projectPath: Property<String>
}

private data class AarMetadataReader(val file: File) {

    val aarFormatVersion: String?
    val aarMetadataVersion: String?
    val minCompileSdk: String?
    val minAgpVersion: String?

    init {
        val properties = Properties()
        file.inputStream().use { properties.load(it) }
        aarFormatVersion = properties.getProperty(AAR_FORMAT_VERSION_PROPERTY)
        aarMetadataVersion = properties.getProperty(AAR_METADATA_VERSION_PROPERTY)
        minCompileSdk = properties.getProperty(MIN_COMPILE_SDK_PROPERTY)
        minAgpVersion = properties.getProperty(MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY)
    }
}

data class AarMetadataArtifact(val file: File, val displayName: String): Serializable
