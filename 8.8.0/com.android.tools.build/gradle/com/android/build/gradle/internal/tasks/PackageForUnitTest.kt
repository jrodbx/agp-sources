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

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.caching.DisabledCachingReason.SIMPLE_MERGING_TASK
import com.android.build.gradle.internal.component.HostTestCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.LINKED_RESOURCES_BINARY_FORMAT
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.internal.packaging.ApkFlinger
import com.android.builder.packaging.PackagingUtils
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode
import com.android.utils.FileUtils
import com.android.utils.PathUtils
import com.google.common.base.Joiner
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.Deflater.BEST_SPEED

/**
 * Task that takes the linked android resources zip and android assets and puts them into a zip file
 * without code or signing.
 */
@DisableCachingByDefault(because = SIMPLE_MERGING_TASK)
@BuildAnalyzer(primaryTaskCategory = TaskCategory.TEST, secondaryTaskCategories = [TaskCategory.APK_PACKAGING])
abstract class PackageForHostTest : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val resApk: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedAssetsDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val noCompress: ListProperty<String>

    @get:OutputFile
    abstract val apkForHostTest: RegularFileProperty

    override fun doTaskAction() {
        // this can certainly be optimized by making it incremental...
        val apkForHostTest = apkForHostTest.get().asFile
        FileUtils.copyFile(apkFrom(resApk), apkForHostTest)
        val creationData =
            ApkCreatorFactory.CreationData.builder()
                .setApkPath(apkForHostTest)
                .setNativeLibrariesPackagingMode(NativeLibrariesPackagingMode.COMPRESSED)
                .setNoCompressPredicate(
                    PackagingUtils.getNoCompressPredicateForJavaRes(noCompress.get())::test
                )
                .build()
        val apkAssetsPath = Paths.get("assets")
        val mergedAssets = mergedAssetsDirectory.get().asFile
        if (!mergedAssets.exists()) {
            return
        }
        val mergedAssetsPath = mergedAssets.toPath()
        ApkFlinger(creationData, BEST_SPEED).use { apkCreator ->
            Files.walkFileTree(mergedAssetsPath, object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun visitFile(
                    path: Path,
                    basicFileAttributes: BasicFileAttributes
                ): FileVisitResult {
                    val relativePath = PathUtils.toSystemIndependentPath(
                        mergedAssetsPath.relativize(path)
                    )
                    val destPath = PathUtils.toSystemIndependentPath(
                        apkAssetsPath.resolve(relativePath)
                    )
                    apkCreator.writeFile(path.toFile(), destPath)
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

    class CreationAction(creationConfig: HostTestCreationConfig) :
        VariantTaskCreationAction<PackageForHostTest, HostTestCreationConfig>(
            creationConfig
        ) {

        override val name = computeTaskName(
            "package", "For${creationConfig.componentType.suffix}"
        )
        override val type = PackageForHostTest::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<PackageForHostTest>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PackageForHostTest::apkForHostTest
            ).withName("apk-for-local-test.ap_").on(InternalArtifactType.APK_FOR_LOCAL_TEST)


        }

        override fun configure(
            task: PackageForHostTest
        ) {
            super.configure(task)
            val artifacts = creationConfig.artifacts
            artifacts.setTaskInputToFinalProduct(LINKED_RESOURCES_BINARY_FORMAT, task.resApk)
            task.mergedAssetsDirectory.setDisallowChanges(artifacts.get(SingleArtifact.ASSETS))
            creationConfig.androidResources?.let {
                task.noCompress.setDisallowChanges(it.noCompress)
            }
        }
    }
}
