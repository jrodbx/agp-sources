/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.build.api.variant.BuiltArtifacts
import com.android.build.api.variant.impl.BuiltArtifactImpl
import com.android.build.api.variant.impl.BuiltArtifactsImpl
import com.android.build.api.variant.impl.getApiString
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.component.DeviceTestCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.InstrumentedTestCreationConfig
import com.android.build.gradle.internal.component.TestCreationConfig
import com.android.build.gradle.internal.component.TestVariantCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalArtifactType.PACKAGED_MANIFESTS
import com.android.build.gradle.internal.tasks.BuildAnalyzer
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.tasks.manifest.ManifestProviderImpl
import com.android.build.gradle.internal.utils.parseTargetHash
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.tasks.ProcessApplicationManifest.Companion.getArtifactName
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.internal.InstrumentedTestManifestGenerator
import com.android.builder.internal.UnitTestManifestGenerator
import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.ManifestMerger2.MergeFailureException
import com.android.manifmerger.ManifestProvider
import com.android.manifmerger.ManifestSystemProperty
import com.android.manifmerger.MergingReport
import com.android.manifmerger.PlaceholderHandler
import com.android.utils.FileUtils
import com.android.utils.ILogger
import com.google.common.base.Charsets
import com.google.common.base.Preconditions
import com.google.common.io.Files
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.IOException

/**
 * A task that processes the manifest for test modules and tests in androidTest.
 *
 * For both test modules and tests in androidTest process is the same, except for how the tested
 * application id is extracted.
 *
 * Tests in androidTest get that info from the [ComponentCreationConfig.getApplicationId] on
 * the [TestComponentCreationConfig.getTestedConfig()] object,
 * while the test modules get the info from the published intermediate manifest with type
 * [AndroidArtifacts.TYPE_METADATA] of the tested app.
 */
@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.MANIFEST)
abstract class ProcessTestManifest : ManifestProcessorTask() {

    @get:OutputDirectory
    abstract val packagedManifestOutputDirectory: DirectoryProperty

    @get:Internal
    abstract val tmpDir: DirectoryProperty

    private var manifests: ArtifactCollection? = null

    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    var navigationJsons: FileCollection? = null
        private set

    override fun doTaskAction() {
        val manifestOutputFolder = packagedManifestOutputDirectory.get().asFile
        FileUtils.mkdirs(manifestOutputFolder)
        val manifestOutputFile = File(manifestOutputFolder, SdkConstants.ANDROID_MANIFEST_XML)
        val navJsons = navigationJsons?.files ?: listOf<File>()

        mergeManifestsForTestVariant(
            testApplicationId.get(),
            namespace.get(),
            minSdkVersion.get(),
            targetSdkVersion.orNull,
            compileSdk.orNull,
            testedApplicationId.get(),
            instrumentationRunner.get(),
            handleProfiling.orNull,
            functionalTest.orNull,
            testLabel.orNull?.let { it.ifEmpty { null } },
            if (testManifestFile.get().asFile.isFile) testManifestFile.get().asFile else null,
            computeProviders(),
            placeholdersValues.get(),
            navJsons,
            extractNativeLibs.orNull,
            debuggable.get(),
            manifestOutputFile,
            tmpDir.get().asFile
        )
        BuiltArtifactsImpl(
            BuiltArtifacts.METADATA_FILE_VERSION,
            PACKAGED_MANIFESTS,
            testApplicationId.get(),
            variantName,
            listOf(
                BuiltArtifactImpl.make(
                    manifestOutputFile.absolutePath
                )
            )
        ).saveToDirectory(packagedManifestOutputDirectory.get().asFile)
    }

    /**
     * Creates the manifest for a test variant
     *
     * @param testApplicationId the application id of the test application
     * @param namespace the namespace of the test application
     * @param minSdkVersion the minSdkVersion of the test application
     * @param targetSdkVersion the targetSdkVersion of the test application
     * @param compileSdk the compile SDK API level of the test application
     * @param testedApplicationId the application id of the tested application
     * @param instrumentationRunner the name of the instrumentation runner
     * @param handleProfiling whether or not the Instrumentation object will turn profiling on and
     * off
     * @param functionalTest whether or not the Instrumentation class should run as a functional
     * test
     * @param testLabel the label for the tests
     * @param testManifestFile optionally user provided AndroidManifest.xml for testing application
     * @param manifestProviders the manifest providers
     * @param manifestPlaceholders used placeholders in the manifest
     * @param navigationJsons the list of navigation JSON files
     * @param extractNativeLibs the value to assign to the injected android:extractNativeLibs
     * attribute. The attribute will not be injected if null. Even if not null, the attribute will
     * not be modified if it's already present in the test's source manifest.
     * @param debuggable whether the variant is debuggable
     * @param outManifest the output location for the merged manifest
     * @param tmpDir temporary dir used for processing
     */
    private fun mergeManifestsForTestVariant(
        testApplicationId: String,
        namespace: String,
        minSdkVersion: String,
        targetSdkVersion: String?,
        compileSdk: Int?,
        testedApplicationId: String,
        instrumentationRunner: String,
        handleProfiling: Boolean?,
        functionalTest: Boolean?,
        testLabel: String?,
        testManifestFile: File?,
        manifestProviders: List<ManifestProvider?>,
        manifestPlaceholders: Map<String?, Any?>,
        navigationJsons: Collection<File>,
        extractNativeLibs: Boolean?,
        debuggable: Boolean,
        outManifest: File,
        tmpDir: File
    ) {
        Preconditions.checkNotNull(
            testApplicationId,
            "testApplicationId cannot be null."
        )
        Preconditions.checkNotNull(
            testedApplicationId,
            "testedApplicationId cannot be null."
        )
        Preconditions.checkNotNull(
            manifestProviders,
            "manifestProviders cannot be null."
        )
        Preconditions.checkNotNull(
            outManifest,
            "outManifestLocation cannot be null."
        )
        val logger: ILogger =
            LoggerWrapper(logger)
        // These temp files are only need in the middle of processing manifests; delete
        // them when they're done. We're not relying on File#deleteOnExit for this
        // since in the Gradle daemon for example that would leave the files around much
        // longer than we want.
        var tempFile1: File? = null
        var tempFile2: File? = null
        try {
            val targetSdkVersionOrNull = targetSdkVersion?.takeIf { targetSdkVersion != "-1" }
            FileUtils.mkdirs(tmpDir)
            var generatedTestManifest: File =
                File.createTempFile("tempFile1ProcessTestManifest", ".xml", tmpDir)
                    .also { tempFile1 = it }
            // we are generating the manifest and if there is an existing one,
            // it will be merged with the generated one
            logger.verbose("Generating in %1\$s", generatedTestManifest.absolutePath)
            if (handleProfiling != null) {
                Preconditions.checkNotNull(
                    functionalTest,
                    "functionalTest cannot be null."
                )
                generateInstrumentedTestManifest(
                    testApplicationId,
                    minSdkVersion,
                    targetSdkVersionOrNull,
                    testedApplicationId,
                    instrumentationRunner,
                    handleProfiling,
                    functionalTest!!,
                    generatedTestManifest
                )
            } else {
                generateUnitTestManifest(
                    testApplicationId,
                    minSdkVersion,
                    targetSdkVersionOrNull,
                    generatedTestManifest,
                    testApplicationId,
                    instrumentationRunner)
            }

            // There can be three types of input that combine into making the main manifest and
            // its overlays :
            // 1. the `testManifestFile` which is the main source file (possibly nonexistent)
            // 2. the overlays coming from the build-type/product flavor specific source folders.
            // 3. generated manifest files added through the variant API as a Variant specific source
            // (1), (2) and (3) can all be null/empty or nonexistent in the file system.
            // In case (1) does not exist, we still need to do the merging if (2) or (3) exists and
            // in that case, we consider that the first overlay becomes the main Manifest (variant one),
            // and we remove it from the list of overlays.
            val overlays: List<File> = manifestOverlayFilePaths.get().filter(File::isFile)
            // Pair of main manifest (possibly null or nonexistent) and overlays (possibly empty)
            val mainManifestAndOverlays: Pair<File?, List<File>> =
                if (testManifestFile == null || !testManifestFile.exists()) {
                    Pair(overlays.firstOrNull(), if (overlays.isNotEmpty()) overlays.drop(1) else emptyList())
                }  else {
                    Pair(testManifestFile, overlays)
                }

            val mainManifestFile = mainManifestAndOverlays.first
            if (mainManifestFile != null && mainManifestFile.exists()) {
                val intermediateInvoker = ManifestMerger2.newMerger(
                    mainManifestFile,
                    logger,
                    ManifestMerger2.MergeType.APPLICATION
                )
                    .setPlaceHolderValues(manifestPlaceholders)
                    .addFlavorAndBuildTypeManifests(
                        *mainManifestAndOverlays.second.toTypedArray())
                    .addLibraryManifest(generatedTestManifest)
                    .addAllowedNonUniqueNamespace(namespace)
                    .setOverride(ManifestSystemProperty.Document.PACKAGE, testApplicationId)
                    .setOverride(ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION, minSdkVersion)
                    .setOverride(ManifestSystemProperty.Instrumentation.TARGET_PACKAGE, testedApplicationId)
                    .setNamespace(namespace)
                    .withFeatures(
                        ManifestMerger2.Invoker.Feature.DISABLE_MINSDKLIBRARY_CHECK,
                        ManifestMerger2.Invoker.Feature.CHECK_IF_PACKAGE_IN_MAIN_MANIFEST
                    )

                instrumentationRunner.let {
                    intermediateInvoker.setPlaceHolderValue(
                        PlaceholderHandler.INSTRUMENTATION_RUNNER,
                        it)
                    intermediateInvoker.setOverride(ManifestSystemProperty.Instrumentation.NAME, it)
                }
                functionalTest?.let {
                    intermediateInvoker.setOverride(
                        ManifestSystemProperty.Instrumentation.FUNCTIONAL_TEST, it.toString()
                    )
                }
                handleProfiling?.let {
                    intermediateInvoker.setOverride(
                        ManifestSystemProperty.Instrumentation.HANDLE_PROFILING, it.toString()
                    )
                }
                if (testLabel != null) {
                    intermediateInvoker.setOverride(ManifestSystemProperty.Instrumentation.LABEL, testLabel)
                }
                targetSdkVersionOrNull?.let {
                    intermediateInvoker.setOverride(
                        ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION, it
                    )
                }
                tempFile2 = File.createTempFile("tempFile2ProcessTestManifest", ".xml", tmpDir)
                handleMergingResult(intermediateInvoker.merge(), tempFile2, logger)
                generatedTestManifest = tempFile2
            }
            val finalInvoker = ManifestMerger2.newMerger(
                generatedTestManifest,
                logger,
                ManifestMerger2.MergeType.APPLICATION
            )
                .withFeatures(
                    ManifestMerger2.Invoker.Feature.REMOVE_TOOLS_DECLARATIONS
                )
                .setOverride(ManifestSystemProperty.Document.PACKAGE, testApplicationId)
                .addManifestProviders(manifestProviders)
                .setPlaceHolderValues(manifestPlaceholders)
                .addNavigationJsons(navigationJsons)
                .setNamespace(namespace)
            extractNativeLibs?.let {
                // android:extractNativeLibs unrecognized if using compile SDK < 23.
                if (compileSdk == null || compileSdk >= 23) {
                    finalInvoker.setOverride(
                        ManifestSystemProperty.Application.EXTRACT_NATIVE_LIBS,
                        it.toString()
                    )
                }
            }
            if (debuggable) {
                finalInvoker.withFeatures(ManifestMerger2.Invoker.Feature.DEBUGGABLE)
            }
            handleMergingResult(finalInvoker.merge(), outManifest, logger)
        } catch (e: IOException) {
            throw RuntimeException("Unable to create the temporary file", e)
        } catch (e: MergeFailureException) {
            throw RuntimeException("Manifest merging exception", e)
        } finally {
            try {
                if (tempFile1 != null) {
                    FileUtils.delete(tempFile1!!)
                }
                if (tempFile2 != null) {
                    FileUtils.delete(tempFile2)
                }
            } catch (e: IOException) {
                // just log this, so we do not mask the initial exception if there is any
                logger.error(e, "Unable to clean up the temporary files.")
            }
        }
    }

    @Throws(IOException::class)
    private fun handleMergingResult(
        mergingReport: MergingReport, outFile: File, logger: ILogger
    ) {
        outputMergeBlameContents(
            mergingReport,
            mergeBlameFile.get().asFile
        )
        if (mergingReport.result == MergingReport.Result.ERROR) {
            mergingReport.log(logger)
            throw RuntimeException(mergingReport.reportString)
        }
        if (mergingReport.result == MergingReport.Result.WARNING) {
            mergingReport.log(logger)
        }

        try {
            val annotatedDocument =
                mergingReport.getMergedDocument(MergingReport.MergedManifestKind.BLAME)
            logger.verbose(annotatedDocument
                ?: "No blaming records from manifest merger")
        } catch (e: Exception) {
            logger.error(e, "cannot print resulting xml")
        }
        val finalMergedDocument =
            mergingReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED)
                ?: throw RuntimeException("No result from manifest merger")
        try {
            Files.asCharSink(outFile, Charsets.UTF_8).write(finalMergedDocument)
        } catch (e: IOException) {
            logger.error(e, "Cannot write resulting xml")
            throw RuntimeException(e)
        }
        logger.verbose("Merged manifest saved to $outFile")
    }

    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputFiles // Use InputFiles rather than InputFile to allow the file not to exist
    abstract val testManifestFile: RegularFileProperty

    @get:Input
    abstract val testApplicationId: Property<String>

    @get:Input
    abstract val testedApplicationId: Property<String>

    @get:Input
    abstract val namespace: Property<String>

    @get:Input
    abstract val minSdkVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val targetSdkVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val instrumentationRunner: Property<String>

    @get:Input
    @get:Optional
    abstract val handleProfiling: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val functionalTest: Property<Boolean>

    @get:Input
    abstract val componentType: Property<String>

    @get:Optional
    @get:Input
    abstract val testLabel: Property<String>

    @get:Input
    abstract val placeholdersValues: MapProperty<String, String>

    @get:Optional
    @get:Input
    abstract val extractNativeLibs: Property<Boolean>

    @get:Input
    abstract val debuggable: Property<Boolean>

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles // Note: The files may not exist
    abstract val manifestOverlayFilePaths: ListProperty<File>

    @get:Input
    @get:Optional
    abstract val compileSdk: Property<Int>

    /**
     * Compute the final list of providers based on the manifest file collection.
     * @return the list of providers.
     */
    private fun computeProviders(): List<ManifestProvider?> {
        return manifests!!.artifacts.map { ManifestProviderImpl(it.file, getArtifactName(it)) }
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getManifests(): FileCollection {
        return manifests!!.artifactFiles
    }

    class CreationAction(
        creationConfig: TestCreationConfig
    ) : VariantTaskCreationAction<ProcessTestManifest, TestCreationConfig>(creationConfig) {
        override val name = computeTaskName("process", "Manifest")
        override val type = ProcessTestManifest::class.java

        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)
            creationConfig
                .artifacts
                .republish(
                    PACKAGED_MANIFESTS,
                    InternalArtifactType.MANIFEST_METADATA
                )
        }

        override fun handleProvider(
            taskProvider: TaskProvider<ProcessTestManifest>
        ) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.processManifestTask = taskProvider
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ProcessTestManifest::packagedManifestOutputDirectory
            ).on(PACKAGED_MANIFESTS)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ProcessTestManifest::mergeBlameFile
            ).withName("manifest-merger-blame-" + creationConfig.baseName + "-report.txt")
                .on(InternalArtifactType.MANIFEST_MERGE_BLAME_FILE)
        }

        override fun configure(
            task: ProcessTestManifest
        ) {
            super.configure(task)
            val project = task.project
            task.testManifestFile
                .fileProvider(creationConfig.sources.manifestFile)
            task.testManifestFile.disallowChanges()
            task.manifestOverlayFilePaths.setDisallowChanges(creationConfig.sources.manifestOverlayFiles)
            task.componentType.setDisallowChanges(creationConfig.componentType.toString())
            task.tmpDir.setDisallowChanges(
                creationConfig.paths.intermediatesDir(
                    "tmp",
                    "manifest",
                    creationConfig.dirName
                )
            )
            task.minSdkVersion.setDisallowChanges(creationConfig.minSdk.getApiString())
            task.targetSdkVersion.setDisallowChanges(creationConfig.targetSdkVersion.getApiString())

            task.testApplicationId.setDisallowChanges(creationConfig.applicationId)
            task.testedApplicationId.setDisallowChanges(creationConfig.testedApplicationId)
            task.namespace.setDisallowChanges(creationConfig.namespace)

            task.instrumentationRunner.setDisallowChanges(creationConfig.instrumentationRunner)
            if (creationConfig is InstrumentedTestCreationConfig) {
                task.handleProfiling.set(creationConfig.handleProfiling)
                task.functionalTest.set(creationConfig.functionalTest)
                task.testLabel.set(creationConfig.testLabel)
            }
            task.handleProfiling.disallowChanges()
            task.functionalTest.disallowChanges()
            task.testLabel.disallowChanges()

            task.manifests = creationConfig
                .variantDependencies
                .getArtifactCollection(
                    ConsumedConfigType.RUNTIME_CLASSPATH,
                    ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.MANIFEST
                )
            task.placeholdersValues.setDisallowChanges(
                creationConfig.manifestPlaceholdersCreationConfig?.placeholders,
                handleNullable = {
                    empty()
                }
            )
            if (!creationConfig.global.namespacedAndroidResources) {
                task.navigationJsons = project.files(
                    creationConfig
                        .variantDependencies
                        .getArtifactFileCollection(
                            ConsumedConfigType.RUNTIME_CLASSPATH,
                            ArtifactScope.ALL,
                            AndroidArtifacts.ArtifactType.NAVIGATION_JSON
                        )
                )
            }
            when (creationConfig) {
                is DeviceTestCreationConfig -> {
                    task.extractNativeLibs.setDisallowChanges(
                        creationConfig.packaging.jniLibs.useLegacyPackaging
                    )
                }
                is TestVariantCreationConfig -> {
                    task.extractNativeLibs.setDisallowChanges(
                        creationConfig.packaging.jniLibs.useLegacyPackaging
                    )
                }
                else -> {
                    task.extractNativeLibs.disallowChanges()
                }
            }
            task.debuggable.setDisallowChanges(creationConfig.debuggable)
            task.compileSdk
                .setDisallowChanges(
                    parseTargetHash(creationConfig.global.compileSdkHashString).apiLevel
                )
        }
    }

    companion object {
        private fun generateInstrumentedTestManifest(
            testApplicationId: String,
            minSdkVersion: String?,
            targetSdkVersion: String?,
            testedApplicationId: String,
            instrumentationRunner: String,
            handleProfiling: Boolean,
            functionalTest: Boolean,
            outManifestLocation: File
        ) {
            val generator =
                InstrumentedTestManifestGenerator(
                    outManifestLocation,
                    testApplicationId,
                    minSdkVersion,
                    targetSdkVersion,
                    testedApplicationId,
                    instrumentationRunner,
                    handleProfiling,
                    functionalTest
                )
            try {
                generator.generate()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }

        private fun generateUnitTestManifest(
            testApplicationId: String,
            minSdkVersion: String?,
            targetSdkVersion: String?,
            outManifestLocation: File,
            testedApplicationId: String,
            instrumentedRunner: String,
        ) {
            val generator =
                UnitTestManifestGenerator(
                    outManifestLocation,
                    testApplicationId,
                    minSdkVersion,
                    targetSdkVersion,
                    testedApplicationId,
                    instrumentedRunner,
                )
            try {
                generator.generate()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
        }
    }
}
