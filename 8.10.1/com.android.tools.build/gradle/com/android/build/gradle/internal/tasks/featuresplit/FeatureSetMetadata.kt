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
package com.android.build.gradle.internal.tasks.featuresplit

import com.android.sdklib.AndroidVersion
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.File
import java.io.IOException

/** Container for all the feature split metadata.  */
class FeatureSetMetadata private constructor(
        val sourceFile: File?,
        featureSplits: Set<FeatureInfo>,
        private val minSdkVersion: Int,
        private val maxNumberOfSplitsBeforeO: Int,
) {
    private val featureSplits = ImmutableSet.copyOf(featureSplits)

    fun getResOffsetFor(modulePath: String): Int? {
        return featureSplits.firstOrNull { it.modulePath == modulePath }?.resOffset
    }

    fun getFeatureNameFor(modulePath: String): String? {
        return featureSplits.firstOrNull { it.modulePath == modulePath }?.featureName
    }

    val featureNameToNamespaceMap: Map<String, String>
        get() = ImmutableMap.copyOf(featureSplits.associateBy({ it.featureName }) { it.namespace })

    private class FeatureInfo(
            val modulePath: String,
            val featureName: String,
            val resOffset: Int,
            val namespace: String,
    )

    class Builder private constructor(
            private val minSdkVersion: Int,
            private val maxNumberOfSplitsBeforeO: Int,
            private val featureSplits: MutableSet<FeatureInfo>,
    ) {

        constructor(
                minSdkVersion: Int,
                maxNumberOfSplitsBeforeO: Int
        ) : this(
                minSdkVersion,
                maxNumberOfSplitsBeforeO,
                mutableSetOf<FeatureInfo>()
        )

        constructor(featureSetMetadata: FeatureSetMetadata) : this(
                featureSetMetadata.minSdkVersion,
                featureSetMetadata.maxNumberOfSplitsBeforeO,
                featureSetMetadata.featureSplits.toMutableSet(),
        )

        fun addFeatureSplit(
                modulePath: String,
                featureName: String,
                packageName: String,
        ) : Int {
            val id: Int = if (minSdkVersion < AndroidVersion.VersionCodes.O) {
                if (featureSplits.size >= maxNumberOfSplitsBeforeO) {
                    throw RuntimeException("You have reached the maximum number of feature splits : "
                            + maxNumberOfSplitsBeforeO)
                }
                // allocate split ID backwards excluding BASE_ID.
                BASE_ID - 1 - featureSplits.size
            } else {
                if (featureSplits.size >= MAX_NUMBER_OF_SPLITS_STARTING_IN_O) {
                    throw RuntimeException("You have reached the maximum number of feature splits : "
                            + MAX_NUMBER_OF_SPLITS_STARTING_IN_O)
                }
                // allocated forward excluding BASE_ID
                BASE_ID + 1 + featureSplits.size
            }
            featureSplits.add(FeatureInfo(modulePath, featureName, id, packageName))
            return id
        }

        @Throws(IOException::class)
        fun save(outputFile: File) {
            JsonWriter(outputFile.bufferedWriter()).use { writer ->
                FeatureSetMetadataTypeAdapter(outputFile).write(writer, build())
            }
        }

        private fun build(): FeatureSetMetadata {
            return FeatureSetMetadata(
                    sourceFile = null,
                    featureSplits = ImmutableSet.copyOf(featureSplits),
                    minSdkVersion = minSdkVersion,
                    maxNumberOfSplitsBeforeO = maxNumberOfSplitsBeforeO,
            )
        }
    }

    fun toBuilder() : Builder {
        return Builder(this)
    }

    companion object {

        const val MAX_NUMBER_OF_SPLITS_BEFORE_O = 50
        const val MAX_NUMBER_OF_SPLITS_STARTING_IN_O = 127

        @VisibleForTesting
        const val OUTPUT_FILE_NAME = "feature-metadata.json"

        /** Base module or application module resource ID  */
        @VisibleForTesting
        const val BASE_ID = 0x7F

        /**
         * Loads the feature set metadata file
         *
         * @param input the location of the file, or the folder that contains it.
         * @return the FeatureSetMetadata instance that contains all the data from the file
         * @throws IOException if the loading failed.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun load(input: File): FeatureSetMetadata {
            val inputFile = if (input.isDirectory) File(input, OUTPUT_FILE_NAME) else input
            JsonReader(inputFile.bufferedReader()).use {
                try {
                    return FeatureSetMetadataTypeAdapter(inputFile).read(it)
                } catch (e: Exception) {
                    throw IOException("Failed loading feature set metadata from $inputFile", e)
                }
            }
        }
    }

    private class FeatureSetMetadataTypeAdapter(private val sourceFile: File?) : TypeAdapter<FeatureSetMetadata>() {

        override fun write(writer: JsonWriter, metadata: FeatureSetMetadata) {
            with(writer) {
                beginObject()
                name("minSdkVersion").value(metadata.minSdkVersion)
                name("maxNumberOfSplitsBeforeO").value(metadata.maxNumberOfSplitsBeforeO)
                name("featureSplits").beginArray()
                for (split in metadata.featureSplits) {
                    FeatureInfoTypeAdapter.write(writer, split)
                }
                writer.endArray()
                endObject()
            }
        }

        override fun read(reader: JsonReader): FeatureSetMetadata {
            val splits = ImmutableSet.builder<FeatureInfo>()
            var minSdkVersion: Int? = null
            var maxNumberOfSplitsBeforeO: Int? = null
            with(reader) {
                beginObject()
                while(hasNext()) {
                    when (nextName()) {
                        "minSdkVersion" -> minSdkVersion = nextInt()
                        "maxNumberOfSplitsBeforeO" -> maxNumberOfSplitsBeforeO = nextInt()
                        "featureSplits" -> {
                            beginArray()
                            while (hasNext()) {
                                splits.add(FeatureInfoTypeAdapter.read(reader))
                            }
                            endArray()
                        }
                        else -> skipValue()
                    }
                }
                endObject()
            }
            return FeatureSetMetadata(sourceFile, splits.build(), minSdkVersion!!, maxNumberOfSplitsBeforeO!!)
        }
    }

    private object FeatureInfoTypeAdapter : TypeAdapter<FeatureInfo>() {

        override fun write(writer: JsonWriter, featureInfo: FeatureInfo) {
            with(writer) {
                beginObject()
                name("modulePath").value(featureInfo.modulePath)
                name("featureName").value(featureInfo.featureName)
                name("resOffset").value(featureInfo.resOffset)
                name("namespace").value(featureInfo.namespace)
                endObject()
            }
        }

        override fun read(reader: JsonReader): FeatureInfo {
            with(reader) {
                beginObject()
                var modulePath: String? = null
                var featureName: String? = null
                var resOffset: Int? = null
                var namespace: String? = null
                while (hasNext()) {
                    when (nextName()) {
                        "modulePath" -> modulePath = nextString()
                        "featureName" -> featureName = nextString()
                        "resOffset" -> resOffset = nextInt()
                        "namespace" -> namespace = nextString()
                        else -> skipValue()
                    }
                }
                val featureInfo = FeatureInfo(modulePath!!, featureName!!, resOffset!!, namespace!!)
                endObject()
                return featureInfo
            }
        }
    }
}
