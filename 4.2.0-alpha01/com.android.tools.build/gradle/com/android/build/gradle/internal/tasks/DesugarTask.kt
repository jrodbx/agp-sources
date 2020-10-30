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

package com.android.build.gradle.internal.tasks;

import com.android.SdkConstants
import com.android.build.api.component.impl.ComponentPropertiesImpl
import com.android.build.api.transform.QualifiedContent
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.coverage.JacocoConfigurations
import com.android.build.gradle.internal.pipeline.OriginalStream
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.builder.core.DesugarProcessArgs
import com.android.builder.core.DesugarProcessBuilder
import com.android.ide.common.repository.GradleVersion
import com.android.ide.common.workers.WorkerExecutorFacade
import com.android.utils.FileUtils
import com.android.utils.PathUtils
import com.google.common.collect.ArrayListMultimap
import org.apache.log4j.Logger
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/**
 * Desugar all bytecode that is using Java 8 langauge features, using the desugar tool. This
 * task processes all runtime classes and it uses the runtime classpath and bootclasspath to
 * rewrite the code.
 */
@CacheableTask
abstract class DesugarTask @Inject constructor(objectFactory: ObjectFactory) :
    NonIncrementalTask() {
    @get: Classpath
    abstract val projectClasses: ConfigurableFileCollection
    @get: Classpath
    abstract val subProjectClasses: ConfigurableFileCollection
    @get: Classpath
    abstract val externaLibsClasses: DirectoryProperty
    @get: CompileClasspath
    abstract val desugaringClasspath: ConfigurableFileCollection
    @get: CompileClasspath
    abstract val bootClasspath: ConfigurableFileCollection

    @get: OutputDirectory
    abstract val projectOutput: DirectoryProperty
    @get: OutputDirectory
    abstract val subProjectOutput: DirectoryProperty
    // A directory containing jars.
    @get: OutputDirectory
    abstract val externalLibsOutput: DirectoryProperty
    @get: LocalState
    abstract val tmpDir: DirectoryProperty

    @get: Input
    val minSdk: Property<Int> = objectFactory.property(Int::class.java)
    @get: Input
    val enableBugFixForJacoco: Property<Boolean> = objectFactory.property(Boolean::class.java)

    override fun doTaskAction() {
        getWorkerFacadeWithWorkers().use { executorFacade ->
            val libs = externaLibsClasses.asFile.get().listFiles()!!.toList().sortedBy { it.name }
            DesugarTaskDelegate(
                projectClasses = projectClasses.files,
                subProjectClasses = subProjectClasses.files,
                externaLibsClasses = libs,
                desugaringClasspath = desugaringClasspath.files,
                projectOutput = projectOutput.asFile.get(),
                subProjectOutput = subProjectOutput.asFile.get(),
                externalLibsOutput = externalLibsOutput.asFile.get(),
                tmpDir = tmpDir.asFile.get(),
                bootClasspath = bootClasspath.files,
                minSdk = minSdk.get(),
                enableBugFixForJacoco = enableBugFixForJacoco.get(),
                verbose = Logger.getLogger(DesugarTask::class.java).isDebugEnabled,
                executorFacade = executorFacade
            ).doProcess()
        }
    }

    class CreationAction(componentProperties: ComponentPropertiesImpl) :
        VariantTaskCreationAction<DesugarTask, ComponentPropertiesImpl>(
            componentProperties
        ) {
        override val name: String = computeTaskName("desugar")
        override val type: Class<DesugarTask> = DesugarTask::class.java

        private val projectClasses: FileCollection

        init {
            projectClasses =
                componentProperties.transformManager.getPipelineOutputAsFileCollection { types, scopes ->
                    QualifiedContent.DefaultContentType.CLASSES in types &&
                            scopes == setOf(QualifiedContent.Scope.PROJECT)
                }
            componentProperties.transformManager.consumeStreams(
                mutableSetOf(
                    QualifiedContent.Scope.PROJECT,
                    QualifiedContent.Scope.SUB_PROJECTS,
                    QualifiedContent.Scope.EXTERNAL_LIBRARIES
                ), setOf(QualifiedContent.DefaultContentType.CLASSES)
            )

            // Put processed classes back into Transform pipeline, external transforms may be
            // consuming them.
            mapOf(
                InternalArtifactType.DESUGAR_PROJECT_CLASSES to QualifiedContent.Scope.PROJECT,
                InternalArtifactType.DESUGAR_SUB_PROJECT_CLASSES to QualifiedContent.Scope.SUB_PROJECTS,
                InternalArtifactType.DESUGAR_EXTERNAL_LIBS_CLASSES to QualifiedContent.Scope.EXTERNAL_LIBRARIES
            ).forEach { (output, scope) ->
                val processedClasses = componentProperties.globalScope.project.files(
                    componentProperties.artifacts.get(output)
                )
                    .asFileTree
                componentProperties
                    .transformManager
                    .addStream(
                        OriginalStream.builder(
                            componentProperties.globalScope.project,
                            "desugared-classes-${scope.name}"
                        )
                            .addContentTypes(TransformManager.CONTENT_CLASS)
                            .addScope(scope)
                            .setFileCollection(processedClasses)
                            .build()
                    )
            }
        }

        override fun handleProvider(
            taskProvider: TaskProvider<DesugarTask>
        ) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DesugarTask::projectOutput
            ).on(InternalArtifactType.DESUGAR_PROJECT_CLASSES)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DesugarTask::subProjectOutput
            ).on(InternalArtifactType.DESUGAR_SUB_PROJECT_CLASSES)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DesugarTask::externalLibsOutput
            ).on(InternalArtifactType.DESUGAR_EXTERNAL_LIBS_CLASSES)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                DesugarTask::tmpDir
            ).on(InternalArtifactType.DESUGAR_LOCAL_STATE_OUTPUT)
        }

        override fun configure(
            task: DesugarTask
        ) {
            super.configure(task)
            val variantScope = creationConfig.variantScope
            task.minSdk.set(creationConfig.minSdkVersion.featureLevel)

            /**
             * If a fix in Desugar should be enabled to handle broken bytecode produced by older
             * Jacoco, see http://b/62623509.
             */
            val enableDesugarBugFixForJacoco = try {
                val current = GradleVersion.parse(JacocoTask.getJacocoVersion(creationConfig))
                JacocoConfigurations.MIN_WITHOUT_BROKEN_BYTECODE > current
            } catch (ignored: Throwable) {
                // Cannot determine using version comparison, avoid passing the flag.
                true
            }
            task.enableBugFixForJacoco.set(enableDesugarBugFixForJacoco)

            task.projectClasses.from(projectClasses)
            task.subProjectClasses.from(
                creationConfig.variantDependencies.getArtifactFileCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.PROJECT,
                    AndroidArtifacts.ArtifactType.CLASSES_JAR
                )
            )
            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.FIXED_STACK_FRAMES,
                task.externaLibsClasses
            )

            task.desugaringClasspath.from(variantScope.providedOnlyClasspath)
            task.bootClasspath.from(variantScope.bootClasspath)

            creationConfig.onTestedConfig {
                task.desugaringClasspath.from(
                    it.variantDependencies.getArtifactCollection(
                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                        AndroidArtifacts.ArtifactScope.ALL,
                        AndroidArtifacts.ArtifactType.CLASSES_JAR
                    ).artifactFiles
                )
            }
        }
    }
}

class DesugarTaskDelegate(
    private val projectClasses: Set<File>,
    private val subProjectClasses: Set<File>,
    private val externaLibsClasses: List<File>,
    private val desugaringClasspath: Set<File>,
    private val projectOutput: File,
    private val subProjectOutput: File,
    private val externalLibsOutput: File,
    private val tmpDir: File,
    private val bootClasspath: Set<File>,
    private val minSdk: Int,
    private val enableBugFixForJacoco: Boolean,
    private val verbose: Boolean,
    private val executorFacade: WorkerExecutorFacade
) {

    fun doProcess() {
        FileUtils.cleanOutputDir(projectOutput)
        FileUtils.cleanOutputDir(subProjectOutput)
        FileUtils.cleanOutputDir(externalLibsOutput)
        FileUtils.cleanOutputDir(tmpDir)

        val inputToOutputs = mutableMapOf<Path, Path>()
        projectClasses.forEachIndexed { index, file ->
            inputToOutputs[file.toPath()] = projectOutput.resolve("$index.jar").toPath()
        }
        subProjectClasses.forEachIndexed { index, file ->
            inputToOutputs[file.toPath()] = subProjectOutput.resolve("$index.jar").toPath()
        }
        externaLibsClasses.forEachIndexed { index, file ->
            inputToOutputs[file.toPath()] = externalLibsOutput.resolve("$index.jar").toPath()
        }
        val classpath =
            (projectClasses + subProjectClasses + externaLibsClasses + desugaringClasspath).map { it.path }

        val processArgs =
            getProcessArgs(inputToOutputs, classpath, bootClasspath.map { it.path })

        for (processArg in processArgs) {
            val lambdaDir = PathUtils.createTmpDirToRemoveOnShutdown("gradle_lambdas")
            val isWindows = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS
            val desugarArgs = processArg.getArgs(isWindows)

            loggerWrapper.info("Desugar process args: $desugarArgs")

            executorFacade.submit(
                DesugarWorkerItem.DesugarAction::class.java, WorkerExecutorFacade.Configuration(
                    isolationMode = WorkerExecutorFacade.IsolationMode.PROCESS,
                    classPath = setOf(desugarJar.value),
                    parameter = DesugarWorkerItem.DesugarActionParams(desugarArgs),
                    jvmArgs = listOf(
                        "-Xmx64m",
                        "-Djdk.internal.lambda.dumpProxyClasses=$lambdaDir"
                    )
                )
            )
        }
    }

    private fun getProcessArgs(
        inputToOutput: Map<Path, Path>,
        classpath: List<String>, bootclasspath: List<String>
    ): List<DesugarProcessArgs> {
        val parallelExecutions = Runtime.getRuntime().availableProcessors() / 2

        val procBuckets = ArrayListMultimap.create<Int, Map.Entry<Path, Path>>()
        for ((index, entry) in inputToOutput.entries.withIndex()) {
            val bucketId = index % parallelExecutions
            procBuckets.put(bucketId, entry)
        }

        val args = ArrayList<DesugarProcessArgs>(procBuckets.keySet().size)
        for (bucketId in procBuckets.keySet()) {
            val inToOut = mutableMapOf<String, String>()
            for (e in procBuckets.get(bucketId)) {
                inToOut[e.key.toString()] = e.value.toString()
            }

            val processArgs = DesugarProcessArgs(
                inToOut,
                classpath,
                bootclasspath,
                tmpDir.toString(),
                verbose,
                minSdk,
                enableBugFixForJacoco
            )
            args.add(processArgs)
        }
        return args
    }

    companion object {
        val desugarJar = lazy(LazyThreadSafetyMode.SYNCHRONIZED) { initDesugarJar() }

        /** Set this location of extracted desugar jar that is used for processing.  */
        private fun initDesugarJar(): File {
            val url = DesugarProcessBuilder::class.java.classLoader.getResource(DESUGAR_JAR)!!

            val extractedDesugar = PathUtils.createTmpToRemoveOnShutdown(DESUGAR_JAR)
            url.openConnection().getInputStream().buffered().use { inputStream ->
                Files.copy(
                    inputStream,
                    extractedDesugar,
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            return extractedDesugar.toFile()
        }
    }
}

private val loggerWrapper = LoggerWrapper.getLogger(DesugarTask::class.java)
private const val DESUGAR_JAR = "desugar_deploy.jar"
