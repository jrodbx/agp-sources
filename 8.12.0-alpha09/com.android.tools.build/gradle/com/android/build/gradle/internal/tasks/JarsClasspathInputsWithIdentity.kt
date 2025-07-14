/*
 * Copyright (C) 2020 The Android Open Source Project
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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.work.ChangeType
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import java.io.File
import java.io.Serializable

/**
 * Class used to represent mapping from input jar files to identity representing those files. This
 * identity can be used to compute the output location for the jar. It deterministic across builds
 * and different machines. Also, this exposes [FileInfo] about every file which can be used to get
 * the file identity and also with [FileInfo.hasChanged] if a file has changed.
 *
 * Identity assigned to input is ordinal in the input collection. Also, all files will be reported
 * as changed if any of the files is reported as added or removed i.e. only if files are modified
 * we can process them incrementally.
 */
abstract class JarsClasspathInputsWithIdentity {

    @get:Incremental
    @get:Classpath
    abstract val inputJars: ConfigurableFileCollection

    @Transient
    private var mappingStateCached: JarsIdentityMapping? = null

    @Synchronized
    fun getMappingState(taskInputChanges: InputChanges): JarsIdentityMapping {
        return mappingStateCached?: initMappingState(taskInputChanges).also {
            mappingStateCached = it
        }
    }

    private fun initMappingState(taskInputChanges: InputChanges): JarsIdentityMapping {
        val (changed, addedOrRemoved) = taskInputChanges.getFileChanges(inputJars)
            .partition { it.changeType == ChangeType.MODIFIED }

        val reprocessAll = !taskInputChanges.isIncremental || addedOrRemoved.isNotEmpty()
        val changedFiles = changed.map { it.file }.toSet()
        val hasChanged = { file: File -> reprocessAll || (file in changedFiles) }
        var i = 0
        val mapping = inputJars.files.associateWith {
            FileInfo((i++).toString(), hasChanged(it))
        }

        return JarsIdentityMapping(mapping, reprocessAll)
    }
}

class JarsIdentityMapping(val jarsInfo: Map<File, FileInfo>, val reprocessAll: Boolean) :
    Serializable

/**
 * Contains file identity for this run, and info if the file changed in the current run.
 */
data class FileInfo(val identity: String, val hasChanged: Boolean): Serializable
