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
import com.android.SdkConstants.EXT_AAR
import com.android.SdkConstants.EXT_JAR
import com.android.SdkConstants.FD_AAR_LIBS
import com.android.SdkConstants.FD_OUTPUTS
import com.android.SdkConstants.FN_LINT_JAR
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryGlobalScope
import com.android.build.gradle.internal.fusedlibrary.FusedLibraryInternalArtifactType
import com.android.build.gradle.internal.tasks.AarMetadataTask
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.GlobalTask
import com.android.build.gradle.internal.tasks.NonIncrementalGlobalTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.packaging.JarFlinger
import java.io.File
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.Optional
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Task does not calculate anything, only creates a jar.")
@BuildAnalyzer(primaryTaskCategory = TaskCategory.AAR_PACKAGING)
abstract class FusedLibraryBundle: Jar(), GlobalTask {

    // We have to explicitly repeat the output file as the artifacts API expects a
    // RegularFileProperty annotated with OutputFile so proper dependency can be expressed.
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * Not used, but intended to run [FusedLibraryDependencyValidationTask]
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val validationDir: DirectoryProperty

    abstract class CreationAction<T: FusedLibraryBundle>(
        val creationConfig: FusedLibraryGlobalScope,
        val artifactType: FusedLibraryInternalArtifactType<RegularFile>
    ) : GlobalTaskCreationAction<T>() {

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
            task.destinationDirectory.set(creationConfig.projectLayout.buildDirectory.dir(task.name))

            task.validationDir.set(
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.DEPENDENCY_VALIDATION)
            )
        }
    }
}

@DisableCachingByDefault(because = "Task does not calculate anything, only creates a jar.")
@BuildAnalyzer(primaryTaskCategory = TaskCategory.AAR_PACKAGING)
abstract class FusedLibraryBundleAar: FusedLibraryBundle() {

    class CreationAction(
        creationConfig: FusedLibraryGlobalScope,
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
            task.destinationDirectory.set(
                creationConfig.projectLayout.buildDirectory.dir("$FD_OUTPUTS/$EXT_AAR"))
            task.archiveFileName.set("${task.project.name}${SdkConstants.DOT_AAR}")
            task.includeEmptyDirs = false

            task.from(
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.CLASSES_JAR),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.LINT_JAR),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_MANIFEST),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_RES),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_AIDL),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_CONSUMER_PROGUARD_RULES),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_RENDERSCRIPT_HEADERS),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_PREFAB_PACKAGE),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_PREFAB_PACKAGE_CONFIGURATION),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_JNI),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_NAVIGATION_JSON),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.COMPILE_SYMBOL_LIST)
            )

            task.from(creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_AAR_METADATA)) {
                it.rename(
                        "aar-metadata.properties",
                        AarMetadataTask.AAR_METADATA_ENTRY_PATH
                )
            }
            task.from(creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_ASSETS)) {
                it.into(SdkConstants.FD_ASSETS)
            }

            task.from(creationConfig.getLocalJars()) {
                it.into(FD_AAR_LIBS)
            }
        }
    }
}

@DisableCachingByDefault(because = "Task does not calculate anything, only creates a jar.")
@BuildAnalyzer(primaryTaskCategory = TaskCategory.COMPILED_CLASSES, secondaryTaskCategories = [TaskCategory.ZIPPING])
abstract class FusedLibraryBundleClasses: NonIncrementalGlobalTask() {

    @get:InputFiles
    @get:Classpath
    abstract val include: ConfigurableFileCollection

    @get:Optional
    @get:OutputFile
    abstract val jar: RegularFileProperty

    override fun doTaskAction() {
        if (include.asFileTree.none(File::isFile)) {
            return
        }
        JarFlinger(jar.get().asFile.toPath()).use { jarFlinger ->
            for (artifact in include) {
                when {
                    artifact.isDirectory -> jarFlinger.addDirectory(artifact.toPath())
                    artifact.extension == EXT_JAR -> jarFlinger.addJar(artifact.toPath())
                    else -> jarFlinger.addFile(artifact.name, artifact.toPath())
                }
            }
        }
    }

    class CreationActionClassesJar(
        val creationConfig: FusedLibraryGlobalScope,
    ): GlobalTaskCreationAction<FusedLibraryBundleClasses>() {

        override val name: String
            get() = "packageClassesJar"
        override val type: Class<FusedLibraryBundleClasses>
            get() = FusedLibraryBundleClasses::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryBundleClasses>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                FusedLibraryBundleClasses::jar
            ).withName("classes.jar").on(FusedLibraryInternalArtifactType.CLASSES_JAR)
        }

        override fun configure(task: FusedLibraryBundleClasses) {
            super.configure(task)
            task.include.from(
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.CLASSES_WITH_REWRITTEN_R_CLASS_REFS),
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_JAVA_RES)
            )
        }
    }

    class CreationActionLintJar(
        val creationConfig: FusedLibraryGlobalScope,
    ): GlobalTaskCreationAction<FusedLibraryBundleClasses>() {

        override val name: String
            get() = "packageLintJar"
        override val type: Class<FusedLibraryBundleClasses>
            get() = FusedLibraryBundleClasses::class.java

        override fun handleProvider(taskProvider: TaskProvider<FusedLibraryBundleClasses>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                FusedLibraryBundleClasses::jar
            ).withName(FN_LINT_JAR).on(FusedLibraryInternalArtifactType.LINT_JAR)
        }

        override fun configure(task: FusedLibraryBundleClasses) {
            super.configure(task)
            task.include.from(
                creationConfig.artifacts.get(FusedLibraryInternalArtifactType.MERGED_PUBLISHED_LINT_CLASSES)
            )
        }
    }
}
