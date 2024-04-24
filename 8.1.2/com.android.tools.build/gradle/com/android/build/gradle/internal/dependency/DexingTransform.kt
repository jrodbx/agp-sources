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

package com.android.build.gradle.internal.dependency

import com.android.build.api.variant.impl.getFeatureLevel
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.dependency.AsmClassesTransform.Companion.ATTR_ASM_TRANSFORMED_VARIANT
import com.android.build.gradle.internal.dexing.readDesugarGraph
import com.android.build.gradle.internal.dexing.writeDesugarGraph
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.SyncOptions
import com.android.build.gradle.tasks.toSerializable
import com.android.builder.dexing.ClassFileInput
import com.android.builder.dexing.ClassFileInputs
import com.android.builder.dexing.DependencyGraphUpdater
import com.android.builder.dexing.DexArchiveBuilder
import com.android.builder.dexing.DexFilePerClassFile
import com.android.builder.dexing.DexParameters
import com.android.builder.dexing.MutableDependencyGraph
import com.android.builder.dexing.isJarFile
import com.android.builder.dexing.r8.ClassFileProviderFactory
import com.android.builder.files.SerializableFileChanges
import com.android.sdklib.AndroidVersion
import com.android.utils.FileUtils
import com.google.common.io.Closer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.slf4j.LoggerFactory
import java.io.File
import javax.inject.Inject

@CacheableTransform
abstract class BaseDexingTransform<T : BaseDexingTransform.Parameters> : TransformAction<T> {

    interface Parameters : GenericTransformParameters {
        @get:Input
        val minSdkVersion: Property<Int>
        @get:Input
        val debuggable: Property<Boolean>
        @get:Input
        val enableDesugaring: Property<Boolean>
        @get:Classpath
        val bootClasspath: ConfigurableFileCollection
        @get:Internal
        val errorFormat: Property<SyncOptions.ErrorFormatMode>
        @get:Optional
        @get:Input
        val libConfiguration: Property<String>
        @get:Input
        val enableGlobalSynthetics: Property<Boolean>
        @get:Input
        val enableApiModeling: Property<Boolean>
    }

    @get:Inject
    abstract val inputChanges: InputChanges

    @get:Classpath
    @get:Incremental
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    /** Classpath used for dexing/desugaring, or null if it is not required. */
    protected abstract fun computeClasspathFiles(): List<File>?

    override fun transform(outputs: TransformOutputs) {
        //TODO(b/162813654) record transform execution span
        val inputDirOrJar = inputArtifact.get().asFile.also {
            check(it.isDirectory || isJarFile(it)) {
                "Expected directory or jar but found: ${it.path}"
            }
        }
        val classpath = computeClasspathFiles()
        val outputDir = outputs.dir(inputDirOrJar.nameWithoutExtension)

        // Currently we are able to run incrementally only if
        //   - The input artifact is a directory (not jar).
        //   - Dexing/desugaring does not require a classpath.
        // This is because the desugaring graph that is used for incremental dexing needs to be
        // relocatable, which requires that all files in the graph share one single root directory
        // so that they can be converted to relative paths (see `DesugarGraph`'s kdoc).
        val provideIncrementalSupport = inputDirOrJar.isDirectory && classpath == null

        val (dexOutputDir, globalSyntheticsOutputDir) = if (parameters.enableGlobalSynthetics.get()) {
            Pair(
                outputDir.resolve(computeDexDirName(outputDir)),
                outputDir.resolve(computeGlobalSyntheticsDirName(outputDir))
            )
        } else {
            Pair(outputDir, null)
        }
        val desugarGraphOutputFile = if (provideIncrementalSupport) {
            outputDir.resolve(DESUGAR_GRAPH_FILE_NAME)
        } else null

        if (provideIncrementalSupport && inputChanges.isIncremental) {
            logger.verbose("Running dexing transform incrementally for '${inputDirOrJar.path}'")
            processIncrementally(
                // Must be a directory (see the definition of `provideIncrementalSupport`)
                inputDir = inputDirOrJar,
                // `inputChanges` contains changes to the input artifact only, changes to the
                // classpath are not available (https://github.com/gradle/gradle/issues/11794)
                inputDirChanges = inputChanges.getFileChanges(inputArtifact).toSerializable(),
                dexOutputDir,
                globalSyntheticsOutputDir,
                desugarGraphOutputFile!!
            )
        } else {
            logger.verbose("Running dexing transform non-incrementally for '${inputDirOrJar.path}'")
            processNonIncrementally(
                inputDirOrJar,
                classpath,
                dexOutputDir,
                globalSyntheticsOutputDir,
                provideIncrementalSupport,
                desugarGraphOutputFile
            )
        }
    }

    private fun processIncrementally(
        inputDir: File,
        inputDirChanges: SerializableFileChanges,
        dexOutputDir: File,
        globalSyntheticsOutputDir: File?,
        desugarGraphOutputFile: File
    ) {
        val desugarGraph = DesugarGraph.read(desugarGraphOutputFile, rootDir = inputDir)

        // Compute impacted files based on the changed files and the desugaring graph
        val removedFiles = inputDirChanges.removedFiles.map { it.file }
        val modifiedFiles = inputDirChanges.modifiedFiles.map { it.file }
        val addedFiles = inputDirChanges.addedFiles.map { it.file }
        val unchangedButImpactedFiles = desugarGraph.getAllDependents(removedFiles + modifiedFiles)

        // Remove outputs of removed, modified, and unchanged-but-impacted class files
        (removedFiles + modifiedFiles + unchangedButImpactedFiles)
            .filter { ClassFileInput.CLASS_MATCHER.test(it.path) }
            .forEach { classFile ->
                val classFileRelativePath = classFile.toRelativeString(inputDir)
                // Output mode must be DexFilePerClassFile (see the `process` method)
                DexFilePerClassFile.getDexOutputRelativePath(classFileRelativePath)
                    .let { FileUtils.deleteIfExists(dexOutputDir.resolve(it)) }
                globalSyntheticsOutputDir?.let {
                    DexFilePerClassFile.getGlobalSyntheticOutputRelativePath(classFileRelativePath)
                        .let { FileUtils.deleteIfExists(globalSyntheticsOutputDir.resolve(it)) }
                }
                desugarGraph.removeNode(classFile)
            }

        // Process only class files that are modified, unchanged-but-impacted, or added
        val modifiedImpactedOrAddedFiles =
            (modifiedFiles + unchangedButImpactedFiles + addedFiles).toSet()
        val inputFilter: (File, String) -> Boolean = { _, relativePath: String ->
            inputDir.resolve(relativePath) in modifiedImpactedOrAddedFiles
        }

        process(
            inputDir,
            inputFilter,
            // `classpath` must be null (see the definition of `provideIncrementalSupport`)
            classpath = null,
            dexOutputDir,
            globalSyntheticsOutputDir,
            provideIncrementalSupport = true,
            desugarGraph
        )

        desugarGraph.write(desugarGraphOutputFile)
    }

    private fun processNonIncrementally(
        inputDirOrJar: File,
        classpath: List<File>?,
        dexOutputDir: File,
        globalSyntheticsOutputDir: File?,
        provideIncrementalSupport: Boolean,
        desugarGraphOutputFile: File? // Not-null iff provideIncrementalSupport == true
    ) {
        FileUtils.cleanOutputDir(dexOutputDir)
        globalSyntheticsOutputDir?.let { FileUtils.cleanOutputDir(it) }
        desugarGraphOutputFile?.let { FileUtils.deleteIfExists(it) }

        val desugarGraph = if (provideIncrementalSupport) {
            // `inputDirOrJar` must be a directory when `provideIncrementalSupport == true`
            // (see the definition of `provideIncrementalSupport`)
            DesugarGraph(rootDir = inputDirOrJar)
        } else null

        process(
            inputDirOrJar,
            { _, _ -> true },
            classpath,
            dexOutputDir,
            globalSyntheticsOutputDir,
            provideIncrementalSupport,
            desugarGraph
        )

        if (provideIncrementalSupport) {
            desugarGraph!!.write(desugarGraphOutputFile!!)
        }
    }

    private fun process(
        inputDirOrJar: File,
        inputFilter: (File, String) -> Boolean,
        classpath: List<File>?,
        dexOutputDir: File,
        globalSyntheticsOutputDir: File?,
        provideIncrementalSupport: Boolean,
        desugarGraph: DesugarGraph? // Not-null iff provideIncrementalSupport == true
    ) {
        @Suppress("UnstableApiUsage")
        Closer.create().use { closer ->
            val d8DexBuilder = DexArchiveBuilder.createD8DexBuilder(
                DexParameters(
                    minSdkVersion = parameters.minSdkVersion.get(),
                    debuggable = parameters.debuggable.get(),
                    // dexPerClass iff provideIncrementalSupport == true
                    dexPerClass = provideIncrementalSupport,
                    withDesugaring = parameters.enableDesugaring.get(),
                    desugarBootclasspath = ClassFileProviderFactory(
                        parameters.bootClasspath.files.map(File::toPath)
                    )
                        .also { closer.register(it) },
                    desugarClasspath = ClassFileProviderFactory(
                        classpath?.map(File::toPath) ?: emptyList()
                    )
                        .also { closer.register(it) },
                    coreLibDesugarConfig = parameters.libConfiguration.orNull,
                    enableApiModeling = parameters.enableApiModeling.get(),
                    messageReceiver = MessageReceiverImpl(
                        parameters.errorFormat.get(),
                        LoggerFactory.getLogger(BaseDexingTransform::class.java)
                    )
                )
            )

            ClassFileInputs.fromPath(inputDirOrJar.toPath()).use { classFileInput ->
                classFileInput.entries { rootPath, relativePath ->
                    inputFilter(rootPath.toFile(), relativePath)
                }.use { classesInput ->
                    d8DexBuilder.convert(
                        classesInput,
                        dexOutputDir.toPath(),
                        globalSyntheticsOutputDir?.toPath(),
                        desugarGraph
                    )
                }
            }
        }
    }
}

/**
 * Desugaring graph used for incremental dexing. It contains class files and their dependencies.
 *
 * This graph handles files with absolute paths. To make it relocatable, it requires that all files
 * in the graph share one single root directory ([rootDir]) so that they can be converted to
 * relative paths.
 *
 * Internally, this graph maintains a [relocatableDesugarGraph] containing relative paths of the
 * files. When writing this graph to disk, we will write the [relocatableDesugarGraph].
 */
private class DesugarGraph(

    /** The root directory that is shared among all files in the desugaring graph. */
    private val rootDir: File,

    /** The relocatable desugaring graph, which contains relative paths of the files. */
    private val relocatableDesugarGraph: MutableDependencyGraph<File> = MutableDependencyGraph()

) : DependencyGraphUpdater<File> {

    override fun addEdge(dependent: File, dependency: File) {
        relocatableDesugarGraph.addEdge(
            dependent.relativeTo(rootDir),
            dependency.relativeTo(rootDir)
        )
    }

    fun removeNode(nodeToRemove: File) {
        relocatableDesugarGraph.removeNode(nodeToRemove.relativeTo(rootDir))
    }

    fun getAllDependents(nodes: Collection<File>): Set<File> {
        val relativePaths = nodes.mapTo(mutableSetOf()) { it.relativeTo(rootDir) }

        val dependents = relocatableDesugarGraph.getAllDependents(relativePaths)

        return dependents.mapTo(mutableSetOf()) { rootDir.resolve(it) }
    }

    fun write(relocatableDesugarGraphFile: File) {
        writeDesugarGraph(relocatableDesugarGraphFile, relocatableDesugarGraph)
    }

    companion object {

        fun read(relocatableDesugarGraphFile: File, rootDir: File): DesugarGraph {
            return DesugarGraph(
                rootDir = rootDir,
                relocatableDesugarGraph = readDesugarGraph(relocatableDesugarGraphFile)
            )
        }
    }
}

@CacheableTransform
abstract class DexingNoClasspathTransform : BaseDexingTransform<BaseDexingTransform.Parameters>() {

    override fun computeClasspathFiles() = null
}

@CacheableTransform
abstract class DexingWithClasspathTransform : BaseDexingTransform<BaseDexingTransform.Parameters>() {

    // Use @CompileClasspath instead of @Classpath because non-ABI changes on the classpath do not
    // impact dexing/desugaring of the artifact.
    @get:CompileClasspath
    @get:InputArtifactDependencies
    abstract val classpath: FileCollection

    override fun computeClasspathFiles() = classpath.files.toList()
}

fun getDexingArtifactConfigurations(components: Collection<ComponentCreationConfig>): Set<DexingArtifactConfiguration> {
    return components
        .filterIsInstance<ApkCreationConfig>()
        .map { getDexingArtifactConfiguration(it) }.toSet()
}

fun getDexingArtifactConfiguration(creationConfig: ApkCreationConfig): DexingArtifactConfiguration {
    return DexingArtifactConfiguration(
        minSdk = creationConfig.dexingCreationConfig.minSdkVersionForDexing.getFeatureLevel(),
        isDebuggable = creationConfig.debuggable,
        enableDesugaring =
            creationConfig.dexingCreationConfig.java8LangSupportType == Java8LangSupport.D8,
        enableCoreLibraryDesugaring = creationConfig.dexingCreationConfig.isCoreLibraryDesugaringEnabled,
        asmTransformedVariant =
            if (creationConfig.instrumentationCreationConfig?.dependenciesClassesAreInstrumented == true) {
                creationConfig.name
            } else {
                null
            },
        useJacocoTransformInstrumentation = creationConfig.useJacocoTransformInstrumentation,
        enableGlobalSynthetics = creationConfig.enableGlobalSynthetics,
        enableApiModeling = creationConfig.enableApiModeling
    )
}

data class DexingArtifactConfiguration(
    private val minSdk: Int,
    private val isDebuggable: Boolean,
    private val enableDesugaring: Boolean,
    private val enableCoreLibraryDesugaring: Boolean,
    private val asmTransformedVariant: String?,
    private val useJacocoTransformInstrumentation: Boolean,
    private val enableGlobalSynthetics: Boolean,
    private val enableApiModeling: Boolean,
) {

    // If we want to do desugaring and our minSdk (or the API level of the device we're deploying
    // to) is lower than N then we need a classpath in order to properly do the desugaring.
    private val needsClasspath = enableDesugaring && minSdk < AndroidVersion.VersionCodes.N

    fun registerTransform(
        projectName: String,
        dependencyHandler: DependencyHandler,
        bootClasspath: FileCollection,
        libConfiguration: Provider<String>,
        errorFormat: SyncOptions.ErrorFormatMode,
        disableIncrementalDexing: Boolean
    ) {
        dependencyHandler.registerTransform(getTransformClass()) { spec ->
            spec.parameters { parameters ->
                parameters.projectName.set(projectName)
                parameters.minSdkVersion.set(minSdk)
                parameters.debuggable.set(isDebuggable)
                parameters.enableDesugaring.set(enableDesugaring)
                // bootclasspath is required by d8 to do API conversion for library desugaring
                if (needsClasspath || enableCoreLibraryDesugaring) {
                    parameters.bootClasspath.from(bootClasspath)
                }
                parameters.errorFormat.set(errorFormat)
                if (enableCoreLibraryDesugaring) {
                    parameters.libConfiguration.set(libConfiguration)
                }
                parameters.enableGlobalSynthetics.set(enableGlobalSynthetics)
                parameters.enableApiModeling.set(enableApiModeling)
            }
            // There are 2 transform flows for DEX:
            //   1. (JACOCO_)CLASSES_DIR -> (JACOCO_)CLASSES -> DEX
            //   2. (JACOCO_)CLASSES_JAR -> (JACOCO_)CLASSES -> DEX
            //
            // For incremental dexing, when requesting DEX the consumer will indicate a
            // preference for CLASSES_DIR over CLASSES_JAR (see DexMergingTask), otherwise
            // Gradle will select CLASSES_JAR by default.
            //
            // However, there could be an issue if CLASSES_DIR is selected: For Java libraries
            // using Kotlin, CLASSES_DIR has two separate directories: one for compiled Java
            // classes and one for compiled Kotlin classes. Classes in one directory may
            // reference classes in the other directory, but each directory is transformed to
            // DEX independently. Therefore, if dexing requires a classpath (desugaring is
            // enabled and minSdk < 24), desugaring may not work correctly.
            //
            // Android libraries do not have this issue, as their CLASSES_DIR is one directory
            // containing both Java and Kotlin classes.
            //
            // Therefore, to ensure correctness in all cases, we transform CLASSES to DEX only
            // when dexing does not require a classpath, and it is not for main and androidTest
            // components in dynamic feature module(b/246326007). Otherwise, we transform
            // CLASSES_JAR to DEX directly so that CLASSES_DIR will not be selected.
            //
            // In the case that the JacocoTransform is executed, the Jacoco equivalent artifact is
            // used. These artifacts are the same as CLASSES, CLASSES_JAR and ASM_INSTRUMENTED_JARS,
            // but they have been offline instrumented by Jacoco and include Jacoco dependencies.
            val inputArtifact: AndroidArtifacts.ArtifactType =
                if (useJacocoTransformInstrumentation) {
                    when {
                        asmTransformedVariant != null ->
                            AndroidArtifacts.ArtifactType.JACOCO_ASM_INSTRUMENTED_JARS
                        !needsClasspath && !disableIncrementalDexing ->
                            AndroidArtifacts.ArtifactType.JACOCO_CLASSES
                        else ->
                            AndroidArtifacts.ArtifactType.JACOCO_CLASSES_JAR
                    }
                } else {
                    when {
                        asmTransformedVariant != null ->
                            AndroidArtifacts.ArtifactType.ASM_INSTRUMENTED_JARS
                        !needsClasspath && !disableIncrementalDexing ->
                            AndroidArtifacts.ArtifactType.CLASSES
                        else ->
                            AndroidArtifacts.ArtifactType.CLASSES_JAR
                    }
                }
            spec.from.attribute(
                ARTIFACT_TYPE_ATTRIBUTE,
                inputArtifact.type
            )

            if (enableGlobalSynthetics) {
                spec.to.attribute(
                    ARTIFACT_TYPE_ATTRIBUTE,
                    AndroidArtifacts.ArtifactType.D8_OUTPUTS.type
                )
            } else {
                spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, AndroidArtifacts.ArtifactType.DEX.type)
            }

            getAttributes().apply {
                addAttributesToContainer(spec.from)
                addAttributesToContainer(spec.to)
            }
        }
    }

    private fun getTransformClass(): Class<out BaseDexingTransform<BaseDexingTransform.Parameters>> {
        return if (needsClasspath) {
            DexingWithClasspathTransform::class.java
        } else {
            DexingNoClasspathTransform::class.java
        }
    }

    fun getAttributes(): AndroidAttributes {
        return AndroidAttributes(
            mapOf(
                ATTR_MIN_SDK to minSdk.toString(),
                ATTR_IS_DEBUGGABLE to isDebuggable.toString(),
                ATTR_ENABLE_DESUGARING to enableDesugaring.toString(),
                ATTR_ENABLE_JACOCO_INSTRUMENTATION to useJacocoTransformInstrumentation.toString(),
                ATTR_ASM_TRANSFORMED_VARIANT to (asmTransformedVariant ?: "NONE")
            )
        )
    }
}

val ATTR_MIN_SDK: Attribute<String> = Attribute.of("dexing-min-sdk", String::class.java)
val ATTR_IS_DEBUGGABLE: Attribute<String> =
    Attribute.of("dexing-is-debuggable", String::class.java)
val ATTR_ENABLE_DESUGARING: Attribute<String> =
    Attribute.of("dexing-enable-desugaring", String::class.java)
val ATTR_ENABLE_JACOCO_INSTRUMENTATION: Attribute<String> =
    Attribute.of("dexing-enable-jacoco-instrumentation", String::class.java)

private val logger = LoggerWrapper.getLogger(BaseDexingTransform::class.java)

private const val DESUGAR_GRAPH_FILE_NAME = "desugar_graph.bin"
