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

@file:JvmName("FeatureSplitUtils")

package com.android.build.gradle.internal.tasks.featuresplit

import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.options.IntegerOption
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.tooling.BuildException
import java.io.File
import java.io.FileNotFoundException
import java.io.Serializable
import java.util.regex.Pattern
import javax.inject.Inject

/** Task to write the FeatureSetMetadata file.  */
@CacheableTask
abstract class FeatureSetMetadataWriterTask : NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var inputFiles: FileCollection
        internal set

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    var minSdkVersion: Int = 1
        internal set

    @get:Input
    var maxNumberOfFeaturesBeforeOreo: Int = FeatureSetMetadata.MAX_NUMBER_OF_SPLITS_BEFORE_O
        internal set

    public override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use {
            it.submit(
                FeatureSetRunnable::class.java,
                Params(inputFiles.asFileTree.files,
                    minSdkVersion,
                    maxNumberOfFeaturesBeforeOreo,
                    outputFile.get().asFile
                )
            )
        }
    }

    private data class Params(
        val featureFiles: Set<File>,
        val minSdkVersion: Int,
        val maxNumberOfFeaturesBeforeOreo: Int,
        val outputFile: File
    ) : Serializable

    private class FeatureSetRunnable @Inject constructor(private val params: Params): Runnable {
        override fun run() {
            val features = mutableListOf<FeatureSplitDeclaration>()

            val featureMetadata = FeatureSetMetadata(params.maxNumberOfFeaturesBeforeOreo)

            for (file in params.featureFiles) {
                try {
                    features.add(FeatureSplitDeclaration.load(file))
                } catch (e: FileNotFoundException) {
                    throw BuildException("Cannot read features split declaration file", e)
                }
            }

            val featureNameMap = computeFeatureNames(features)

            for (feature in features) {
                featureMetadata.addFeatureSplit(
                    params.minSdkVersion, feature.modulePath, featureNameMap[feature.modulePath]!!)
            }

            // save the list.
            featureMetadata.save(params.outputFile)
        }
    }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<FeatureSetMetadataWriterTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("generate", "FeatureMetadata")
        override val type: Class<FeatureSetMetadataWriterTask>
            get() = FeatureSetMetadataWriterTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out FeatureSetMetadataWriterTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.FEATURE_SET_METADATA,
                taskProvider,
                FeatureSetMetadataWriterTask::outputFile,
                FeatureSetMetadata.OUTPUT_FILE_NAME
            )
        }

        override fun configure(task: FeatureSetMetadataWriterTask) {
            super.configure(task)

            task.minSdkVersion = variantScope.minSdkVersion.apiLevel

            task.inputFiles = variantScope.getArtifactFileCollection(
                AndroidArtifacts.ConsumedConfigType.REVERSE_METADATA_VALUES,
                AndroidArtifacts.ArtifactScope.PROJECT,
                AndroidArtifacts.ArtifactType.REVERSE_METADATA_FEATURE_DECLARATION
            )
            val maxNumberOfFeaturesBeforeOreo = variantScope.globalScope.projectOptions
                .get(IntegerOption.PRE_O_MAX_NUMBER_OF_FEATURES)
            if (maxNumberOfFeaturesBeforeOreo != null) {
                task.maxNumberOfFeaturesBeforeOreo =
                        Integer.min(100, maxNumberOfFeaturesBeforeOreo)
            }
        }
    }
}

/** Regular expression defining valid characters for split names  */
private val FEATURE_NAME_CHARS = Pattern.compile("[a-zA-Z0-9_]+")

/** Returns the feature name based on the module path. */
fun getFeatureName(modulePath: String): String =
    if (modulePath == ":") modulePath else modulePath.substring(modulePath.lastIndexOf(':') + 1)

/**
 * Converts from a list of [FeatureSplitDeclaration] to a map of (module-path -> feature name)
 *
 * This also performs validation to ensure all feature name are unique.
 */
internal fun computeFeatureNames(features: List<FeatureSplitDeclaration>): Map<String, String> {
    val result = mutableMapOf<String, String>()

    // build a map of (leaf -> list(full paths)). This will allow us to detect duplicates
    // and properly display error messages with all the paths containing the same leaf.
    val leafMap = features.groupBy({ getFeatureName(it.modulePath) }, { it.modulePath })

    // check for root module name (root module)
    if (leafMap.keys.contains(":")) {
        throw RuntimeException("Root module ':' is used as a feature module. This is not supported.")
    }

    // check all the leaves for invalid characters.
    val invalidNames = leafMap.keys.filter { name -> !FEATURE_NAME_CHARS.matcher(name).matches() }
    if (!invalidNames.isEmpty()) {
        throw RuntimeException(
            invalidNames.joinTo(
                StringBuilder(
                    "The following feature module names contain invalid characters. Feature " +
                            "module names can only contain letters, digits and underscores."
                ), separator = "\n\t-> ", prefix = "\n\t-> "
            ).toString()
        )
    }

    // check for duplicate names while building the final (path, leaf) map.
    // Dex splitting relies on all features having unique names.
    for ((leaf, modules) in leafMap) {
        val module = modules.singleOrNull() ?: throw RuntimeException(
            modules.joinTo(
                StringBuilder(
                    "Module name '$leaf' is used by multiple modules. All dynamic features must have a unique name."
                ), separator = "\n\t-> ", prefix = "\n\t-> "
            ).toString()
        )

       result[module] = leaf
    }

    return result
}
