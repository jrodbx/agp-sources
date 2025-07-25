/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.bundle.DeviceGroupConfig
import com.google.common.annotations.VisibleForTesting
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.w3c.dom.Document
import java.io.File
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Task that parses a DeviceGroupConfig proto from an XML file.
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.METADATA)
abstract class ParseDeviceTargetingConfigTask : NonIncrementalTask() {

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val deviceTargetingConfigXml: RegularFileProperty

    @get:OutputFile
    abstract val deviceTargetingConfigProto: RegularFileProperty

    public override fun doTaskAction() {
        workerExecutor.noIsolation().submit(ParseDeviceTargetingConfigRunnable::class.java) {
            it.initializeFromBaseTask(this)
            it.deviceTargetingConfigXml.set(deviceTargetingConfigXml)
            it.deviceTargetingConfigProto.set(deviceTargetingConfigProto)
        }
    }

    @VisibleForTesting
    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val deviceTargetingConfigXml: RegularFileProperty
        abstract val deviceTargetingConfigProto: RegularFileProperty
    }

    @VisibleForTesting
    abstract class ParseDeviceTargetingConfigRunnable  : ProfileAwareWorkAction<Params>() {
        override fun run() {
            parameters.deviceTargetingConfigXml.asFile.orNull?.let {
                val doc = loadXML(it)
                val configProto = DeviceTargetingConfigParser(doc).parseConfig()
                storeProto(configProto, parameters.deviceTargetingConfigProto.asFile.get())
            } ?: run {
                Files.deleteIfExists(parameters.deviceTargetingConfigProto.asFile.get().toPath())
            }
        }
        private fun loadXML(xmlFile: File): Document {
            val documentFactory = DocumentBuilderFactory.newInstance()
            documentFactory.isNamespaceAware = true
            val documentBuilder = documentFactory.newDocumentBuilder()
            return documentBuilder.parse(xmlFile)
        }

        private fun storeProto(configProto: DeviceGroupConfig, output: File) {
            Files.newOutputStream(output.toPath()).use { outputStream ->
                configProto.writeTo(outputStream)
            }
        }

    }


    class CreationAction(creationConfig: VariantCreationConfig) :
        VariantTaskCreationAction<ParseDeviceTargetingConfigTask, VariantCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = computeTaskName("parse", "DeviceTargetingConfig")

        override val type: Class<ParseDeviceTargetingConfigTask>
            get() = ParseDeviceTargetingConfigTask::class.java

        override fun handleProvider(
            taskProvider: TaskProvider<ParseDeviceTargetingConfigTask>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ParseDeviceTargetingConfigTask::deviceTargetingConfigProto
            ).withName("DeviceTargetingConfig.pb").on(InternalArtifactType.DEVICE_TARGETING_CONFIG)
        }

        override fun configure(
            task: ParseDeviceTargetingConfigTask
        ) {
            super.configure(task)
            task.deviceTargetingConfigXml.setDisallowChanges(creationConfig.global.bundleOptions.deviceTargetingConfig)
        }
    }
}


