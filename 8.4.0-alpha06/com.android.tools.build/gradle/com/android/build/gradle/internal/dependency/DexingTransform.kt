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

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.dexing.readDesugarGraph
import com.android.build.gradle.internal.dexing.writeDesugarGraph
import com.android.build.gradle.internal.errors.MessageReceiverImpl
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.Java8LangSupport
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.SyncOptions.ErrorFormatMode
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
        val errorFormat: Property<ErrorFormatMode>
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

/**
 * Dexing transform which uses the full classpath. This classpath consists of all external artifacts
 * ([com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.EXTERNAL])
 * in addition to the input artifact's dependencies provided by Gradle through
 * [org.gradle.api.artifacts.transform.InputArtifactDependencies].
 */
@CacheableTransform
abstract class DexingWithFullClasspathTransform :
    BaseDexingTransform<DexingWithFullClasspathTransform.Parameters>() {

    @get:CompileClasspath
    @get:InputArtifactDependencies
    abstract val inputArtifactDependencies: FileCollection

    interface Parameters : BaseDexingTransform.Parameters {

        @get:CompileClasspath
        val externalArtifacts: ConfigurableFileCollection
    }

    override fun computeClasspathFiles() =
        inputArtifactDependencies.files.toList() + parameters.externalArtifacts.files
}

object DexingRegistration {

    /** Parameters that are shared across all [ComponentCreationConfig]s. */
    class ComponentAgnosticParameters(
        val projectName: String,
        val dependencyHandler: DependencyHandler,
        val bootClasspath: ConfigurableFileCollection,
        val errorFormat: ErrorFormatMode,
        val libConfiguration: Provider<String>,
        val disableIncrementalDexing: Boolean,
        val components: List<ComponentCreationConfig>
    )

    /**
     * Parameters that are specific to a given [ComponentCreationConfig].
     *
     * Note: This class is a data class so that we can identify equivalent instances of this class
     * (see [registerTransforms]).
     *
     * IMPORTANT: The properties of this class must be of primitive types (e.g., [Boolean], [Int],
     * [String]) because the [getAttributes] method relies on [toString], and the implementation of
     * [toString] on non-primitive types are not well-defined and subject to change (i.e., it can't
     * be used to uniquely represent an object).
     */
    data class ComponentSpecificParameters(
        val minSdkVersion: Int,
        val debuggable: Boolean,
        val enableCoreLibraryDesugaring: Boolean,
        val enableGlobalSynthetics: Boolean,
        val enableApiModeling: Boolean,
        val dependenciesClassesAreInstrumented: Boolean,
        val asmTransformComponent: String?, // Not-null iff dependenciesClassesAreInstrumented == true
        val useJacocoTransformInstrumentation: Boolean,
        val enableDesugaring: Boolean,
        val needsClasspath: Boolean,
        val useFullClasspath: Boolean,
        val componentIfUsingFullClasspath: String? // Not-null iff useFullClasspath == true
    ) {

        constructor(creationConfig: ApkCreationConfig) : this(
            minSdkVersion = creationConfig.dexing.minSdkVersionForDexing,
            debuggable = creationConfig.debuggable,
            enableCoreLibraryDesugaring = creationConfig.dexing.isCoreLibraryDesugaringEnabled,
            enableGlobalSynthetics = creationConfig.enableGlobalSynthetics,
            enableApiModeling = creationConfig.enableApiModeling,
            dependenciesClassesAreInstrumented = creationConfig.instrumentationCreationConfig?.dependenciesClassesAreInstrumented == true,
            asmTransformComponent = creationConfig.name.takeIf { creationConfig.instrumentationCreationConfig?.dependenciesClassesAreInstrumented == true },
            useJacocoTransformInstrumentation = creationConfig.useJacocoTransformInstrumentation,
            enableDesugaring = needsDesugaring(creationConfig),
            needsClasspath = needsClasspath(creationConfig),
            useFullClasspath = useFullClasspath(creationConfig),
            componentIfUsingFullClasspath = creationConfig.name.takeIf { useFullClasspath(creationConfig) }
        )

        companion object {

            private fun needsDesugaring(creationConfig: ApkCreationConfig): Boolean =
                creationConfig.dexing.java8LangSupportType == Java8LangSupport.D8

            private fun needsClasspath(creationConfig: ApkCreationConfig): Boolean =
                needsDesugaring(creationConfig) &&
                        creationConfig.dexing.minSdkVersionForDexing < 24

            private fun useFullClasspath(creationConfig: ApkCreationConfig): Boolean =
                needsClasspath(creationConfig) &&
                        creationConfig.services.projectOptions.get(BooleanOption.USE_FULL_CLASSPATH_FOR_DEXING_TRANSFORM)

        }

        /**
         * Returns [AndroidAttributes] that uniquely represent the contents of this object.
         *
         * These attributes will be used when registering the transforms and when consuming the
         * artifacts to ensure correct artifact production/consumption.
         */
        fun getAttributes() =
            AndroidAttributes(
                Attribute.of("dexing-component-attributes", String::class.java) to this.toString()
            ) + asmTransformComponent?.let {
                // When asmTransformComponent != null, the consumed artifacts have the attribute
                // below, so we need to specify it here as well to allow unambiguous artifact
                // selection.
                AndroidAttributes(AsmClassesTransform.ATTR_ASM_TRANSFORMED_VARIANT to asmTransformComponent)
            }
    }

    fun registerTransforms(
        components: List<ComponentCreationConfig>,
        parameters: ComponentAgnosticParameters
    ) {
        // To improve performance and avoid duplicate registrations, instead of setting up one
        // transform per component, we will set up one transform per group of equivalent components
        // (components whose registered transforms would be identical if registered separately).
        components.filterIsInstance<ApkCreationConfig>()
            .mapTo(linkedSetOf()) { ComponentSpecificParameters(it) }
            .forEach {
                registerTransform(parameters, it)
            }
    }

    fun registerTransform(
        allComponents: ComponentAgnosticParameters,
        component: ComponentSpecificParameters
    ) {
        @Suppress("UNCHECKED_CAST")
        val transformClass = when {
            !component.needsClasspath -> DexingNoClasspathTransform::class.java
            !component.useFullClasspath -> DexingWithClasspathTransform::class.java
            else -> DexingWithFullClasspathTransform::class.java as Class<BaseDexingTransform<BaseDexingTransform.Parameters>>
        }

        allComponents.dependencyHandler.registerTransform(transformClass) { spec ->
            spec.parameters.apply {
                projectName.set(allComponents.projectName)
                minSdkVersion.set(component.minSdkVersion)
                debuggable.set(component.debuggable)
                enableDesugaring.set(component.enableDesugaring)
                // bootclasspath is required by d8 to do API conversion for library desugaring
                if (component.needsClasspath || component.enableCoreLibraryDesugaring) {
                    bootClasspath.from(allComponents.bootClasspath)
                }
                errorFormat.set(allComponents.errorFormat)
                if (component.enableCoreLibraryDesugaring) {
                    libConfiguration.set(allComponents.libConfiguration)
                }
                enableGlobalSynthetics.set(component.enableGlobalSynthetics)
                enableApiModeling.set(component.enableApiModeling)
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
            val inputArtifactType: AndroidArtifacts.ArtifactType =
                if (component.useJacocoTransformInstrumentation) {
                    when {
                        component.dependenciesClassesAreInstrumented ->
                            AndroidArtifacts.ArtifactType.JACOCO_ASM_INSTRUMENTED_JARS
                        !component.needsClasspath && !allComponents.disableIncrementalDexing ->
                            AndroidArtifacts.ArtifactType.JACOCO_CLASSES
                        else ->
                            AndroidArtifacts.ArtifactType.JACOCO_CLASSES_JAR
                    }
                } else {
                    when {
                        component.dependenciesClassesAreInstrumented ->
                            AndroidArtifacts.ArtifactType.ASM_INSTRUMENTED_JARS
                        !component.needsClasspath && !allComponents.disableIncrementalDexing ->
                            AndroidArtifacts.ArtifactType.CLASSES
                        else ->
                            AndroidArtifacts.ArtifactType.CLASSES_JAR
                    }
                }

            if (component.useFullClasspath) {
                val componentName = component.componentIfUsingFullClasspath!!
                val creationConfig = allComponents.components.first { it.name == componentName }
                (spec.parameters as DexingWithFullClasspathTransform.Parameters).externalArtifacts.from(
                    creationConfig.variantDependencies.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.EXTERNAL,
                        inputArtifactType
                    ).artifactFiles
                )
            }

            spec.from.attribute(ARTIFACT_TYPE_ATTRIBUTE, inputArtifactType.type)
            if (component.enableGlobalSynthetics) {
                spec.to.attribute(
                    ARTIFACT_TYPE_ATTRIBUTE,
                    AndroidArtifacts.ArtifactType.D8_OUTPUTS.type
                )
            } else {
                spec.to.attribute(ARTIFACT_TYPE_ATTRIBUTE, AndroidArtifacts.ArtifactType.DEX.type)
            }

            component.getAttributes().apply {
                addAttributesToContainer(spec.from)
                addAttributesToContainer(spec.to)
            }
        }
    }

}

private val logger = LoggerWrapper.getLogger(BaseDexingTransform::class.java)

private const val DESUGAR_GRAPH_FILE_NAME = "desugar_graph.bin"
