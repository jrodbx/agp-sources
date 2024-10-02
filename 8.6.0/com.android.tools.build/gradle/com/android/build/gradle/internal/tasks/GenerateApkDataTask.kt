/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.SdkConstants.DOT_ANDROID_PACKAGE
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FD_RES_RAW
import com.android.SdkConstants.FD_RES_XML
import com.android.build.api.variant.impl.BuiltArtifactsLoaderImpl
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.process.GradleProcessExecutor
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.Aapt2Input
import com.android.build.gradle.internal.services.getAapt2Executable
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.core.ApkInfoParser
import com.android.builder.core.BuilderConstants.ANDROID_WEAR
import com.android.builder.core.BuilderConstants.ANDROID_WEAR_MICRO_APK
import com.android.ide.common.process.ProcessException
import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.collect.Iterables
import com.google.common.io.Files
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.process.ExecOperations
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Task to generate micro app data res file.  */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.APK_PACKAGING, secondaryTaskCategories = [TaskCategory.VERIFICATION, TaskCategory.SOURCE_GENERATION])
abstract class GenerateApkDataTask : NonIncrementalTask() {

    // Tells us if the apk file collection received from task manager exists
    @get:Input
    abstract val hasDependency: Property<Boolean>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val apkFileCollection: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val resOutputDir: DirectoryProperty

    @get:OutputFile
    abstract val manifestFile: RegularFileProperty

    @get:Input
    abstract val minSdkVersion: Property<Int>

    @get:Input
    abstract val targetSdkVersion: Property<Int>

    @get:Nested
    abstract val aapt2: Aapt2Input

    @get:Input
    abstract val mainPkgName: Property<String>

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun doTaskAction() {
        val outDir = resOutputDir.get().asFile

        // if the FileCollection contains no file, then there's nothing to do just abort.
        var apkDirectory: File? = null
        if (hasDependency.get()) {
            val files = apkFileCollection.files
            if (files.isEmpty()) {
                return
            }

            check(files.size <= 1) {
                "Wear App dependency resolve to more than one file: $files"
            }

            apkDirectory = Iterables.getOnlyElement(files)

            check(apkDirectory!!.isDirectory) {
                "Wear App dependency does not resolve to a directory: $files"
            }
        }

        if (apkDirectory != null) {
            val apks = BuiltArtifactsLoaderImpl.loadFromDirectory(apkDirectory)

            check(apks != null) { "Wear App dependency resolve to zero APK" }
            check(!apks.elements.isEmpty()) { "Wear App dependency resolve to zero APK" }

            check(apks.elements.size <= 1) {
                "Wear App dependency resolve to more than one APK: ${apks.elements.map { it.outputFile }}"
            }

            val apk = Iterables.getOnlyElement(apks.elements).outputFile

            // copy the file into the destination, by sanitizing the name first.
            val rawDir = File(outDir, FD_RES_RAW)
            FileUtils.mkdirs(rawDir)

            val to = File(rawDir, ANDROID_WEAR_MICRO_APK + DOT_ANDROID_PACKAGE)
            Files.copy(File(apk), to)

            generateApkData(
                File(apk), outDir, mainPkgName.get(), aapt2.getAapt2Executable().toFile()
            )
        } else {
            generateUnbundledWearApkData(outDir, mainPkgName.get())
        }

        generateApkDataEntryInManifest(
            minSdkVersion.get(),
            targetSdkVersion.get(),
            manifestFile.get().asFile)
    }

    @Throws(ProcessException::class, IOException::class)
    private fun generateApkData(
        apkFile: File,
        outResFolder: File,
        mainPkgName: String,
        aapt2Executable: File
    ) {

        val parser = ApkInfoParser(aapt2Executable, GradleProcessExecutor(execOperations::exec))
        val apkInfo = parser.parseApk(apkFile)

        if (apkInfo.packageName != mainPkgName) {
            throw RuntimeException(
                "The main and the micro apps do not have the same package name."
            )
        }

        val content = """|<?xml version="1.0" encoding="utf-8"?>
                    |<wearableApp package="${apkInfo.packageName}">
                    |    <versionCode>${apkInfo.versionCode}</versionCode>
                    |    <versionName>${apkInfo.versionName}</versionName>
                    |    <rawPathResId>$ANDROID_WEAR_MICRO_APK</rawPathResId>
                    |</wearableApp>""".trimMargin("|")

        // xml folder
        val resXmlFile = File(outResFolder, FD_RES_XML)
        FileUtils.mkdirs(resXmlFile)

        Files.asCharSink(File(resXmlFile, ANDROID_WEAR_MICRO_APK + DOT_XML), Charsets.UTF_8)
            .write(content)
    }

    private fun generateUnbundledWearApkData(outResFolder: File, mainPkgName: String) {
        val content = """|<?xml version="1.0" encoding="utf-8"?>
                    |<wearableApp package="$mainPkgName">
                    |    <unbundled />
                    |</wearableApp>""".trimMargin("|")

        // xml folder
        val resXmlFile = File(outResFolder, FD_RES_XML)
        FileUtils.mkdirs(resXmlFile)

        Files.asCharSink(File(resXmlFile, ANDROID_WEAR_MICRO_APK + DOT_XML), Charsets.UTF_8)
            .write(content)
    }

    private fun generateApkDataEntryInManifest(
        minSdkVersion: Int, targetSdkVersion: Int, manifestFile: File
    ) {

        val targetVersionString =
            if (targetSdkVersion == -1) ""
            else " android:targetSdkVersion=\"$targetSdkVersion\""

        val content = """|<?xml version="1.0" encoding="utf-8"?>
            |<manifest xmlns:android="http://schemas.android.com/apk/res/android">
            |    <uses-sdk android:minSdkVersion="$minSdkVersion"$targetVersionString/>
            |    <application>
            |        <meta-data android:name="$ANDROID_WEAR"
            |                   android:resource="@xml/$ANDROID_WEAR_MICRO_APK" />
            |   </application>
            |</manifest>
            |""".trimMargin("|")

        Files.asCharSink(manifestFile, Charsets.UTF_8).write(content)
    }

    internal class CreationAction(
        creationConfig: ApkCreationConfig,
        private val apkFileCollection: FileCollection?
    ) : VariantTaskCreationAction<GenerateApkDataTask, ApkCreationConfig>(
        creationConfig
    ) {

        override val name: String = computeTaskName("handle", "MicroApk")

        override val type: Class<GenerateApkDataTask> = GenerateApkDataTask::class.java

        override fun handleProvider(
                taskProvider: TaskProvider<GenerateApkDataTask>
        ) {
            super.handleProvider(taskProvider)

            val manifestLocation = creationConfig.paths.generatedDir(
                "manifests",
                "microapk",
                creationConfig.dirName
            ).get().asFile

            creationConfig.artifacts
                    .setInitialProvider(taskProvider, GenerateApkDataTask::manifestFile)
                    .atLocation(manifestLocation.absolutePath)
                    .withName(SdkConstants.FN_ANDROID_MANIFEST_XML)
                    .on(InternalArtifactType.MICRO_APK_MANIFEST_FILE)

            val microApkResDirectory =
                creationConfig.paths.generatedDir(
                            "res",
                            "microapk",
                            creationConfig.dirName).get().asFile
            creationConfig.artifacts
                    .setInitialProvider(taskProvider, GenerateApkDataTask::resOutputDir)
                    .atLocation(microApkResDirectory.absolutePath)
                    .on(InternalArtifactType.MICRO_APK_RES)

            creationConfig.taskContainer.microApkTask = taskProvider
            creationConfig.taskContainer.generateApkDataTask = taskProvider
        }

        override fun configure(
            task: GenerateApkDataTask
        ) {
            super.configure(task)

            apkFileCollection?.let {
                task.apkFileCollection.from(it)
            }
            task.apkFileCollection.disallowChanges()

            task.hasDependency.setDisallowChanges(apkFileCollection != null)

            task.mainPkgName.setDisallowChanges(creationConfig.applicationId)

            task.minSdkVersion.setDisallowChanges(creationConfig.minSdk.apiLevel)

            task.targetSdkVersion.setDisallowChanges(creationConfig.targetSdk.apiLevel)

            creationConfig.services.initializeAapt2Input(task.aapt2)
        }
    }
}
