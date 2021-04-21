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
import com.android.SdkConstants.MIN_COMPILE_SDK_PROPERTY
import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.lang.StringBuilder
import javax.inject.Inject

/** A task that writes the AAR metadata file  */
@CacheableTask
abstract class AarMetadataTask : NonIncrementalTask() {

    @get:OutputFile
    abstract val output: RegularFileProperty

    // Use a property to hold the [WorkerExecutor] so unit tests can reset it if necessary.
    @get:Internal
    abstract val workerExecutorProperty: Property<WorkerExecutor>

    @get:Input
    abstract val aarFormatVersion: Property<String>

    @get:Input
    abstract val aarMetadataVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val minCompileSdk: Property<Int?>

    override fun doTaskAction() {
        workerExecutorProperty.get().noIsolation().submit(AarMetadataWorkAction::class.java) {
            it.output.set(output)
            it.aarFormatVersion.set(aarFormatVersion)
            it.aarMetadataVersion.set(aarMetadataVersion)
            it.minCompileSdk.set(minCompileSdk)
        }
    }

    class CreationAction(
        creationConfig: LibraryCreationConfig
    ) : VariantTaskCreationAction<AarMetadataTask, LibraryCreationConfig>(creationConfig) {

        override val name: String
            get() = computeTaskName("write", "AarMetadata")

        override val type: Class<AarMetadataTask>
            get() = AarMetadataTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<AarMetadataTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts
                .setInitialProvider(taskProvider, AarMetadataTask::output)
                .withName(AAR_METADATA_FILE_NAME)
                .on(InternalArtifactType.AAR_METADATA)
        }

        override fun configure(task: AarMetadataTask) {
            super.configure(task)

            task.workerExecutorProperty.setDisallowChanges(task.workerExecutor)
            task.aarFormatVersion.setDisallowChanges(AAR_FORMAT_VERSION)
            task.aarMetadataVersion.setDisallowChanges(AAR_METADATA_VERSION)
            task.minCompileSdk.setDisallowChanges(
                creationConfig.variantDslInfo.aarMetadata.minCompileSdk
            )
        }
    }

    companion object {
        const val AAR_METADATA_FILE_NAME = "aar-metadata.properties"
        const val AAR_METADATA_ENTRY_PATH =
            "META-INF/com/android/build/gradle/$AAR_METADATA_FILE_NAME"
        const val AAR_FORMAT_VERSION = "1.0"
        const val AAR_METADATA_VERSION = "1.0"
    }
}

/** [WorkAction] to write AAR metadata file */
abstract class AarMetadataWorkAction @Inject constructor(
    private val aarMetadataWorkParameters: AarMetadataWorkParameters
): WorkAction<AarMetadataWorkParameters> {

    override fun execute() {
        writeAarMetadataFile(
            aarMetadataWorkParameters.output.get().asFile,
            aarMetadataWorkParameters.aarFormatVersion.get(),
            aarMetadataWorkParameters.aarMetadataVersion.get(),
            aarMetadataWorkParameters.minCompileSdk.orNull
        )
    }
}

/** [WorkParameters] for [AarMetadataWorkAction] */
abstract class AarMetadataWorkParameters: WorkParameters {
    abstract val output: RegularFileProperty
    abstract val aarFormatVersion: Property<String>
    abstract val aarMetadataVersion: Property<String>
    abstract val minCompileSdk: Property<Int?>
}

/** Writes an AAR metadata file with the given parameters */
fun writeAarMetadataFile(
    file: File,
    aarFormatVersion: String,
    aarMetadataVersion: String,
    minCompileSdk: Int?
) {
    // We write the file manually instead of using the java.util.Properties API because (1) that API
    // doesn't guarantee the order of properties in the file and (2) that API writes an unnecessary
    // timestamp in the file.
    val stringBuilder = StringBuilder()
    stringBuilder.appendln("$AAR_FORMAT_VERSION_PROPERTY=$aarFormatVersion")
    stringBuilder.appendln("$AAR_METADATA_VERSION_PROPERTY=$aarMetadataVersion")
    minCompileSdk?.let { stringBuilder.appendln("$MIN_COMPILE_SDK_PROPERTY=$it") }
    file.writeText(stringBuilder.toString())
}
