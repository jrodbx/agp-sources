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
import com.android.build.gradle.internal.dependency.GenericTransformParameters
import com.android.build.gradle.internal.dependency.L8DesugarLibTransform
import com.android.build.gradle.internal.dependency.VariantDependencies.Companion.CONFIG_NAME_CORE_LIBRARY_DESUGARING
import com.android.build.gradle.internal.services.TaskCreationServices
import com.android.build.gradle.internal.tasks.factory.GlobalTaskCreationConfig
import com.android.builder.dexing.D8DesugaredMethodsGenerator
import com.android.sdklib.AndroidTargetHash
import com.google.common.io.ByteStreams
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.artifacts.ArtifactAttributes
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.nio.charset.StandardCharsets
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
private val ATTR_LINT_MIN_SDK: Attribute<String> = Attribute.of("lint-min-sdk", String::class.java)
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
fun getDesugaredDesugarLib(
        services: TaskCreationServices,
        minSdkVersion: Int,
        fullBootClasspath: ConfigurableFileCollection
): FileCollection {
    val configuration = getDesugarLibConfiguration(services)

    registerDesugaredDesugarLibTransform(
        services,
        minSdkVersion,
        getDesugarLibConfig(services),
        fullBootClasspath
    )

    return getDesugaredDesugarLibFromTransfrom(configuration)
}

/** Implementation of provider holding JSON file value. */
abstract class DesugarConfigJson : ValueSource<String, DesugarConfigJson.Parameters> {

    interface Parameters : ValueSourceParameters {

        val desugarJson: ConfigurableFileCollection
    }

    override fun obtain(): String? {
        val jsonFiles = parameters.desugarJson.files
        return if (jsonFiles.isEmpty()) {
            null
        } else {
            val content = StringBuilder()
            val dirs = jsonFiles.map { it.toPath() }
            dirs.forEach {
                content.append(String(Files.readAllBytes(it), StandardCharsets.UTF_8))
            }
            content.toString()
        }
    }
}

/**
 * Returns a provider which represents the content of desugar.json file extracted from
 * desugar lib configuration jars
 */
fun getDesugarLibConfig(services: TaskCreationServices): Provider<String> {
    val configuration = services.configurations.findByName(CONFIG_NAME_CORE_LIBRARY_DESUGARING)!!

    registerDesugarLibConfigTransform(services.dependencies)

    return services.providerOf(DesugarConfigJson::class.java) {
        it.parameters.desugarJson.setFrom(getDesugarLibConfigFromTransform(configuration))
    }
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

    if (coreLibDesugar && global.compileSdkHashString != null) {
        val minSdk = minSdkVersion.getFeatureLevel()
        val compileSdk = AndroidTargetHash.getPlatformVersion(global.compileSdkHashString)!!.featureLevel
        registerDesugarLibLintTransform(services, minSdk, compileSdk)
        desugaredMethodsFiles.from(
            getDesugarLibLintFromTransform(getDesugarLibConfiguration(services), minSdk, compileSdk)
        )
    }

    desugaredMethodsFiles.fromDisallowChanges(
        getD8DesugarMethodFileFromTransform(global.fakeDependency, coreLibDesugar)
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
                ArtifactAttributes.ARTIFACT_FORMAT,
                DESUGAR_LIB_CONFIG
            )
        }
    }.artifacts.artifactFiles
}

private fun getDesugaredDesugarLibFromTransfrom(configuration: Configuration): FileCollection {
    return configuration.incoming.artifactView { configuration ->
        configuration.componentFilter { id ->
            !id.displayName.contains(DESUGAR_LIB_COMPONENT_NAME)
        }
        configuration.attributes {
            it.attribute(
                ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                DESUGARED_DESUGAR_LIB
            )
        }
    }.artifacts.artifactFiles
}

private fun getArtifactCollection(configuration: Configuration): FileCollection =
    configuration.incoming.artifactView { config ->
        config.attributes {
            it.attribute(
                ArtifactAttributes.ARTIFACT_FORMAT,
                ArtifactTypeDefinition.JAR_TYPE
            )
        }
    }.artifacts.artifactFiles

private fun registerDesugarLibConfigTransform(dependencies: DependencyHandler) {
    dependencies.registerTransform(DesugarLibConfigExtractor::class.java) { spec ->
        spec.from.attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE)
        spec.to.attribute(ArtifactAttributes.ARTIFACT_FORMAT, DESUGAR_LIB_CONFIG)
    }
}

private fun registerDesugaredDesugarLibTransform(
    services: TaskCreationServices,
    minSdkVersion: Int,
    libConfiguration: Provider<String>,
    fullBootClasspath: ConfigurableFileCollection
) {
    services.dependencies.registerTransform(L8DesugarLibTransform::class.java) { spec ->
        spec.parameters { parameters ->
            parameters.minSdkVersion.set(minSdkVersion)
            parameters.libConfiguration.set(libConfiguration)
            parameters.fullBootClasspath.from(fullBootClasspath)
        }
        spec.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
        spec.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, DESUGARED_DESUGAR_LIB)
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
        spec.from.attribute(ArtifactAttributes.ARTIFACT_FORMAT, ArtifactTypeDefinition.JAR_TYPE)
        spec.to.attribute(ArtifactAttributes.ARTIFACT_FORMAT, DESUGAR_LIB_LINT)
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
                ArtifactAttributes.ARTIFACT_FORMAT,
                DESUGAR_LIB_LINT
            )
            it.attribute(ATTR_LINT_MIN_SDK, minSdkVersion.toString())
            it.attribute(ATTR_LINT_COMPILE_SDK, compileSdkVersion.toString())
        }
    }.artifacts.artifactFiles
}

private fun getD8DesugarMethodFileFromTransform(
    configuration: Configuration,
    coreLibDesugar: Boolean
): FileCollection {
    return configuration.incoming.artifactView { configuration ->
        configuration.attributes {
            it.attribute(ArtifactAttributes.ARTIFACT_FORMAT, D8_DESUGAR_METHODS)
            it.attribute(ATTR_ENABLE_CORE_LIBRARY_DESUGARING, coreLibDesugar.toString())
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
        @get:Optional
        val coreLibDesugarConfig: Property<String>

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
                parameters.coreLibDesugarConfig.orNull,
                parameters.bootclasspath.files
            ).forEach { method ->
                it.println(method)
            }
        }
    }
}
