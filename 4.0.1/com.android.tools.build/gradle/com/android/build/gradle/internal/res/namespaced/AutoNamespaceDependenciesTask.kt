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

package com.android.build.gradle.internal.res.namespaced

import android.databinding.tool.util.Preconditions
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.res.Aapt2CompileRunnable
import com.android.build.gradle.internal.res.getAapt2FromMavenAndVersion
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.internal.services.Aapt2DaemonBuildService
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.services.getAapt2DaemonBuildService
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.toImmutableList
import com.android.build.gradle.internal.utils.toImmutableMap
import com.android.build.gradle.options.SyncOptions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.tools.build.apkzlib.zip.StoredEntryType
import com.android.tools.build.apkzlib.zip.ZFile
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask

/**
 * Rewrites the non-namespaced AAR dependencies of this module to be namespaced.
 *
 * 1. Build a model of where the resources of non-namespaced AARs have come from.
 * 2. Rewrites the classes.jar to be namespaced
 * 3. Rewrites the manifest to use namespaced resource references
 * 4. Rewrites the resources themselves to use namespaced references, and compiles
 *    them in to a static library.
 */
@CacheableTask
abstract class AutoNamespaceDependenciesTask : NonIncrementalTask() {

    private lateinit var rFiles: ArtifactCollection
    private lateinit var nonNamespacedManifests: ArtifactCollection
    private lateinit var jarFiles: ArtifactCollection
    private lateinit var publicFiles: ArtifactCollection
    private lateinit var externalNotNamespacedResources: ArtifactCollection
    private lateinit var externalResStaticLibraries: ArtifactCollection
    // Don't need to mark this as input as it's already covered by the other inputs
    private lateinit var dependencies: ResolvableDependencies

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    lateinit var androidJar: Provider<File>
        private set

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    fun getRDefFiles(): FileCollection = rFiles.artifactFiles

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    fun getManifestsFiles(): FileCollection = nonNamespacedManifests.artifactFiles

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    fun getClassesJarFiles(): FileCollection = jarFiles.artifactFiles

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    fun getPublicFilesArtifactFiles(): FileCollection = publicFiles.artifactFiles

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    fun getNonNamespacedResourcesFiles(): FileCollection =
        externalNotNamespacedResources.artifactFiles

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    fun getStaticLibraryDependenciesFiles(): FileCollection =
        externalResStaticLibraries.artifactFiles

    @get:Input
    lateinit var aapt2Version: String
        private set
    @get:Internal
    abstract val aapt2FromMaven: ConfigurableFileCollection

    @get:Internal
    @VisibleForTesting internal var log: Logger? = null

    @get:Internal
    abstract val aapt2DaemonBuildService: Property<Aapt2DaemonBuildService>

    /**
     * Reading the R files and building symbol tables is costly, and is wasteful to repeat,
     * hence, use a LoadingCache.
     */
    private var symbolTablesCache: LoadingCache<File, SymbolTable> = CacheBuilder.newBuilder()
        .build(
            object : CacheLoader<File, SymbolTable>() {
                override fun load(rDefFile: File): SymbolTable {
                    return SymbolIo.readRDef(rDefFile.toPath())
                }
            }
        )

    @get:OutputFile abstract val outputClassesJar: RegularFileProperty
    @get:OutputFile abstract val outputRClassesJar: RegularFileProperty
    @get:OutputDirectory abstract val outputRewrittenManifests: DirectoryProperty
    @get:OutputDirectory abstract val outputStaticLibraries: DirectoryProperty
    @get:OutputDirectory lateinit var intermediateDirectory: File private set

    private lateinit var errorFormatMode: SyncOptions.ErrorFormatMode

    override fun doTaskAction() = autoNamespaceDependencies()

    private fun autoNamespaceDependencies(
        forkJoinPool: ForkJoinPool = sharedForkJoinPool,
        aapt2FromMaven: FileCollection = this.aapt2FromMaven,
        dependencies: ResolvableDependencies = this.dependencies,
        rFiles: ArtifactCollection = this.rFiles,
        jarFiles: ArtifactCollection = this.jarFiles,
        manifests: ArtifactCollection = this.nonNamespacedManifests,
        notNamespacedResources: ArtifactCollection = this.externalNotNamespacedResources,
        staticLibraryDependencies: ArtifactCollection = this.externalResStaticLibraries,
        intermediateDirectory: File = this.intermediateDirectory,
        outputStaticLibraries: File = this.outputStaticLibraries.get().asFile,
        outputClassesJar: File = this.outputClassesJar.get().asFile,
        outputRClassesJar: File = this.outputRClassesJar.get().asFile,
        outputManifests: File = this.outputRewrittenManifests.get().asFile,
        publicFiles: ArtifactCollection = this.publicFiles
    ) {

        try {
            val fileMaps =
                ImmutableMap.builder<ArtifactType, ImmutableMap<String, ImmutableCollection<File>>>()
                    .put(ArtifactType.DEFINED_ONLY_SYMBOL_LIST, rFiles.toMap())
                    .put(ArtifactType.NON_NAMESPACED_CLASSES, jarFiles.toMap())
                    .put(ArtifactType.NON_NAMESPACED_MANIFEST, manifests.toMap())
                    .put(ArtifactType.ANDROID_RES, notNamespacedResources.toMap())
                    .put(ArtifactType.RES_STATIC_LIBRARY, staticLibraryDependencies.toMap())
                    .put(ArtifactType.PUBLIC_RES, publicFiles.toMap())
                    .build()

            val graph = DependenciesGraph.create(
                dependencies,
                fileMaps
            )

            val rewrittenResources = File(intermediateDirectory, "namespaced_res")
            val rewrittenClasses = File(intermediateDirectory, "namespaced_classes")
            val rewrittenRClasses = File(intermediateDirectory, "namespaced_r_classes")

            val rewrittenResourcesMap = namespaceDependencies(
                graph = graph,
                forkJoinPool = forkJoinPool,
                outputRewrittenClasses = rewrittenClasses,
                outputRClasses = rewrittenRClasses,
                outputManifests = outputManifests,
                outputResourcesDir = rewrittenResources
            )

            // Jar all the classes into two JAR files - one for namespaced classes, one for R classes.
            jarOutputs(outputClassesJar, rewrittenClasses)
            jarOutputs(outputRClassesJar, rewrittenRClasses)

            val aapt2ServiceKey = aapt2DaemonBuildService.get()
                .registerAaptService(aapt2FromMaven.singleFile, logger = LoggerWrapper(logger))

            val outputCompiledResources = File(intermediateDirectory, "compiled_namespaced_res")
            // compile the rewritten resources
            val compileMap =
                compile(
                    rewrittenResourcesMap = rewrittenResourcesMap,
                    aapt2ServiceKey = aapt2ServiceKey,
                    forkJoinPool = forkJoinPool,
                    outputDirectory = outputCompiledResources
                )

            // then link them in to static libraries.
            val nonNamespacedDependenciesLinker = NonNamespacedDependenciesLinker(
                graph = graph,
                compiled = compileMap,
                outputStaticLibrariesDirectory = outputStaticLibraries,
                intermediateDirectory = intermediateDirectory,
                pool = forkJoinPool,
                aapt2ServiceKey = aapt2ServiceKey,
                errorFormatMode = errorFormatMode,
                androidJarPath = androidJar.get().absolutePath
            )
            nonNamespacedDependenciesLinker.link()
        } finally {
            symbolTablesCache.invalidateAll()
        }
    }

    @VisibleForTesting
    internal fun namespaceDependencies(
        graph: DependenciesGraph,
        forkJoinPool: ForkJoinPool,
        outputRewrittenClasses: File,
        outputRClasses: File,
        outputManifests: File,
        outputResourcesDir: File
    ): Map<DependenciesGraph.Node, File> {
        FileUtils.cleanOutputDir(outputRewrittenClasses)
        FileUtils.cleanOutputDir(outputRClasses)
        FileUtils.cleanOutputDir(outputManifests)
        FileUtils.cleanOutputDir(outputResourcesDir)


        // The rewriting works per node, since for rewriting a library the only files from its
        // dependencies we need are their R-def.txt files, which were already generated by the
        // [LibraryDefinedSymbolTableTransform].
        // TODO: do this all as one action to interleave work.
        val rewrittenResources = ImmutableMap.builder<DependenciesGraph.Node, File>()

        val tasks = mutableListOf<ForkJoinTask<*>>()
        for (dependency in graph.allNodes) {
            val outputResources = if (dependency.getFile(ArtifactType.ANDROID_RES) != null) {
                File(
                    outputResourcesDir,
                    dependency.sanitizedName
                )
            } else {
                null
            }
            outputResources?.apply { rewrittenResources.put(dependency, outputResources) }
            tasks.add(forkJoinPool.submit {
                namespaceDependency(
                    dependency,
                    outputRewrittenClasses, outputRClasses, outputManifests, outputResources
                )
            })
        }
        for (task in tasks) {
            task.get()
        }
        tasks.clear()
        return rewrittenResources.build()
    }

    private fun jarOutputs(outputJar: File, inputDirectory: File) {
        ZFile(outputJar, ZFileOptions(), false).use { jar ->
            Files.walk(inputDirectory.toPath()).use { paths ->
                paths.filter { p -> p.toFile().isFile}.forEach { it ->
                    ZFile(it.toFile(), ZFileOptions(), true).use { classesJar ->
                        classesJar.entries().forEach { entry ->
                            val name = entry.centralDirectoryHeader.name
                            if (entry.type == StoredEntryType.FILE && name.endsWith(".class")) {
                                jar.add(name, entry.open())
                            }
                        }
                    }
                }
            }
        }
    }

    private fun namespaceDependency(
        dependency: DependenciesGraph.Node,
        outputClassesDirectory: File,
        outputRClassesDirectory: File,
        outputManifests: File,
        outputResourcesDirectory: File?
    ) {
        val inputClasses = dependency.getFiles(ArtifactType.NON_NAMESPACED_CLASSES)
        val manifest = dependency.getFile(ArtifactType.NON_NAMESPACED_MANIFEST)
        val resources = dependency.getFile(ArtifactType.ANDROID_RES)
        val publicTxt = dependency.getFile(ArtifactType.PUBLIC_RES)

        // Only convert external nodes and non-namespaced libraries. Already namespaced libraries
        // and JAR files can be present in the graph, but they will not contain the
        // NON_NAMESPACED_CLASSES artifacts. Only try to rewrite non-namespaced libraries' classes.
        if (dependency.id !is ProjectComponentIdentifier && inputClasses != null) {
            Preconditions.checkNotNull(
                manifest,
                "Manifest missing for library $dependency")

            // The rewriting algorithm uses ordered symbol tables, with this library's table at the
            // top of the list. It looks up resources starting from the top of the list, trying to
            // find where the references resource was defined (or overridden), closest to the root
            // (this node) in the dependency graph.
            val symbolTables = getSymbolTables(dependency)
            logger.info("Started rewriting $dependency")
            val rewriter = NamespaceRewriter(symbolTables, log ?: logger)

            // Brittle, relies on the AAR expansion logic that makes sure all jars have unique names
            try {
                inputClasses.forEach {
                    val out = File(
                        outputClassesDirectory,
                        "namespaced-${dependency.sanitizedName}-${it.name}"
                    )
                    rewriter.rewriteJar(it, out)
                }
            } catch (e: Exception) {
                throw IOException("Failed to transform jar + ${dependency.getTransitiveFiles(ArtifactType.DEFINED_ONLY_SYMBOL_LIST)}", e)
            }
            rewriter.rewriteManifest(
                manifest!!.toPath(),
                outputManifests.toPath().resolve("${dependency.sanitizedName}_AndroidManifest.xml"))
            if (resources != null) {
                rewriter.rewriteAarResources(
                    resources.toPath(),
                    outputResourcesDirectory!!.toPath()
                )

                rewriter.generatePublicFile(publicTxt, outputResourcesDirectory.toPath())
            }

            logger.info("Finished rewriting $dependency")

            // Also generate fake R classes for compilation.
            rewriter.writeRClass(
                File(
                    outputRClassesDirectory,
                    "namespaced-${dependency.sanitizedName}-R.jar"
                ).toPath()
            )
        }
    }

    private fun compile(
        rewrittenResourcesMap: Map<DependenciesGraph.Node, File>,
        aapt2ServiceKey: Aapt2DaemonServiceKey,
        forkJoinPool: ForkJoinPool,
        outputDirectory: File
    ): Map<DependenciesGraph.Node, File> {
        val compiled = ImmutableMap.builder<DependenciesGraph.Node, File>()
        val tasks = mutableListOf<ForkJoinTask<*>>()

        rewrittenResourcesMap.forEach { node, rewrittenResources ->
            val nodeOutputDirectory = File(outputDirectory, node.sanitizedName)
            compiled.put(node, nodeOutputDirectory)
            Files.createDirectories(nodeOutputDirectory.toPath())
            for (resConfigurationDir in rewrittenResources.listFiles()) {
                for (resourceFile in resConfigurationDir.listFiles()) {
                    val request = CompileResourceRequest(
                        inputFile = resourceFile,
                        outputDirectory = nodeOutputDirectory
                    )
                    val params = Aapt2CompileRunnable.Params(
                        aapt2ServiceKey,
                        listOf(request),
                        errorFormatMode
                    )
                    tasks.add(forkJoinPool.submit(Aapt2CompileRunnable(params)))
                }
            }
        }
        for (task in tasks) {
            task.get()
        }
        return compiled.build()
    }



    private fun getSymbolTables(node: DependenciesGraph.Node): ImmutableList<SymbolTable> {
        val builder = ImmutableList.builder<SymbolTable>()
        for (rDefFile in node.getTransitiveFiles(ArtifactType.DEFINED_ONLY_SYMBOL_LIST)) {
            builder.add(symbolTablesCache.getUnchecked(rDefFile))
        }
        return builder.build()
    }

    private fun ArtifactCollection.toMap(): ImmutableMap<String, ImmutableCollection<File>> =
        HashMap<String, MutableCollection<File>>().apply {
            for (artifact in artifacts) {
                val key = artifact.id.componentIdentifier.displayName
                getOrPut(key) { mutableListOf() }.add(artifact.file)
            }
        }.toImmutableMap { it.toImmutableList() }

    class CreationAction(variantScope: VariantScope) :
        VariantTaskCreationAction<AutoNamespaceDependenciesTask>(variantScope) {

        override val name: String
            get() = variantScope.getTaskName("autoNamespace", "Dependencies")
        override val type: Class<AutoNamespaceDependenciesTask>
            get() = AutoNamespaceDependenciesTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<out AutoNamespaceDependenciesTask>) {
            super.handleProvider(taskProvider)
            variantScope.artifacts.producesFile(
                InternalArtifactType.NAMESPACED_CLASSES_JAR,
                taskProvider,
                AutoNamespaceDependenciesTask::outputClassesJar,
                "namespaced-classes.jar"
            )

            variantScope.artifacts.producesFile(
                InternalArtifactType.COMPILE_ONLY_NAMESPACED_DEPENDENCIES_R_JAR,
                taskProvider,
                AutoNamespaceDependenciesTask::outputRClassesJar,
                "namespaced-R.jar"
            )

            variantScope.artifacts.producesDir(
                InternalArtifactType.RES_CONVERTED_NON_NAMESPACED_REMOTE_DEPENDENCIES,
                taskProvider,
                AutoNamespaceDependenciesTask::outputStaticLibraries
            )

            variantScope.artifacts.producesDir(
                InternalArtifactType.NAMESPACED_MANIFESTS,
                taskProvider,
                AutoNamespaceDependenciesTask::outputRewrittenManifests
            )

        }

        override fun configure(task: AutoNamespaceDependenciesTask) {
            super.configure(task)

            task.rFiles = variantScope.getArtifactCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                ArtifactScope.EXTERNAL,
                ArtifactType.DEFINED_ONLY_SYMBOL_LIST
            )

            task.jarFiles = variantScope.getArtifactCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                ArtifactScope.EXTERNAL,
                ArtifactType.NON_NAMESPACED_CLASSES
            )

            task.nonNamespacedManifests = variantScope.getArtifactCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                ArtifactScope.EXTERNAL,
                ArtifactType.NON_NAMESPACED_MANIFEST
            )

            task.publicFiles = variantScope.getArtifactCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                ArtifactScope.EXTERNAL,
                ArtifactType.PUBLIC_RES
            )

            task.dependencies =
                variantScope.variantData.variantDependency.runtimeClasspath.incoming

            task.externalNotNamespacedResources = variantScope.getArtifactCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                ArtifactScope.EXTERNAL,
                ArtifactType.ANDROID_RES
            )

            task.externalResStaticLibraries = variantScope.getArtifactCollection(
                ConsumedConfigType.RUNTIME_CLASSPATH,
                ArtifactScope.EXTERNAL,
                ArtifactType.RES_STATIC_LIBRARY
            )

            task.intermediateDirectory = variantScope.getIncrementalDir(name)

            val (aapt2FromMaven, aapt2Version) = getAapt2FromMavenAndVersion(variantScope.globalScope)
            task.aapt2FromMaven.from(aapt2FromMaven)
            task. aapt2Version = aapt2Version
            task.androidJar = variantScope.globalScope.sdkComponents.androidJarProvider

            task.errorFormatMode = SyncOptions.getErrorFormatMode(
                variantScope.globalScope.projectOptions
            )
            task.aapt2DaemonBuildService.set(getAapt2DaemonBuildService(task.project))
        }
    }

    companion object {
        val sharedForkJoinPool: ForkJoinPool by lazy {
            ForkJoinPool(
                Math.max(
                    1,
                    Math.min(8, Runtime.getRuntime().availableProcessors() / 2)
                )
            )
        }
    }
}
