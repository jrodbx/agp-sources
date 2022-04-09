/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.MultiOutputHandler
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import java.io.File
import java.io.Serializable

internal abstract class MultiOutputHandlerImplBase: MultiOutputHandler {

    private var _delegate: MultiOutputHandler? = null

    private val delegate: MultiOutputHandler
        get() {
            if (_delegate == null) {
                _delegate = createSerializable()
            }
            return _delegate!!
        }

    override val mainVersionCode: Int?
        get() = delegate.mainVersionCode
    override val mainVersionName: String?
        get() = delegate.mainVersionName

    protected abstract fun createSerializable(): MultiOutputHandler

    override fun getOutputs(
        configFilter: (VariantOutputConfiguration) -> Boolean
    ) = delegate.getOutputs(configFilter)

    override fun getMainSplitArtifact(artifactsDirectory: Provider<Directory>) =
        delegate.getMainSplitArtifact(artifactsDirectory)

    override fun computeBuildOutputFile(dir: File, output: VariantOutputImpl.SerializedForm) =
        delegate.computeBuildOutputFile(dir, output)

    override fun computeUniqueDirForSplit(
        dir: File,
        output: VariantOutputImpl.SerializedForm,
        variantName: String
    ) = delegate.computeUniqueDirForSplit(dir, output, variantName)

    override fun extractArtifactForSplit(
        artifacts: BuiltArtifactsImpl,
        config: VariantOutputConfiguration
    ) = delegate.extractArtifactForSplit(artifacts, config)

    override fun getOutput(config: VariantOutputConfiguration) = delegate.getOutput(config)

    override fun getOutputNameForSplit(
        prefix: String,
        suffix: String,
        outputType: VariantOutputConfiguration.OutputType,
        filters: Collection<FilterConfiguration>
    ) = delegate.getOutputNameForSplit(prefix, suffix, outputType, filters)

    override fun toSerializable() = delegate
}

internal class ApplicationMultiOutputHandler(
    creationConfig: ApplicationCreationConfig
): MultiOutputHandlerImplBase() {

    @get:Nested
    val variantOutputs = creationConfig.outputs.getEnabledVariantOutputs()

    override fun createSerializable(): MultiOutputHandler =
        SerializableApplicationMultiOutputHandler(this)
}

private class SerializableApplicationMultiOutputHandler(
    applicationOutputHandler: ApplicationMultiOutputHandler
): MultiOutputHandler, Serializable {

    @get:Nested
    val variantOutputs = applicationOutputHandler.variantOutputs.map { it.toSerializedForm() }

    private val mainSplit: VariantOutputImpl.SerializedForm
        get() = variantOutputs.find {
            it.variantOutputConfiguration.outputType == VariantOutputConfiguration.OutputType.SINGLE
        } ?: variantOutputs.find {
            it.variantOutputConfiguration.outputType == VariantOutputConfiguration.OutputType.UNIVERSAL
        } ?: variantOutputs.find {
            it.variantOutputConfiguration.outputType == VariantOutputConfiguration.OutputType.ONE_OF_MANY
        } ?: throw RuntimeException("Cannot determine main split information, file a bug.")

    override val mainVersionCode: Int?
        get() = mainSplit.versionCode
    override val mainVersionName: String?
        get() = mainSplit.versionName

    override fun getOutputs(
        configFilter: (VariantOutputConfiguration) -> Boolean
    ) = variantOutputs.filter {
        configFilter.invoke(it.variantOutputConfiguration)
    }

    override fun extractArtifactForSplit(
        artifacts: BuiltArtifactsImpl,
        config: VariantOutputConfiguration
    ) = artifacts.getBuiltArtifact(config)

    override fun getOutput(config: VariantOutputConfiguration) =
        variantOutputs.firstOrNull {
            config.outputType == it.variantOutputConfiguration.outputType &&
                    config.filters == it.variantOutputConfiguration.filters
        }

    override fun getOutputNameForSplit(
        prefix: String,
        suffix: String,
        outputType: VariantOutputConfiguration.OutputType,
        filters: Collection<FilterConfiguration>
    ): String {
        val sb = StringBuilder(prefix)
        if (sb.isNotEmpty()) {
            sb.append("-")
        }
        val split = variantOutputs.find {
            it.variantOutputConfiguration.outputType == outputType &&
                    it.variantOutputConfiguration.filters == filters
        } ?: throw RuntimeException("Cannot find variant output for $outputType, $filters")
        sb.append(split.baseName)

        if (suffix.isNotEmpty()) {
            sb.append("-")
            sb.append(suffix)
        }
        return sb.toString()
    }

    override fun getMainSplitArtifact(artifactsDirectory: Provider<Directory>) =
        BuiltArtifactsLoaderImpl().load(artifactsDirectory)?.getBuiltArtifact(mainSplit.variantOutputConfiguration)

    override fun computeBuildOutputFile(dir: File, output: VariantOutputImpl.SerializedForm) =
        File(dir, output.outputFileName)

    override fun computeUniqueDirForSplit(
        dir: File,
        output: VariantOutputImpl.SerializedForm,
        variantName: String
    ) = File(dir, output.fullName)

    override fun toSerializable() = this
}

internal class SingleOutputHandler(
    creationConfig: ComponentCreationConfig
): MultiOutputHandlerImplBase() {

    @get:Input
    val singleOutputFileName: Provider<String> = creationConfig
        .services
        .projectInfo
        .getProjectBaseName()
        .map {
            creationConfig.paths.getOutputFileName(it, creationConfig.baseName)
        }

    override fun createSerializable(): MultiOutputHandler =
        SerializableSingleOutputHandler(this)
}

private class SerializableSingleOutputHandler(
    singleOutputHandler: SingleOutputHandler
): MultiOutputHandler, Serializable {

    @get:Input
    val singleOutputFileName: String = singleOutputHandler.singleOutputFileName.get()

    private val dummyOutput = VariantOutputImpl.SerializedForm(
        versionCode = null,
        versionName = null,
        variantOutputConfiguration = VariantOutputConfigurationImpl(),
        baseName = "",
        fullName = "",
        outputFileName = ""
    )

    override val mainVersionCode = null
    override val mainVersionName = null

    override fun getMainSplitArtifact(artifactsDirectory: Provider<Directory>) =
        getMainSplitArtifact(BuiltArtifactsLoaderImpl().load(artifactsDirectory))

    override fun extractArtifactForSplit(
        artifacts: BuiltArtifactsImpl,
        config: VariantOutputConfiguration
    ): BuiltArtifactImpl? {
        if (config != dummyOutput.variantOutputConfiguration) {
            return null
        }
        return getMainSplitArtifact(artifacts)
    }

    private fun getMainSplitArtifact(artifacts: BuiltArtifactsImpl?) =
        artifacts?.getBuiltArtifact(dummyOutput.variantOutputConfiguration)

    override fun getOutputs(
        configFilter: (VariantOutputConfiguration) -> Boolean
    ) = listOf(dummyOutput).filter { configFilter.invoke(it.variantOutputConfiguration) }

    override fun getOutput(
        config: VariantOutputConfiguration
    ) = dummyOutput.takeIf { config == it.variantOutputConfiguration }

    override fun getOutputNameForSplit(
        prefix: String,
        suffix: String,
        outputType: VariantOutputConfiguration.OutputType,
        filters: Collection<FilterConfiguration>
    ): String {
        if (prefix.isEmpty() || suffix.isEmpty()) {
            return prefix + suffix
        }
        return "$prefix-$suffix"
    }

    override fun computeBuildOutputFile(dir: File, output: VariantOutputImpl.SerializedForm) =
        File(dir, singleOutputFileName)

    override fun computeUniqueDirForSplit(
        dir: File,
        output: VariantOutputImpl.SerializedForm,
        variantName: String
    ) = File(dir, variantName)

    override fun toSerializable() = this
}
