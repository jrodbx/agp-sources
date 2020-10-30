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

import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.google.common.annotations.VisibleForTesting
import com.android.bundle.AppIntegrityConfigOuterClass.AppIntegrityConfig
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
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
import java.io.Serializable
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import javax.inject.Inject

private const val CONFIG_FILE_NAME = "IntegrityConfig.xml"

/**
 * Task that parses app_integrity_config.proto from an XML file.
 */
@CacheableTask
abstract class ParseIntegrityConfigTask : NonIncrementalTask() {

    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val integrityConfigDir: DirectoryProperty

    @get:OutputFile
    abstract val appIntegrityConfigProto: RegularFileProperty

    public override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                ParseIntegrityConfigRunnable::class.java,
                Params(
                    integrityConfigDir.orNull?.asFile,
                    appIntegrityConfigProto.asFile.get()
                )
            )
        }
    }

    @VisibleForTesting
    data class Params(
        val integrityConfigDir: File?,
        val appIntegrityConfigProto: File
    ) : Serializable

    @VisibleForTesting
    class ParseIntegrityConfigRunnable @Inject constructor(private val params: Params) :
        Runnable {
        override fun run() {
            params.integrityConfigDir?.let {
                if (!it.isDirectory) {
                    throw FileNotFoundException("Could not find directory ${it.absolutePath}")
                }
                val doc = loadXML(it.resolve(CONFIG_FILE_NAME))
                val configProto = IntegrityConfigParser(doc).parseConfig()
                storeProto(configProto, params.appIntegrityConfigProto)
            } ?: run {
                Files.deleteIfExists(params.appIntegrityConfigProto.toPath())
            }
        }
        private fun loadXML(xmlFile: File): Document {
            val documentFactory = DocumentBuilderFactory.newInstance()
            val documentBuilder = documentFactory.newDocumentBuilder()
            return documentBuilder.parse(xmlFile)
        }

        private fun storeProto(configProto: AppIntegrityConfig, output: File) {
            Files.newOutputStream(output.toPath()).use { outputStream ->
                configProto.writeTo(outputStream)
            }
        }

    }


    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<ParseIntegrityConfigTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("parse", "IntegrityConfig")

        override val type: Class<ParseIntegrityConfigTask>
            get() = ParseIntegrityConfigTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out ParseIntegrityConfigTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.APP_INTEGRITY_CONFIG,
                taskProvider,
                ParseIntegrityConfigTask::appIntegrityConfigProto,
                "AppIntegrityConfig.pb"
            )
        }

        override fun configure(task: ParseIntegrityConfigTask) {
            super.configure(task)
            task.integrityConfigDir.set(getIntegrityConfigFolder())
        }

        private fun getIntegrityConfigFolder(): Provider<out Directory> =
            (variantScope.globalScope.extension as BaseAppModuleExtension).bundle.integrityConfigDir
    }
}


