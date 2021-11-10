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

package com.android.build.gradle.internal.tasks.databinding

import android.databinding.tool.DataBindingBuilder
import android.databinding.tool.util.FileUtil
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.ide.common.resources.FileStatus
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.IOFileFilter
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.workers.WorkerExecutor
import java.io.File

private fun isClassListFile(listFile: String) =
    listFile.endsWith(DataBindingBuilder.BINDING_CLASS_LIST_SUFFIX)

open class DataBindingMergeBaseClassLogDelegate(
    private val initiator: AndroidVariantTask,
    private val moduleClassLog: FileCollection,
    private val externalClassLog: FileCollection,
    private val outFolder: Provider<Directory>
) {

    fun doIncrementalRun(
        workers: WorkerExecutor,
        changedInputs: Map<File, FileStatus>) {
        changedInputs.forEach { changedInput ->
            submit(workers, changedInput.key, changedInput.value)
        }
    }

    fun doFullRun(workers: WorkerExecutor) {
        com.android.utils.FileUtils.cleanOutputDir(outFolder.get().asFile)

        moduleClassLog
            .union(externalClassLog)
            .filter { it.exists() }
            .forEach { folder ->
                FileUtil.listAndSortFiles(
                    folder,
                    object : IOFileFilter {
                        override fun accept(file: File): Boolean {
                            return isClassListFile(file.name)
                        }

                        override fun accept(dir: File, name: String): Boolean {
                            return isClassListFile(name)
                        }
                    }
                ).forEach { file ->
                    submit(workers, file, FileStatus.NEW)
                }
            }
    }

    open fun submit(workers: WorkerExecutor, file: File, status: FileStatus) {
        workers.noIsolation().submit(DataBindingMergeBaseClassLogRunnable::class.java) {
            it.initializeFromAndroidVariantTask(initiator)
            it.file.set(file)
            it.outFolder.set(outFolder)
            it.status.set(status)
        }
    }
}

abstract class DataBindingMergeBaseClassLogRunnable
    : ProfileAwareWorkAction<DataBindingMergeBaseClassLogRunnable.Params>() {

    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val file: RegularFileProperty
        abstract val outFolder: DirectoryProperty
        abstract val status: Property<FileStatus>
    }

    override fun run() {
        if (isClassListFile(parameters.file.get().asFile.name)) {
            val outFile = File(parameters.outFolder.get().asFile, parameters.file.get().asFile.name)
            when (parameters.status.get()) {
                FileStatus.NEW, FileStatus.CHANGED ->
                    FileUtils.copyFile(parameters.file.get().asFile, outFile)

                FileStatus.REMOVED -> {
                    if (outFile.exists()) {
                        FileUtils.forceDelete(outFile)
                    }
                }
            }
        }
    }
}