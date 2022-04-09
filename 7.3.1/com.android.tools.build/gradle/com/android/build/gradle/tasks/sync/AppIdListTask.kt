/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.tasks.sync

import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.BaseTask
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationAction
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.ide.common.build.filebasedproperties.module.AppIdListSync
import com.android.ide.common.build.filebasedproperties.module.AppIdSync
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.BufferedOutputStream
import java.io.FileOutputStream

/**
 * Task to write the list of application ids for all variants of this module.
 */
@DisableCachingByDefault
abstract class AppIdListTask: BaseTask() {

    @get:OutputFile
    abstract val outputModelFile: RegularFileProperty

    @get:Nested
    abstract val variantsApplicationId: ListProperty<VariantInformation>

    abstract class VariantInformation {
        @get:Input
        abstract val variantName: Property<String>

        @get:Input
        abstract val applicationId: Property<String>
    }

    @TaskAction
    fun doTaskAction() {
        val listOfAppIds = AppIdListSync.newBuilder()
        variantsApplicationId.get().forEach { variantInformation ->
            listOfAppIds.addAppIds(
                AppIdSync.newBuilder().also {
                    it.name = variantInformation.variantName.get()
                    it.applicationId = variantInformation.applicationId.get()
                }.build()
            )
        }
        BufferedOutputStream(FileOutputStream(outputModelFile.asFile.get())).use {
            listOfAppIds.build().writeTo(it)
        }
    }

    companion object {
        fun getTaskName() = "appIdListTask"

        const val FN_APP_ID_LIST = "app_id_list.pb"
    }

    class CreationAction(
        private val config: GlobalTaskCreationConfig,
        private val applicationIds: Map<String, Provider<String>>,
        ) : GlobalTaskCreationAction<AppIdListTask>(config) {

        override val name: String = getTaskName()

        override val type: Class<AppIdListTask> = AppIdListTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<AppIdListTask>) {
            config.globalArtifacts
                .setInitialProvider(taskProvider, AppIdListTask::outputModelFile)
                .withName(FN_APP_ID_LIST)
                .on(InternalArtifactType.APP_ID_LIST_MODEL)
        }

        override fun configure(task: AppIdListTask) {
            super.configure(task)
            applicationIds.forEach { (variantName, applicationId) ->
                task.variantsApplicationId.add(
                    config.services.newInstance(VariantInformation::class.java).also {
                        it.variantName.setDisallowChanges(variantName)
                        it.applicationId.setDisallowChanges(applicationId)
                    }
                )
            }
            task.variantsApplicationId.disallowChanges()
        }
    }
}
