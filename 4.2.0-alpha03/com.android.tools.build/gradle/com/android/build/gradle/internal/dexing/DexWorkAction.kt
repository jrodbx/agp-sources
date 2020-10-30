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

package com.android.build.gradle.internal.dexing

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.tasks.DexArchiveBuilderTaskDelegate
import com.android.builder.dexing.ClassBucket
import com.android.builder.dexing.DependencyGraphUpdater
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexArchiveBuilderConfig
import com.android.builder.dexing.DexArchiveBuilderException
import com.android.builder.dexing.DexerTool
import com.android.builder.dexing.MutableDependencyGraph
import com.android.dx.command.dexer.DxContext
import com.android.ide.common.blame.MessageReceiver
import com.android.utils.FileUtils
import com.google.common.io.Closer
import org.gradle.api.logging.Logging
import org.gradle.tooling.BuildException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable
import javax.inject.Inject

/** Work action to process a bucket of class files. */
class DexWorkAction @Inject constructor(private val params: DexWorkActionParams) : Runnable {

    override fun run() {
        try {
            launchProcessing(
                params,
                System.out,
                System.err,
                MessageReceiverImpl(
                    params.dexSpec.dexParams.errorFormatMode,
                    Logging.getLogger(DexArchiveBuilderTaskDelegate::class.java)
                )
            )
        } catch (e: Exception) {
            throw BuildException(e.message, e)
        }
    }
}

/** Parameters for running [DexWorkAction]. */
class DexWorkActionParams(
    val dexer: DexerTool,
    val dexSpec: IncrementalDexSpec,
    val dxDexParams: DxDexParameters
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

fun launchProcessing(
    dexWorkActionParams: DexWorkActionParams,
    outStream: OutputStream,
    errStream: OutputStream,
    receiver: MessageReceiver
) {
    val dexArchiveBuilder = getDexArchiveBuilder(
        dexWorkActionParams,
        outStream,
        errStream,
        receiver
    )
    if (dexWorkActionParams.dexSpec.isIncremental) {
        processIncrementally(dexArchiveBuilder, dexWorkActionParams)
    } else {
        processNonIncrementally(dexArchiveBuilder, dexWorkActionParams)
    }
}

private fun processIncrementally(
    dexArchiveBuilder: DexArchiveBuilder,
    dexWorkActionParams: DexWorkActionParams
) {
    with(dexWorkActionParams.dexSpec) {
        val desugarGraph = desugarGraphFile?.let {
            try {
                readDesugarGraph(desugarGraphFile)
            } catch (e: Exception) {
                loggerWrapper.warning(
                    "Failed to read desugaring graph." +
                            " Cause: ${e.javaClass.simpleName}, message: ${e.message}.\n" +
                            "Fall back to non-incremental mode."
                )
                processNonIncrementally(dexArchiveBuilder, dexWorkActionParams)
                return@processIncrementally
            }
        }

        // Compute impacted files based on the changed files and the desugaring graph (if they are
        // not precomputed)
        val unchangedButImpactedFiles =
            impactedFiles ?: desugarGraph!!.getAllDependents(changedFiles)
        val changedOrImpactedFiles = changedFiles + unchangedButImpactedFiles

        // Remove stale nodes in the desugaring graph (stale dex outputs have been removed earlier
        // before the workers are launched)
        desugarGraph?.let { graph ->
            // Note that the `changedOrImpactedFiles` set may contain added files, which should not
            // exist in the graph and will be ignored.
            changedOrImpactedFiles.forEach { graph.removeNode(it) }
        }

        // Process only input files that are modified, added, or unchanged-but-impacted
        val filter: (File, String) -> Boolean = { rootPath: File, relativePath: String ->
            // Note that the `changedOrImpactedFiles` set may contain removed files, but those files
            // will not not be selected as candidates in the process() method and therefore will not
            // make it to this filter.
            rootPath in changedOrImpactedFiles /* for jars (we don't track class files in jars) */ ||
                    rootPath.resolve(relativePath) in changedOrImpactedFiles /* for class files in dirs */
        }
        process(
            dexArchiveBuilder = dexArchiveBuilder,
            inputClassFiles = dexWorkActionParams.dexSpec.inputClassFiles,
            inputFilter = filter,
            outputPath = dexWorkActionParams.dexSpec.outputPath,
            desugarGraphUpdater = desugarGraph
        )

        // Store the desugaring graph for use in the next build. If dexing failed earlier, it is
        // intended that we will not store the graph as the graph is only meant to contain info
        // about a previous successful build.
        desugarGraphFile?.let {
            writeDesugarGraph(it, desugarGraph!!)
        }
    }
}

private fun processNonIncrementally(
    dexArchiveBuilder: DexArchiveBuilder,
    dexWorkActionParams: DexWorkActionParams
) {
    // Dex outputs have been removed earlier before the workers are launched)

    with(dexWorkActionParams.dexSpec) {
        val desugarGraph = desugarGraphFile?.let {
            MutableDependencyGraph<File>()
        }

        process(
            dexArchiveBuilder = dexArchiveBuilder,
            inputClassFiles = dexWorkActionParams.dexSpec.inputClassFiles,
            inputFilter = { _, _ -> true },
            outputPath = dexWorkActionParams.dexSpec.outputPath,
            desugarGraphUpdater = desugarGraph
        )

        // Store the desugaring graph for use in the next build. If dexing failed earlier, it is
        // intended that we will not store the graph as the graph is only meant to contain info
        // about a previous successful build.
        desugarGraphFile?.let {
            FileUtils.mkdirs(it.parentFile)
            writeDesugarGraph(it, desugarGraph!!)
        }
    }
}

private fun process(
    dexArchiveBuilder: DexArchiveBuilder,
    inputClassFiles: ClassBucket,
    inputFilter: (File, String) -> Boolean,
    outputPath: File,
    desugarGraphUpdater: DependencyGraphUpdater<File>?
) {
    val inputRoots = inputClassFiles.bucketGroup.getRoots()
    inputRoots.forEach { loggerWrapper.verbose("Dexing '${it.path}' to '${outputPath.path}'") }
    try {
        Closer.create().use { closer ->
            inputClassFiles.getClassFiles(filter = inputFilter, closer = closer).use {
                dexArchiveBuilder.convert(it, outputPath.toPath(), desugarGraphUpdater)
            }
        }
    } catch (ex: DexArchiveBuilderException) {
        throw DexArchiveBuilderException(
            "Failed to process: ${inputRoots.joinToString(", ") { it.path }}",
            ex
        )
    }
}

private fun getDexArchiveBuilder(
    dexWorkActionParams: DexWorkActionParams,
    outStream: OutputStream,
    errStream: OutputStream,
    messageReceiver: MessageReceiver
): DexArchiveBuilder {
    val dexArchiveBuilder: DexArchiveBuilder
    with(dexWorkActionParams) {
        when (dexer) {
            DexerTool.DX -> {
                val config = DexArchiveBuilderConfig(
                    DxContext(outStream, errStream),
                    !dxDexParams.dxNoOptimizeFlagPresent, // optimizedDex
                    dxDexParams.inBufferSize,
                    dexSpec.dexParams.minSdkVersion,
                    DexerTool.DX,
                    dxDexParams.outBufferSize,
                    dxDexParams.jumboMode
                )

                dexArchiveBuilder = DexArchiveBuilder.createDxDexBuilder(config)
            }
            DexerTool.D8 -> dexArchiveBuilder = DexArchiveBuilder.createD8DexBuilder(
                com.android.builder.dexing.DexParameters(
                    minSdkVersion = dexSpec.dexParams.minSdkVersion,
                    debuggable = dexSpec.dexParams.debuggable,
                    dexPerClass = dexSpec.dexParams.dexPerClass,
                    withDesugaring = dexSpec.dexParams.withDesugaring,
                    desugarBootclasspath =
                    DexArchiveBuilderTaskDelegate.sharedState.getService(dexSpec.dexParams.desugarBootclasspath).service,
                    desugarClasspath =
                    DexArchiveBuilderTaskDelegate.sharedState.getService(dexSpec.dexParams.desugarClasspath).service,
                    coreLibDesugarConfig = dexSpec.dexParams.coreLibDesugarConfig,
                    coreLibDesugarOutputKeepRuleFile =
                    dexSpec.dexParams.coreLibDesugarOutputKeepRuleFile,
                    messageReceiver = messageReceiver
                )
            )
            else -> throw AssertionError("Unknown dexer type: " + dexer.name)
        }
    }
    return dexArchiveBuilder
}

fun readDesugarGraph(desugarGraphFile: File): MutableDependencyGraph<File> {
    return ObjectInputStream(FileInputStream(desugarGraphFile).buffered()).use {
        @Suppress("UNCHECKED_CAST")
        it.readObject() as MutableDependencyGraph<File>
    }
}

fun writeDesugarGraph(desugarGraphFile: File, desugarGraph: MutableDependencyGraph<File>) {
    ObjectOutputStream(FileOutputStream(desugarGraphFile).buffered()).use {
        it.writeObject(desugarGraph)
    }
}

private val loggerWrapper = LoggerWrapper.getLogger(DexWorkAction::class.java)