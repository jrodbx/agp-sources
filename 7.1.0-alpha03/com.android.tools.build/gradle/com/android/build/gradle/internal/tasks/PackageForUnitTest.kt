/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.MERGED_ASSETS
import com.android.build.gradle.internal.scope.InternalArtifactType.PROCESSED_RES
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.utils.FileUtils
import com.android.utils.PathUtils
import com.google.common.base.Joiner
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

@CacheableTask
abstract class PackageForUnitTest : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val resApk: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedAssets: DirectoryProperty

    @get:OutputFile
    abstract val apkForUnitTest: RegularFileProperty

    @Throws(IOException::class)
    override fun doTaskAction() {
        // this can certainly be optimized by making it incremental...

        val apkForUnitTest = apkForUnitTest.get().asFile
        FileUtils.copyFile(apkFrom(resApk), apkForUnitTest)

        val uri = URI.create("jar:" + apkForUnitTest.toURI())
        FileSystems.newFileSystem(uri, emptyMap<String, Any>()).use { apkFs ->
            val apkAssetsPath = apkFs.getPath("/assets")
            val mergedAsset= mergedAssets.get()
            val mergedAssetsPath = mergedAsset.asFile.toPath()
            Files.walkFileTree(mergedAssetsPath, object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun visitFile(
                    path: Path,
                    basicFileAttributes: BasicFileAttributes
                ): FileVisitResult {
                    val relativePath = PathUtils.toSystemIndependentPath(
                        mergedAssetsPath.relativize(path)
                    )
                    val destPath = apkAssetsPath.resolve(relativePath)
                    Files.createDirectories(destPath.parent)
                    FileUtils.copyFile(path, destPath)
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }

    internal fun apkFrom(compiledResourcesZip: Provider<Directory>): File {
        val builtArtifacts = BuiltArtifactsLoaderImpl().load(compiledResourcesZip)
            ?: throw RuntimeException("Cannot load resources from $compiledResourcesZip")


        if (builtArtifacts.elements.size == 1) {
            return File(builtArtifacts.elements.first().outputFile)
        }
        builtArtifacts.elements.forEach { builtArtifact ->
            if (builtArtifact.filters.isEmpty()) {
                // universal APK, take it !
                return File(builtArtifact.outputFile)
            }
            if (builtArtifact.filters.size == 1
                && builtArtifact.getFilter(FilterConfiguration.FilterType.ABI) != null) {

                // the only filter is ABI, good enough for getting all resources.
                return File(builtArtifact.outputFile)
            }
        }

        // if we are here, we could not find an appropriate build output, raise this as an error.
        if (builtArtifacts.elements.isEmpty()) {
            throw java.lang.RuntimeException("No resources build output, please file a bug.")
        }
        val sb = StringBuilder("Found following build outputs : \n")
        builtArtifacts.elements.forEach {
            sb.append("BuildOutput: ${Joiner.on(',').join(it.filters)}\n")
        }
        sb.append("Cannot find a build output with all resources, please file a bug.")
        throw RuntimeException(sb.toString())
    }

    class CreationAction(creationConfig: ComponentCreationConfig) :
        VariantTaskCreationAction<PackageForUnitTest, ComponentCreationConfig>(
            creationConfig
        ) {

        override val name = computeTaskName("package", "ForUnitTest")

        override val type = PackageForUnitTest::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<PackageForUnitTest>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PackageForUnitTest::apkForUnitTest
            ).withName("apk-for-local-test.ap_").on(InternalArtifactType.APK_FOR_LOCAL_TEST)
        }

        override fun configure(
            task: PackageForUnitTest
        ) {
            super.configure(task)
            val artifacts = creationConfig.artifacts
            artifacts.setTaskInputToFinalProduct(PROCESSED_RES, task.resApk)
            artifacts.setTaskInputToFinalProduct(MERGED_ASSETS, task.mergedAssets)
        }
    }
}
