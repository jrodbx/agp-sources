/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryVariantScope
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Task does not calculate anything, only creates a jar.")
@BuildAnalyzer(primaryTaskCategory = TaskCategory.AAR_PACKAGING)
abstract class FusedLibraryBundle: Jar() {

    // We have to explicitly repeat the output file as the artifacts API expects a
    // RegularFileProperty annotated with OutputFile so proper dependency can be expressed.
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    abstract class CreationAction<T: FusedLibraryBundle>(
        val creationConfig: FusedLibraryVariantScope,
        val artifactType: FusedLibraryInternalArtifactType<RegularFile>
    ) : TaskCreationAction<T>() {

        override fun handleProvider(taskProvider: TaskProvider<T>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                    taskProvider,
                    FusedLibraryBundle::outputFile
            ).on(artifactType)
        }

        override fun configure(task: T) {

            // manually resets the output value to the one this task will use.
            task.outputFile.set(task.archiveFile)
            task.destinationDirectory.set(creationConfig.layout.buildDirectory.dir(task.name))
        }
    }
}

@DisableCachingByDefault(because = "Task does not calculate anything, only creates a jar.")
@BuildAnalyzer(primaryTaskCategory = TaskCategory.AAR_PACKAGING)
abstract class FusedLibraryBundleAar: FusedLibraryBundle() {

    class CreationAction(
        creationConfig: FusedLibraryVariantScope,
    ) : FusedLibraryBundle.CreationAction<FusedLibraryBundleAar>(
            creationConfig,
            FusedLibraryInternalArtifactType.BUNDLED_LIBRARY,
    ) {

        override val name: String
            get() = "bundle"
        override val type: Class<FusedLibraryBundleAar>
            get() = FusedLibraryBundleAar::class.java

        override fun configure(task: FusedLibraryBundleAar) {
            super.configure(task)
            task.archiveFileName.set("bundle.aar")
            task.from(
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_MANIFEST),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_RES),

                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_AIDL),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_RENDERSCRIPT_HEADERS),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_PREFAB_PACKAGE),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_PREFAB_PACKAGE_CONFIGURATION),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_JNI),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_NAVIGATION_JSON),
            )

            task.from(creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_AAR_METADATA)) {
                it.rename(
                        "aar_metadata",
                        AarMetadataTask.AAR_METADATA_ENTRY_PATH
                )
            }
            task.from(creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_ASSETS)) {
                it.into(SdkConstants.FD_ASSETS)
            }
        }
    }
}

@DisableCachingByDefault(because = "Task does not calculate anything, only creates a jar.")
@BuildAnalyzer(primaryTaskCategory = TaskCategory.COMPILED_CLASSES, secondaryTaskCategories = [TaskCategory.ZIPPING])
abstract class FusedLibraryBundleClasses: FusedLibraryBundle() {

    class CreationAction(
        creationConfig: FusedLibraryVariantScope,
    ): FusedLibraryBundle.CreationAction<FusedLibraryBundleClasses>(
            creationConfig,
            FusedLibraryInternalArtifactType.CLASSES_JAR
    ) {
        override val name: String
            get() = "packageJar"
        override val type: Class<FusedLibraryBundleClasses>
            get() = FusedLibraryBundleClasses::class.java

        override fun configure(task: FusedLibraryBundleClasses) {
            super.configure(task)
            task.archiveFileName.set("classes.jar")
            task.from(
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.CLASSES_WITH_REWRITTEN_R_CLASS_REFS),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.FUSED_R_CLASS),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_JAVA_RES)
            )
        }
    }
}
