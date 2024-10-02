/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.bundle.AppIntegrityConfigOuterClass.AppIntegrityConfig
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.w3c.dom.Document
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory

private const val CONFIG_FILE_NAME = "IntegrityConfig.xml"

/**
 * Task that parses app_integrity_config.proto from an XML file.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.METADATA)
abstract class ParseIntegrityConfigTask : NonIncrementalTask() {

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val integrityConfigDir: DirectoryProperty

    @get:OutputFile
    abstract val appIntegrityConfigProto: RegularFileProperty

    public override fun doTaskAction() {
        workerExecutor.noIsolation().submit(ParseIntegrityConfigRunnable::class.java) {
            it.initializeFromAndroidVariantTask(this)
            it.integrityConfigDir.set(integrityConfigDir)
            it.appIntegrityConfigProto.set(appIntegrityConfigProto)
        }
    }

    @VisibleForTesting
    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val integrityConfigDir: DirectoryProperty
        abstract val appIntegrityConfigProto: RegularFileProperty
    }

    @VisibleForTesting
    abstract class ParseIntegrityConfigRunnable  : ProfileAwareWorkAction<Params>() {
        override fun run() {
            parameters.integrityConfigDir.asFile.orNull?.let {
                if (!it.isDirectory) {
                    throw FileNotFoundException("Could not find directory ${it.absolutePath}")
                }
                val doc = loadXML(it.resolve(CONFIG_FILE_NAME))
                val configProto = IntegrityConfigParser(doc).parseConfig()
                storeProto(configProto, parameters.appIntegrityConfigProto.asFile.get())
            } ?: run {
                Files.deleteIfExists(parameters.appIntegrityConfigProto.asFile.get().toPath())
            }
        }
        private fun loadXML(xmlFile: File): Document {
            val documentFactory = DocumentBuilderFactory.newInstance()
            documentFactory.isNamespaceAware = true
            val documentBuilder = documentFactory.newDocumentBuilder()
            return documentBuilder.parse(xmlFile)
        }

        private fun storeProto(configProto: AppIntegrityConfig, output: File) {
            Files.newOutputStream(output.toPath()).use { outputStream ->
                configProto.writeTo(outputStream)
            }
        }

    }


    class CreationAction(creationConfig: VariantCreationConfig) :
        VariantTaskCreationAction<ParseIntegrityConfigTask, VariantCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("parse", "IntegrityConfig")

        override val type: Class<ParseIntegrityConfigTask>
            get() = ParseIntegrityConfigTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ParseIntegrityConfigTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ParseIntegrityConfigTask::appIntegrityConfigProto
            ).withName("AppIntegrityConfig.pb").on(InternalArtifactType.APP_INTEGRITY_CONFIG)
        }

        override fun configure(
            task: ParseIntegrityConfigTask
        ) {
            super.configure(task)
            task.integrityConfigDir.setDisallowChanges(creationConfig.global.bundleOptions.integrityConfigDir)
        }
    }
}


