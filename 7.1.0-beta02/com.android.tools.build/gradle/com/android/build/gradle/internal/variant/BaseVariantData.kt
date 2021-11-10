/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.build.gradle.internal.variant

import com.android.build.VariantOutput
import com.android.build.api.artifact.impl.ArtifactsImpl
import com.android.build.api.component.ComponentIdentity
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.core.VariantSources
import com.android.build.gradle.internal.dependency.VariantDependencies
import com.android.build.gradle.internal.dsl.Splits
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.JAVA_RES
import com.android.build.gradle.internal.scope.MutableTaskContainer
import com.android.build.gradle.internal.services.VariantPropertiesApiServices
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.google.common.base.MoreObjects
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.Collections
import java.util.HashSet

/** Base data about a variant.  */
abstract class BaseVariantData(
    // Variant specific Data
    protected val componentIdentity: ComponentIdentity,
    protected val variantDslInfo: VariantDslInfo<*>,
    val variantDependencies: VariantDependencies,
    protected val variantSources: VariantSources,
    protected val paths: VariantPathHelper,
    protected val artifacts: ArtifactsImpl,
    protected val services: VariantPropertiesApiServices,
    // Global Data
    @get:Deprecated("Use {@link ComponentPropertiesImpl#getGlobalScope()} ") val globalScope: GlobalScope,
    val taskContainer: MutableTaskContainer
) {

    // Storage for Old Public API
    val extraGeneratedSourceFolders: MutableList<File> = mutableListOf()
    internal var extraGeneratedSourceFileTrees: MutableList<ConfigurableFileTree>? = null
    internal var externalAptJavaOutputFileTrees: MutableList<ConfigurableFileTree>? = null
    val extraGeneratedResFolders: ConfigurableFileCollection = services.fileCollection()
    private var preJavacGeneratedBytecodeMap: MutableMap<Any, FileCollection>? = null
    private var preJavacGeneratedBytecodeLatest: FileCollection = services.fileCollection()
    val allPreJavacGeneratedBytecode: ConfigurableFileCollection = services.fileCollection()
    val allPostJavacGeneratedBytecode: ConfigurableFileCollection = services.fileCollection()
    private var rawAndroidResources: FileCollection? = null

    private lateinit var densityFilters: Set<String>
    private lateinit var languageFilters: Set<String>
    private lateinit var abiFilters: Set<String>

    /**
     * If true, variant outputs will be considered signed. Only set if you manually set the outputs
     * to point to signed files built by other tasks.
     */
    @JvmField
    var outputsAreSigned = false
    @JvmField
    var applicationIdTextResource: TextResource = services.projectInfo.getProject().resources.text.fromString("")

    abstract val description: String

    init {
        val splits = globalScope.extension.splits
        val splitsEnabled = (splits.density.isEnable
                || splits.abi.isEnable
                || splits.language.isEnable)

        // warn the user if we are forced to ignore the generatePureSplits flag.
        if (splitsEnabled && globalScope.extension.generatePureSplits) {
            Logging.getLogger(BaseVariantData::class.java)
                .warn(
                    String.format(
                        "Variant %s requested removed pure splits support, reverted to full splits",
                        componentIdentity.name
                    )
                )
        }
    }


    fun getGeneratedBytecode(generatorKey: Any?): FileCollection {
        return if (generatorKey == null) {
            allPreJavacGeneratedBytecode
        } else preJavacGeneratedBytecodeMap?.get(generatorKey)
            ?: throw RuntimeException("Bytecode generator key not found")
    }

    fun addJavaSourceFoldersToModel(generatedSourceFolder: File) {
        extraGeneratedSourceFolders.add(generatedSourceFolder)
    }

    fun addJavaSourceFoldersToModel(vararg generatedSourceFolders: File) {
        Collections
            .addAll(extraGeneratedSourceFolders, *generatedSourceFolders)
    }

    fun addJavaSourceFoldersToModel(generatedSourceFolders: Collection<File>) {
        extraGeneratedSourceFolders.addAll(generatedSourceFolders)
    }

    open fun registerJavaGeneratingTask(
        task: Task,
        generatedSourceFolders: Collection<File>
    ) {
        @Suppress("DEPRECATION")
        taskContainer.sourceGenTask.dependsOn(task)

        val fileTrees = extraGeneratedSourceFileTrees ?: mutableListOf<ConfigurableFileTree>().also {
            extraGeneratedSourceFileTrees = it
        }

        for (f in generatedSourceFolders) {
            val fileTree = services.fileTree(f).builtBy(task)
            fileTrees.add(fileTree)
        }
        addJavaSourceFoldersToModel(generatedSourceFolders)
    }

    open fun registerJavaGeneratingTask(
        taskProvider: TaskProvider<out Task>,
        generatedSourceFolders: Collection<File>
    ) {
        taskContainer.sourceGenTask.dependsOn(taskProvider)

        val fileTrees =
            extraGeneratedSourceFileTrees ?: mutableListOf<ConfigurableFileTree>().also {
                extraGeneratedSourceFileTrees = it
            }

        for (f in generatedSourceFolders) {
            val fileTree = services.fileTree(f).builtBy(taskProvider)
            fileTrees.add(fileTree)
        }
        addJavaSourceFoldersToModel(generatedSourceFolders)
    }

    fun registerExternalAptJavaOutput(folder: ConfigurableFileTree) {
        val fileTrees = externalAptJavaOutputFileTrees ?: mutableListOf<ConfigurableFileTree>().also {
            externalAptJavaOutputFileTrees = it
        }

        fileTrees.add(folder)
        addJavaSourceFoldersToModel(folder.dir)
    }

    fun registerGeneratedResFolders(folders: FileCollection) {
        extraGeneratedResFolders.from(folders)
    }

    fun registerResGeneratingTask(
        task: Task,
        vararg generatedResFolders: File
    ) {
        registerResGeneratingTask(
            task,
            listOf(*generatedResFolders)
        )
    }

    fun registerResGeneratingTask(
        task: Task,
        generatedResFolders: Collection<File>
    ) {
        println(
            "registerResGeneratingTask is deprecated, use registerGeneratedResFolders(FileCollection)"
        )
        registerGeneratedResFolders(services.fileCollection(generatedResFolders).builtBy(task))
    }

    fun registerPreJavacGeneratedBytecode(fileCollection: FileCollection): Any {
        val map = preJavacGeneratedBytecodeMap ?: mutableMapOf<Any, FileCollection>().also {
            preJavacGeneratedBytecodeMap = it
        }

        // latest contains the generated bytecode up to now, so create a new key and put it in the
        // map.
        val key = Any()
        map[key] = preJavacGeneratedBytecodeLatest

        // now create a new file collection that will contains the previous latest plus the new
        // one

        // and make this the latest
        preJavacGeneratedBytecodeLatest = preJavacGeneratedBytecodeLatest.plus(fileCollection)

        // also add the stable all-bytecode file collection. We need a stable collection for
        // queries that request all the generated bytecode before the variant api is called.
        allPreJavacGeneratedBytecode.from(fileCollection)
        return key
    }

    fun registerPostJavacGeneratedBytecode(fileCollection: FileCollection) {
        allPostJavacGeneratedBytecode.from(fileCollection)
    }

    /**
     * Calculates the filters for this variant. The filters can either be manually specified by
     * the user within the build.gradle or can be automatically discovered using the variant
     * specific folders.
     *
     * This method must be called before [.getFilters].
     *
     * @param splits the splits configuration from the build.gradle.
     */
    fun calculateFilters(splits: Splits) {
        densityFilters = getFilters(DiscoverableFilterType.DENSITY, splits)
        languageFilters = getFilters(DiscoverableFilterType.LANGUAGE, splits)
        abiFilters = getFilters(DiscoverableFilterType.ABI, splits)
    }

    /**
     * Returns the filters values (as manually specified or automatically discovered) for a
     * particular [com.android.build.OutputFile.FilterType]
     * @param filterType the type of filter in question
     * @return a possibly empty set of filter values.
     * @throws IllegalStateException if [.calculateFilters] has not been called prior
     * to invoking this method.
     */
    fun getFilters(filterType: VariantOutput.FilterType): Set<String> {
        check(::densityFilters.isInitialized && ::languageFilters.isInitialized && ::abiFilters.isInitialized) {
            "calculateFilters method not called"
        }

        return when (filterType) {
            VariantOutput.FilterType.DENSITY -> densityFilters
            VariantOutput.FilterType.LANGUAGE -> languageFilters
            VariantOutput.FilterType.ABI -> abiFilters
            else -> throw RuntimeException("Unhandled filter type")
        }
    }

    val allRawAndroidResources: FileCollection by lazy {
        val allRes: ConfigurableFileCollection = services.fileCollection().builtBy(
            listOfNotNull(
                taskContainer.renderscriptCompileTask,
                taskContainer.generateResValuesTask,
                taskContainer.generateApkDataTask,
                extraGeneratedResFolders.builtBy
            )
        )

        allRes.from(
            variantDependencies
                .getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.ANDROID_RES
                )
                .artifactFiles
        )

        allRes.from(
            services.fileCollection(
                paths.renderscriptResOutputDir,
                paths.generatedResOutputDir,
                extraGeneratedResFolders
            )
        )

        taskContainer.generateApkDataTask?.let {
            allRes.from(artifacts.get(InternalArtifactType.MICRO_APK_RES))
        }

        for (sourceSet in androidResources.values) {
            allRes.from(sourceSet)
        }

        allRes
    }

    /**
     * Defines the discoverability attributes of filters.
     */
    private enum class DiscoverableFilterType {
        DENSITY {
            override fun getConfiguredFilters(splits: Splits): Collection<String> {
                return splits.densityFilters
            }
        },
        LANGUAGE {
            override fun getConfiguredFilters(splits: Splits): Collection<String> {
                return splits.languageFilters
            }
        },
        ABI {
            override fun getConfiguredFilters(splits: Splits): Collection<String> {
                return splits.abiFilters
            }
        };

        /**
         * Returns the applicable filters configured in the build.gradle for this filter type.
         * @param splits the build.gradle splits configuration
         * @return a list of filters.
         */
        abstract fun getConfiguredFilters(splits: Splits): Collection<String>
    }

    val androidResources: Map<String, FileCollection>
        get() = variantSources
            .sortedSourceProviders
            .associate { it.name to (it as AndroidSourceSet).res.getBuildableArtifact() }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .addValue(componentIdentity.name).toString()
    }

    val javaResourcesForUnitTesting: File
        get() {
            // FIXME we need to revise this API as it force-configure the tasks
            val processJavaResourcesTask = taskContainer.processJavaResourcesTask.get()
            return if (processJavaResourcesTask != null) {
                processJavaResourcesTask.outputs.files.singleFile
            } else {
                artifacts.get(JAVA_RES).get().asFile
            }
        }

    companion object {
        /**
         * Gets the list of filter values for a filter type either from the user specified build.gradle
         * settings or through a discovery mechanism using folders names.
         * @param filterType the filter type
         * @param splits the variant's configuration for splits.
         * @return a possibly empty list of filter value for this filter type.
         */
        private fun getFilters(
            filterType: DiscoverableFilterType,
            splits: Splits
        ): Set<String> {
            return HashSet(filterType.getConfiguredFilters(splits))
        }
    }
}

