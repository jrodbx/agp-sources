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

package com.android.build.api.variant.impl

import com.android.SdkConstants
import com.android.build.api.variant.ManifestFiles
import com.android.build.gradle.internal.services.VariantServices
import org.gradle.api.Task
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

class ManifestFilesImpl(private val variantServices: VariantServices) : ManifestFiles {

    override fun getName(): String = "manifests"

    override fun addStaticManifestFile(relativeFilePath: String) {
        val file = variantServices.projectInfo.projectDirectory.file(relativeFilePath)
        addSource(
                FileBasedFileEntry(
                        name = "variant",
                        file = file.asFile,
                        isUserAdded = true,
                        shouldBeAddedToIdeModel = true
                )
        )
    }

    override fun <TASK : Task> addGeneratedManifestFile(
            taskProvider: TaskProvider<TASK>,
            wiredWith: (TASK) -> RegularFileProperty
    ) {
        val mappedValue: Provider<RegularFile> = taskProvider.flatMap {
            wiredWith(it)
        }
        taskProvider.configure { task ->
            wiredWith.invoke(task).set(
                    variantServices.projectInfo.buildDirectory.file(
                            "${SdkConstants.FD_GENERATED}/$name/${taskProvider.name}/AndroidManifest.xml"
                    )
            )
        }
        addSource(
                TaskProviderBasedFileEntry(
                        "$name-${taskProvider.name}",
                        mappedValue,
                        isGenerated = true,
                        isUserAdded = true
                )
        )
    }

    /**
     * For internal usage. File may not exist.
     */
    internal fun addSourceFile(file: File) {
        addSource(
                FileBasedFileEntry(
                        name = "variant",
                        file = file,
                        isUserAdded = false,
                        shouldBeAddedToIdeModel = true
                )
        )
    }

    // this will contain all files
    private val files = variantServices.newListPropertyForInternalUse(
            type = RegularFile::class.java,
    )

    override val all: Provider<out List<RegularFile>> = files.map { it.reversed() }

    fun addSource(fileEntry: FileEntry) {
        files.add(
                fileEntry.asFile(
                        variantServices.provider {
                            variantServices.projectInfo.projectDirectory
                        }
                )
        )
    }
}
