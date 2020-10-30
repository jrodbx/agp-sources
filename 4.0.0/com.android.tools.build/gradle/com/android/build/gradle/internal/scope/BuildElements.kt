/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.scope

import com.android.build.api.artifact.ArtifactType
import com.android.build.VariantOutput
import com.android.ide.common.workers.WorkerExecutorException
import com.android.ide.common.workers.WorkerExecutorFacade
import com.google.common.collect.ImmutableList
import com.google.gson.GsonBuilder
import org.gradle.api.file.DirectoryProperty
import org.gradle.tooling.BuildException
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.stream.Stream

/**
 * Encapsulates the result of a task action. Therefore it is specific to a Task that generate
 * the same type of output for different splits.
 *
 * This can represent a past task action which result got loaded from a saved .json file or
 * the result of an executing of a task (or part of task) to save to a .json file.
 */
open class BuildElements(
    val version: Int = METADATA_FILE_VERSION,
    val applicationId: String,
    val variantType: String,
    val elements: Collection<BuildOutput>) : Iterable<BuildOutput> {

    companion object {
        const val METADATA_FILE_VERSION: Int = 1
    }

    override fun iterator(): Iterator<BuildOutput> = elements.iterator()

    fun element(apkData: ApkData): BuildOutput? {
        return elements.find {
            // TODO : simplify once ApkInfo can become a data class.
            it.apkData.type == apkData.type
                    && it.apkData.filters == apkData.filters
                    && it.apkData.fullName == apkData.fullName
        }
    }

    fun elementByType(type: VariantOutput.OutputType): BuildOutput? {
        return elements.find {
            it.apkData.type == type
        }
    }

    fun size(): Int = elements.size
    fun isEmpty(): Boolean = elements.isEmpty()
    fun stream(): Stream<BuildOutput> = elements.stream()

    /**
     * Register a runnable to use all current build elements and create new built elements.
     * The passed runnable will not be invoked until one of the [BuildElementActionScheduler] API
     * is used.
     *
     * @param workers used to submit transform tasks for each build element.
     * @param transformRunnableClass a runnable class that runs the transform
     * @param paramsFactory a factory that produces the transformRunnableClass parameters
     */
    open fun transform(
        workers: WorkerExecutorFacade,
        transformRunnableClass: Class<out BuildElementsTransformRunnable>,
        paramsFactory: (apkData: ApkData, input: File) -> BuildElementsTransformParams
    ): BuildElementActionScheduler {
        return WorkersBasedScheduler(this, transformRunnableClass, paramsFactory, workers)
    }

    /**
     * Persists the passed output types and split output to a [String] using gson.
     *
     * @param projectPath path to relativize output file paths against.
     * @return a json String.
     */
    fun persist(projectPath: Path): String {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(ApkData::class.java, ExistingBuildElements.ApkDataAdapter())
        gsonBuilder.registerTypeHierarchyAdapter(
            ArtifactType::class.java, ExistingBuildElements.OutputTypeTypeAdapter()
        )
        gsonBuilder.registerTypeAdapter(
            AnchorOutputType::class.java,
            ExistingBuildElements.OutputTypeTypeAdapter()
        )
        val gson = gsonBuilder.setPrettyPrinting().create()

        // flatten and relativize the file paths to be persisted.
        return gson.toJson(BuildElements(version, applicationId, variantType, elements
            .asSequence()
            .map { buildOutput ->
                BuildOutput(
                    buildOutput.type,
                    buildOutput.apkData,
                    projectPath.relativize(buildOutput.outputPath),
                    buildOutput.properties
                )
            }
            .toList()))
    }

    @Throws(IOException::class)
    fun save(folder: File): BuildElements {
        return saveToFile(ExistingBuildElements.getMetadataFile(folder))
    }

    @Throws(IOException::class)
    fun saveToFile(file: File): BuildElements {
        val persistedOutput = persist(file.parentFile.toPath())
        FileWriter(file).use {writer ->
            writer.append(persistedOutput)
        }
        return this
    }

    fun save(directoryProperty: DirectoryProperty): BuildElements =
        save(directoryProperty.get().asFile)


    @Deprecated(
        "", ReplaceWith(
            "WorkersBasedScheduler(input, transformRunnableClass, paramsFactory, workers",
            "com.android.build.gradle.internal.scope.BuildElements.WorkersBasedScheduler"
        )
    )

    internal data class ActionItem(val apkData: ApkData, val output: File?)

    private class WorkersBasedScheduler(
        val input: BuildElements,
        val transformRunnableClass: Class<out BuildElementsTransformRunnable>,
        val paramsFactory: (apkData: ApkData, input: File) -> BuildElementsTransformParams,
        val workers: WorkerExecutorFacade
    ) : BuildElementActionScheduler() {

        @Throws(BuildException::class)
        override fun into(type: ArtifactType<*>): BuildElements {
            return intoCallable(type).call()
        }

        @Throws(BuildException::class)
        override fun intoCallable(type: ArtifactType<*>): Callable<BuildElements> {
            return transform(type, transformRunnableClass, paramsFactory)
        }

        @Throws(BuildException::class)
        private fun transform(
            to: ArtifactType<*>,
            transformRunnableClass: Class<out BuildElementsTransformRunnable>,
            paramsFactory: (apkData: ApkData, input: File) -> BuildElementsTransformParams
        ): Callable<BuildElements> {
            val buildOutputs = ImmutableList.Builder<BuildOutput>()
            input.elements.forEach {
                val params = paramsFactory(it.apkData, it.outputFile)
                workers.submit(transformRunnableClass, params)
                if (params.output != null) {
                    buildOutputs.add(BuildOutput(to, it.apkData, params.output!!))
                }
            }

            return Callable {
                try {
                    workers.await()
                } catch (e: WorkerExecutorException) {
                    throw BuildException(e.message, e)
                }
                BuildElements(
                    input.version,
                    input.applicationId,
                    input.variantType,
                    buildOutputs.build())
            }
        }

    }
}