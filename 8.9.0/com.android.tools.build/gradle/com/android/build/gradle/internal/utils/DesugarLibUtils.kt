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

@file:JvmName("DesugarLibUtils")

package com.android.build.gradle.internal.utils

import com.android.build.api.variant.AndroidVersion
import com.android.build.api.variant.impl.getFeatureLevel
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.build.gradle.internal.dependency.L8DesugarLibTransformRegistration
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_CORE_LIBRARY_DESUGARING
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.build.gradle.internal.utils.DesugarConfigJson.Companion.combineFileContents
import com.android.builder.dexing.D8DesugaredMethodsGenerator
import com.android.sdklib.AndroidTargetHash
import com.google.common.io.ByteStreams
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

// The name of desugar config json file
private const val DESUGAR_LIB_CONFIG_FILE = "desugar.json"

const val DESUGARED_DESUGAR_LIB = "_internal-desugared-desugar-lib"
// The output of DesugarLibConfigExtractor which extracts the desugar config json file from
// desugar lib configuration jar
const val DESUGAR_LIB_CONFIG = "_internal-desugar-lib-config"
private const val DESUGAR_LIB_COMPONENT_NAME = "desugar_jdk_libs_configuration"
private const val DESUGAR_LIB_LINT = "_internal-desugar-lib-lint"
const val D8_DESUGAR_METHODS = "_internal-d8-desugar-methods"
val ATTR_LINT_MIN_SDK: Attribute<String> = Attribute.of("lint-min-sdk", String::class.java)
private val ATTR_LINT_COMPILE_SDK: Attribute<String> =
    Attribute.of("lint-compile-sdk", String::class.java)
val ATTR_ENABLE_CORE_LIBRARY_DESUGARING: Attribute<String> =
    Attribute.of("enable-core-library-desugaring", String::class.java)

/**
 * Returns a file collection which contains desugar lib jars
 */
fun getDesugarLibJarFromMaven(services: TaskCreationServices): FileCollection {
    val configuration = getDesugarLibConfiguration(services)
    return getArtifactCollection(configuration)
}

/**
 * Returns the dependency graph resolved for [getDesugarLibConfiguration] which contains
 * the metadata(group/name/version) of desugar lib.
 */
fun getDesugarLibDependencyGraph(services: TaskCreationServices): Provider<ResolvedComponentResult> {
    val configuration = getDesugarLibConfiguration(services)
    return configuration.incoming.resolutionResult.rootComponent
}

/**
 * Returns a jar which is a desugared version of desugar library jars
 */
fun getDesugaredDesugarLib(creationConfig: ApkCreationConfig): FileCollection {
    L8DesugarLibTransformRegistration.registerTransformIfAbsent(creationConfig)

    return getDesugarLibConfiguration(creationConfig.services).incoming.artifactView {
        it.componentFilter { id ->
            !id.displayName.contains(DESUGAR_LIB_COMPONENT_NAME)
        }
        L8DesugarLibTransformRegistration.getOutputArtifactAttributes(creationConfig)
            .addAttributesToContainer(it.attributes)
    }.artifacts.artifactFiles
}

/** Implementation of provider holding JSON file value. */
abstract class DesugarConfigJson : ValueSource<String, DesugarConfigJson.Parameters> {

    interface Parameters : ValueSourceParameters {

        val desugarJson: ConfigurableFileCollection
    }

    override fun obtain(): String? {
        return combineFileContents(parameters.desugarJson.files)
    }

    companion object {

        fun combineFileContents(jsonFiles: Collection<File>): String? {
            return if (jsonFiles.isEmpty()) {
                null
            } else {
                jsonFiles.joinToString("\n") { it.readText() }
            }
        }
    }
}

/**
 * Returns a provider which represents the content of desugar.json file extracted from
 * desugar lib configuration jars
 *
 * IMPORTANT: DO NOT USE this method to set up a transform's input as this method uses [ValueSource]
 * which might cause deadlock when used as a transform input (see bug 329346760); use
 * [getDesugarLibConfigFiles] instead.
 *
 * This method can still be used to set up a task's input (the deadlock mentioned above seems to
 * happen only to [ValueSource] being used transform inputs, not task inputs).
 */
fun getDesugarLibConfig(services: TaskCreationServices): Provider<String> {
    val configuration = services.configurations.getByName(CONFIG_NAME_CORE_LIBRARY_DESUGARING)

    registerDesugarLibConfigExtractorTransformIfAbsent(services)

    return services.providerOf(DesugarConfigJson::class.java) {
        it.parameters.desugarJson.setFrom(getDesugarLibConfigFromTransform(configuration))
    }
}

/** Returns desugar.json file extracted from desugar lib configuration jars. */
fun getDesugarLibConfigFiles(services: TaskCreationServices): FileCollection {
    val configuration = services.configurations.getByName(CONFIG_NAME_CORE_LIBRARY_DESUGARING)

    registerDesugarLibConfigExtractorTransformIfAbsent(services)

    return getDesugarLibConfigFromTransform(configuration)
}

/**
 * Returns a desugar.json file extracted from desugar lib configuration jar.
 */
fun getDesugarLibConfigFile(project: Project): List<File> {
    val configuration = project.configurations.findByName(CONFIG_NAME_CORE_LIBRARY_DESUGARING)!!

    return getDesugarLibConfigFromTransform(configuration).files.toList()
}

/**
 * Returns a [FileCollection] which contains files with desugared methods supported by D8 and core
 * library desugaring.
 */
fun getDesugaredMethods(
    services: TaskCreationServices,
    coreLibDesugar: Boolean,
    minSdkVersion: AndroidVersion,
    global: GlobalTaskCreationConfig
): FileCollection {

    val desugaredMethodsFiles = services.fileCollection()
    val minSdk = minSdkVersion.getFeatureLevel()
    if (coreLibDesugar && global.compileSdkHashString != null) {
        val compileSdk = AndroidTargetHash.getPlatformVersion(global.compileSdkHashString)!!.featureLevel
        registerDesugarLibLintTransform(services, minSdk, compileSdk)
        desugaredMethodsFiles.from(
            getDesugarLibLintFromTransform(getDesugarLibConfiguration(services), minSdk, compileSdk)
        )
    }

    desugaredMethodsFiles.fromDisallowChanges(
        getD8DesugarMethodFileFromTransform(global.fakeDependency, coreLibDesugar, minSdk)
    )
    return desugaredMethodsFiles
}

/**
 * Returns the configuration of core library to be desugared and throws runtime exception if the
 * user didn't add any dependency to that configuration.
 *
 * Note: this method is only used when core library desugaring is enabled.
 */
private fun getDesugarLibConfiguration(services: TaskCreationServices): Configuration {
    val configuration = services.configurations.findByName(CONFIG_NAME_CORE_LIBRARY_DESUGARING)!!
    if (configuration.dependencies.isEmpty()) {
        throw RuntimeException(
            "$CONFIG_NAME_CORE_LIBRARY_DESUGARING configuration contains no " +
                    "dependencies. If you intend to enable core library desugaring, please add " +
                    "dependencies to $CONFIG_NAME_CORE_LIBRARY_DESUGARING configuration."
        )
    }
    return configuration
}

private fun getDesugarLibConfigFromTransform(configuration: Configuration): FileCollection {
    return configuration.incoming.artifactView { configuration ->
        configuration.attributes {
            it.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                DESUGAR_LIB_CONFIG
            )
        }
    }.artifacts.artifactFiles
}

private fun getArtifactCollection(configuration: Configuration): FileCollection =
    configuration.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                ArtifactTypeDefinition.JAR_TYPE
            )
        }
    }.artifacts.artifactFiles

/** Registers [DesugarLibConfigExtractor] transform if it is not yet registered. */
private fun registerDesugarLibConfigExtractorTransformIfAbsent(services: TaskCreationServices) {
    val transformRegistered = "_agp_internal_${DesugarLibConfigExtractor::class.simpleName}_registered"
    if (services.extraProperties.has(transformRegistered)) {
        return
    }
    services.extraProperties[transformRegistered] = true

    services.dependencies.registerTransform(DesugarLibConfigExtractor::class.java) { spec ->
        spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
        spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, DESUGAR_LIB_CONFIG)
    }
}

private fun registerDesugarLibLintTransform(
    services: TaskCreationServices,
    minSdkVersion: Int,
    compileSdkVersion: Int
) {
    services.dependencies.registerTransform(DesugarLibLintExtractor::class.java) { spec ->
        spec.parameters { parameters ->
            parameters.minSdkVersion.set(minSdkVersion)
            parameters.compileSdkVersion.set(compileSdkVersion)
        }
        spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
        spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, DESUGAR_LIB_LINT)
        spec.from.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
        spec.to.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
        spec.from.attribute(ATTR_LINT_COMPILE_SDK, compileSdkVersion.toString())
        spec.to.attribute(ATTR_LINT_COMPILE_SDK, compileSdkVersion.toString())
    }
}

private fun getDesugarLibLintFromTransform(
    configuration: Configuration,
    minSdkVersion: Int,
    compileSdkVersion: Int
): FileCollection {
    return configuration.incoming.artifactView { configuration ->
        configuration.attributes {
            it.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                DESUGAR_LIB_LINT
            )
            it.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
            it.attribute(ATTR_LINT_COMPILE_SDK, compileSdkVersion.toString())
        }
    }.artifacts.artifactFiles
}

private fun getD8DesugarMethodFileFromTransform(
    configuration: Configuration,
    coreLibDesugar: Boolean,
    minSdkVersion: Int,
): FileCollection {
    return configuration.incoming.artifactView { configuration ->
        configuration.attributes {
            it.attribute(ARTIFACT_TYPE_ATTRIBUTE, D8_DESUGAR_METHODS)
            it.attribute(ATTR_ENABLE_CORE_LIBRARY_DESUGARING, coreLibDesugar.toString())
            it.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
        }
    }.artifacts.artifactFiles
}

/**
 * Extract the desugar config json file from desugar lib configuration jar.
 */
@CacheableTransform
abstract class DesugarLibConfigExtractor : TransformAction<TransformParameters.None> {

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        ZipInputStream(inputFile.inputStream().buffered()).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                if (entry.name.endsWith(DESUGAR_LIB_CONFIG_FILE)) {
                    val outputFile =
                        outputs.file(inputFile.nameWithoutExtension + "-$DESUGAR_LIB_CONFIG_FILE")
                    Files.newOutputStream(outputFile.toPath()).buffered().use { output ->
                        ByteStreams.copy(zipInputStream, output)
                    }
                    break
                }
            }
        }
    }
}

/**
 * Extract the specific lint file with desugared APIs based on minSdkVersion & compileSdkVersion
 * from desugar lib configuration jar.
 */
@CacheableTransform
abstract class DesugarLibLintExtractor : TransformAction<DesugarLibLintExtractor.Parameters> {

    interface Parameters : GenericTransformParameters {

        @get:Input
        val minSdkVersion: Property<Int>

        @get:Input
        val compileSdkVersion: Property<Int>
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        val minSdkVersion = parameters.minSdkVersion.get()

        // Search for the lint file based on compileSdkVersion specified by the user. If we are not able to find it,
        // fallback to the lint file with a lower compileSdkVersion. Currently, the lowest compileSdkVersion for
        // lint files is 26.
        for (compileSdkVersion in parameters.compileSdkVersion.get() downTo 26) {
            val pattern = if (minSdkVersion >= 21) {
                "${compileSdkVersion}_21.txt"
            } else {
                "${compileSdkVersion}_1.txt"
            }

            ZipInputStream(inputFile.inputStream().buffered()).use { zipInputStream ->
                while (true) {
                    val entry = zipInputStream.nextEntry ?: break
                    if (entry.name.endsWith(pattern)) {
                        val outputFile =
                            outputs.file(inputFile.nameWithoutExtension + "-desugar-lint.txt")
                        Files.newOutputStream(outputFile.toPath()).buffered().use { output ->
                            ByteStreams.copy(zipInputStream, output)
                        }
                        return
                    }
                }
            }
        }
    }
}

/**
 * Generate a file of D8 backported desugared methods by invoking D8 API instead of processing
 * input artifact.
 */
@CacheableTransform
abstract class D8BackportedMethodsGenerator
    : TransformAction<D8BackportedMethodsGenerator.Parameters> {

    interface Parameters : GenericTransformParameters {

        @get:Input
        val d8Version: Property<String>

        @get:Input
        val minSdkVersion: Property<Int>

        @get:InputFiles
        @get:PathSensitive(PathSensitivity.NONE)
        val desugarLibConfigFiles: ConfigurableFileCollection

        @get:CompileClasspath
        @get:Optional
        val bootclasspath: ConfigurableFileCollection
    }

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val outputFile = outputs.file("D8BackportedDesugaredMethods.txt")
        outputFile.printWriter().use {
            D8DesugaredMethodsGenerator.generate(
                combineFileContents(parameters.desugarLibConfigFiles.files),
                parameters.bootclasspath.files,
                parameters.minSdkVersion.get(),
            ).forEach { method ->
                it.println(method)
            }
        }
    }
}
