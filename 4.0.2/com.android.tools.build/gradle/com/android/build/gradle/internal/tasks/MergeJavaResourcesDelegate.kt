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

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.packaging.PackagingFileAction
import com.android.build.gradle.internal.packaging.ParsedPackagingOptions
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.builder.merge.DelegateIncrementalFileMergerOutput
import com.android.builder.merge.FilterIncrementalFileMergerInput
import com.android.builder.merge.IncrementalFileMerger
import com.android.builder.merge.IncrementalFileMergerInput
import com.android.builder.merge.IncrementalFileMergerOutputs
import com.android.builder.merge.IncrementalFileMergerState
import com.android.builder.merge.MergeOutputWriters
import com.android.builder.merge.RenameIncrementalFileMergerInput
import com.android.builder.merge.StreamMergeAlgorithms
import com.android.builder.packaging.PackagingUtils
import com.android.utils.FileUtils
import com.google.common.collect.ImmutableList
import org.gradle.api.logging.Logging
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.function.Predicate
import java.util.regex.Pattern

private val JAR_ABI_PATTERN = Pattern.compile("lib/([^/]+)/[^/]+")

private fun containsHighPriorityScope(scopes: MutableSet<in Scope>): Boolean {
    return scopes.stream()
        .anyMatch { scope -> scope == Scope.PROJECT || scope == InternalScope.FEATURES }
}

/**
 * A delegate which actually does the merging of java resources, for example for the
 * [MergeJavaResourceTask]
 */
class MergeJavaResourcesDelegate(
    inputs: List<IncrementalFileMergerInput>,
    private val outputLocation: File,
    private val contentMap: MutableMap<IncrementalFileMergerInput, QualifiedContent>,
    private val packagingOptions: ParsedPackagingOptions,
    private val mergedType: ContentType,
    private val incrementalStateFile: File,
    private val isIncremental: Boolean,
    private val noCompress: Collection<String>
) {

    private var inputs: MutableList<IncrementalFileMergerInput>
    private val acceptedPathsPredicate: Predicate<String>

    init {
        this.inputs = inputs.toMutableList()
        this.acceptedPathsPredicate = when(mergedType) {
            QualifiedContent.DefaultContentType.RESOURCES -> MergeJavaResourceTask.predicate
            ExtendedContentType.NATIVE_LIBS ->
                Predicate { path ->
                    val m = JAR_ABI_PATTERN.matcher(path)
                    // if the ABI is accepted, check the 3rd segment
                    if (m.matches()) {
                        // remove the beginning of the path (lib/<abi>/)
                        val filename = path.substring(5 + m.group(1).length)
                        // and check the filename
                        return@Predicate MergeNativeLibsTask.predicate.test(filename)
                    }
                    return@Predicate false
                }
            else ->
                throw UnsupportedOperationException(
                    "mergedType param must be RESOURCES or NATIVE_LIBS"
                )
        }
    }

    /**
     * Returns the incremental state.
     *
     * @throws IOException if fails to load the incremental state
     */
    private fun loadMergeState(): IncrementalFileMergerState {
        if (!incrementalStateFile.isFile || !isIncremental) {
            return IncrementalFileMergerState()
        }
        try {
            ObjectInputStream(FileInputStream(incrementalStateFile)).use {
                return it.readObject() as IncrementalFileMergerState
            }
        } catch (e: ClassNotFoundException) {
            throw IOException(e)
        }
    }

    /**
     * Save the incremental merge state.
     *
     * @param state the incremental file merger state
     * @throws IOException if fails to save the state
     */
    private fun saveMergeState(state: IncrementalFileMergerState) {
        FileUtils.mkdirs(incrementalStateFile.parentFile)
        ObjectOutputStream(FileOutputStream(incrementalStateFile)).use { it.writeObject(state) }
    }

    fun run() {

        /*
         * In an ideal world, we could just send the inputs to the file merger. However, in the
         * real world, things are more complicated :)
         *
         * We need to:
         *
         * 1. Bring inputs that refer to the project scope before the other inputs.
         * 2. Prefix libraries that come from directories with "lib/".
         * 3. Filter all inputs to remove anything not accepted by acceptedPathsPredicate or
         * by packagingOptions.
         */

        // Sort inputs to move project scopes to the start.
        inputs.sortBy { if (contentMap[it]?.scopes?.contains(Scope.PROJECT) == true) 0 else 1 }

        // Prefix libraries with "lib/" if we're doing native libraries.
        if (mergedType == ExtendedContentType.NATIVE_LIBS) {
            inputs =
                    inputs.map {
                        val qc = contentMap[it]
                        if (qc?.file?.isDirectory == true) {
                            val renamedInput = RenameIncrementalFileMergerInput(
                                it,
                                { s -> "lib/$s" },
                                { s -> s.substring("lib/".length) })
                            contentMap[renamedInput] = qc
                            return@map renamedInput
                        } else {
                            return@map it
                        }
                    }.toMutableList()
        }

        // Filter inputs.
        val inputFilter =
            acceptedPathsPredicate.and { path -> packagingOptions.getAction(path) != PackagingFileAction.EXCLUDE }
        inputs =
                inputs.map {
                    val filteredInput = FilterIncrementalFileMergerInput(it, inputFilter)
                    contentMap[filteredInput] = contentMap[it]!!
                    filteredInput
                }.toMutableList()

        /*
         * Create the algorithm used by the merge transform. This algorithm decides on which
         * algorithm to delegate to depending on the packaging option of the path. By default it
         * requires just one file (no merging).
         */
        val mergeTransformAlgorithm = StreamMergeAlgorithms.select { path ->
            val packagingAction = packagingOptions.getAction(path)
            when (packagingAction) {
                PackagingFileAction.EXCLUDE ->
                    // Should have been excluded from the input.
                    throw AssertionError()
                PackagingFileAction.PICK_FIRST -> return@select StreamMergeAlgorithms.pickFirst()
                PackagingFileAction.MERGE -> return@select StreamMergeAlgorithms.concat()
                PackagingFileAction.NONE -> return@select StreamMergeAlgorithms.acceptOnlyOne()
                else -> throw AssertionError()
            }
        }

        /*
         * Create an output that uses the algorithm. This is not the final output because,
         * unfortunately, we still have the complexity of the project scope overriding other scopes
         * to solve.
         *
         * When resources inside a jar file are extracted to a directory, the results may not be
         * expected on Windows if the file names end with "." (bug 65337573), or if there is an
         * uppercase/lowercase conflict. To work around this issue, we copy these resources to a
         * jar file.
         */
        val baseOutput = if (mergedType == QualifiedContent.DefaultContentType.RESOURCES) {
            IncrementalFileMergerOutputs.fromAlgorithmAndWriter(
                mergeTransformAlgorithm, MergeOutputWriters.toZip(outputLocation)
            )
        } else {
            IncrementalFileMergerOutputs.fromAlgorithmAndWriter(
                mergeTransformAlgorithm, MergeOutputWriters.toDirectory(outputLocation)
            )
        }

        /*
         * We need a custom output to handle the case in which the same path appears in multiple
         * inputs and the action is NONE, but only one input is actually PROJECT or FEATURES. In
         * this specific case we will ignore all other inputs.
         */
        val highPriorityInputs =
            contentMap
                .keys
                .filter { input ->
                    containsHighPriorityScope(
                        contentMap[input]?.scopes ?: TransformManager.EMPTY_SCOPES
                    )
                }

        val output = object : DelegateIncrementalFileMergerOutput(baseOutput) {
            override fun create(
                path: String,
                inputs: List<IncrementalFileMergerInput>,
                compress: Boolean
            ) {
                super.create(path, filter(path, inputs), compress)
            }

            override fun update(
                path: String,
                prevInputNames: List<String>,
                inputs: List<IncrementalFileMergerInput>,
                compress: Boolean
            ) {
                super.update(path, prevInputNames, filter(path, inputs), compress)
            }

            private fun filter(
                path: String,
                inputs: List<IncrementalFileMergerInput>
            ): ImmutableList<IncrementalFileMergerInput> {
                val packagingAction = packagingOptions.getAction(path)
                val shouldFilterInputs =
                    packagingAction == PackagingFileAction.NONE &&
                            inputs.any { highPriorityInputs.contains(it) }
                return if (shouldFilterInputs) {
                    // Warn if filtering out "low priority inputs" resolves collisions. Future
                    // AGP versions will not do this filtering and will result in an error instead.
                    // See Issue 141758241.
                    val filteredInputs =
                        ImmutableList.copyOf(inputs.filter { highPriorityInputs.contains(it) })
                    if (filteredInputs.size < inputs.size) {
                        val logger =
                            LoggerWrapper(Logging.getLogger(MergeJavaResourcesDelegate::class.java))
                        logger.warning(
                            "More than one file was found with OS independent path '$path'. "
                                    + "This version of the Android Gradle Plugin chooses the file "
                                    + "from the app or dynamic-feature module, but this can cause "
                                    + "unexpected behavior or errors at runtime. Future versions "
                                    + "of the Android Gradle Plugin will throw an error in this "
                                    + "case."
                        )
                    }
                    filteredInputs
                } else {
                    ImmutableList.copyOf(inputs)
                }
            }
        }

        saveMergeState(
            IncrementalFileMerger.merge(
                inputs.toList(),
                output,
                loadMergeState(),
                PackagingUtils.getNoCompressPredicateForExtensions(noCompress)
            )
        )
    }
}