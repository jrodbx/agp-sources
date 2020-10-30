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
import com.android.ide.common.resources.FileStatus
import com.android.ide.common.workers.WorkerExecutorFacade
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.IOFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import java.io.File
import java.io.Serializable
import javax.inject.Inject

private fun isClassListFile(listFile: String) =
    listFile.endsWith(DataBindingBuilder.BINDING_CLASS_LIST_SUFFIX)

class DataBindingMergeBaseClassLogDelegate(
    private val moduleClassLog: FileCollection,
    private val externalClassLog: FileCollection,
    private val outFolder: Provider<Directory>
) {

    fun doIncrementalRun(
        workers: WorkerExecutorFacade,
        changedInputs: Map<File, FileStatus>) {
        workers.use { facade ->
            changedInputs.forEach {
                facade.submit(
                    DataBindingMergeBaseClassLogRunnable::class.java,
                    DataBindingMergeBaseClassLogRunnable.Params(it.key, outFolder.get().asFile, it.value)
                )
            }
        }
    }

    fun doFullRun(workers: WorkerExecutorFacade) {
        com.android.utils.FileUtils.cleanOutputDir(outFolder.get().asFile)

        workers.use { facade ->
            moduleClassLog
                .union(externalClassLog)
                .filter { it.exists() }
                .forEach { folder ->
                    FileUtils.listFiles(
                        folder,
                        object : IOFileFilter {
                            override fun accept(file: File): Boolean {
                                return isClassListFile(file.name)
                            }

                            override fun accept(dir: File, name: String): Boolean {
                                return isClassListFile(name)
                            }
                        },
                        TrueFileFilter.INSTANCE
                    ).forEach {
                        facade.submit(
                            DataBindingMergeBaseClassLogRunnable::class.java,
                            DataBindingMergeBaseClassLogRunnable.Params(it, outFolder.get().asFile, FileStatus.NEW)
                        )
                    }
                }
        }
    }
}

class DataBindingMergeBaseClassLogRunnable @Inject constructor(private val params: Params) : Runnable {

    data class Params(val file: File, val outFolder: File, val status: FileStatus) : Serializable

    override fun run() {
        if (isClassListFile(params.file.name)) {
            when (params.status) {
                FileStatus.NEW, FileStatus.CHANGED ->
                    FileUtils.copyFile(params.file, File(params.outFolder, params.file.name))

                FileStatus.REMOVED -> {
                    val outFile = File(params.outFolder, params.file.name)
                    if (outFile.exists()) {
                        FileUtils.forceDelete(outFile)
                    }
                }
            }
        }
    }
}