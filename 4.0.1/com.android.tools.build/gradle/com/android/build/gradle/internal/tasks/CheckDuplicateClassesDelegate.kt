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

import com.android.SdkConstants
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry
import com.android.build.gradle.internal.workeractions.WorkerActionServiceRegistry.ServiceKey
import com.android.builder.dexing.ClassFileInput
import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.common.collect.Maps
import org.gradle.api.artifacts.ArtifactCollection
import java.io.File
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.streams.toList

// Shared state used by worker actions.
private val sharedState = WorkerActionServiceRegistry()

/**
 * A class that checks for duplicate classes within an ArtifactCollection. Classes are assumed to be
 * duplicate if they have the same name and they are positioned within the same package (this is
 * possible if they are in different artifacts).
 */
class CheckDuplicateClassesDelegate(private val classesArtifacts: ArtifactCollection) {

    class ArtifactClassesMap : ConcurrentHashMap<String, List<String>>()

    data class ArtifactClassesKey(private val name: String) : ServiceKey<ArtifactClassesMap> {
        override val type = ArtifactClassesMap::class.java
    }

    fun run(workers: WorkerExecutorFacade) {
        val artifactClasses = ArtifactClassesMap()
        val artifactClassesKey = ArtifactClassesKey("artifactClasses${hashCode()}")

        sharedState.registerServiceAsCloseable(artifactClassesKey, artifactClasses).use {
            workers.use { facade ->
                classesArtifacts.artifacts.forEach {
                    facade.submit(
                        ExtractClassesRunnable::class.java,
                        ExtractClassesParams(it.id.displayName, it.file, artifactClassesKey)
                    )
                }

                facade.await()

                facade.submit(
                    CheckDuplicatesRunnable::class.java,
                    CheckDuplicatesParams(artifactClassesKey)
                )

                facade.await()
            }
        }
    }
}

private data class ExtractClassesParams(
    val artifactName: String,
    val artifactFile: File,
    val serviceKey: ServiceKey<CheckDuplicateClassesDelegate.ArtifactClassesMap>
) : Serializable

private class ExtractClassesRunnable @Inject constructor(
    private val params: ExtractClassesParams) : Runnable {

    override fun run() {
        val map = sharedState.getService(params.serviceKey).service
        map[params.artifactName] = extractClasses(params.artifactFile)
    }
}

private data class CheckDuplicatesParams(
    val serviceKey: ServiceKey<CheckDuplicateClassesDelegate.ArtifactClassesMap>): Serializable

private const val RECOMMENDATION =
    "Go to the documentation to learn how to <a href=\"d.android.com/r/tools/classpath-sync-errors\">Fix dependency resolution errors</a>."

private fun duplicateClassMessage(className: String, artifactNames: List<String>): String {
    val sorted = artifactNames.sorted()
    val modules = when {
        artifactNames.size == 2 -> "modules ${sorted[0]} and ${sorted[1]}"
        else -> {
            val last = sorted.last()
            "the following modules: ${sorted.dropLast(1).joinToString(", ")} and $last"
        }
    }
    return "Duplicate class $className found in $modules"
}

private class CheckDuplicatesRunnable @Inject constructor(
    private val params: CheckDuplicatesParams) : Runnable {

    override fun run() {

        val map = sharedState.getService(params.serviceKey).service
        val maxSize = map.map { it.value.size }.sum()
        val classes = Maps.newHashMapWithExpectedSize<String, MutableList<String>>(maxSize)

        map.forEach {
            val artifactName = it.key
            it.value.forEach { className ->
                classes.getOrPut(className) { mutableListOf() }.add(artifactName)
            }
        }

        val duplicatesMap = classes.filter { it.value.size > 1 }.toSortedMap()
        if (!duplicatesMap.isEmpty()) {
            val lineSeparator = System.lineSeparator()
            val duplicateMessages = duplicatesMap
                .map { duplicateClassMessage(it.key, it.value) }
                .joinToString(lineSeparator)
            throw RuntimeException("$duplicateMessages$lineSeparator$lineSeparator$RECOMMENDATION")
        }
    }
}

private fun extractClasses(jarFile: File): List<String> = ZipFile(jarFile).use {
    return it.stream()
    .filter { ClassFileInput.CLASS_MATCHER.test(it.name) }
    .map { it.name.replace('/', '.').dropLast(SdkConstants.DOT_CLASS.length) }
    .toList()
}
