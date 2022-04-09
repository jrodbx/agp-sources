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
package com.android.build.gradle.internal.coverage

import com.android.Version
import com.android.build.api.component.impl.TestComponentImpl
import com.android.build.gradle.internal.component.TestComponentCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.utils.usLocaleCapitalize
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.io.Closeables
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.ClassLoaderWorkerSpec
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.tools.ExecFileLoader
import org.jacoco.report.DirectorySourceFileLocator
import org.jacoco.report.FileMultiReportOutput
import org.jacoco.report.IReportVisitor
import org.jacoco.report.MultiReportVisitor
import org.jacoco.report.MultiSourceFileLocator
import org.jacoco.report.html.HTMLFormatter
import org.jacoco.report.xml.XMLFormatter
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.UncheckedIOException
import java.util.Locale

/**
 * For generating unit test coverage reports using jacoco. Provides separate CreateActions for
 * generating unit test and connected test reports.
 */
@DisableCachingByDefault
abstract class JacocoReportTask : NonIncrementalTask() {

    // PathSensitivity.NONE since only the contents of the files under the directory matter as input
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val jacocoConnectedTestsCoverageDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val jacocoUnitTestCoverageFile: RegularFileProperty

    @get:Input
    abstract val reportName: Property<String>

    @get:Classpath
    abstract val classFileCollection: ConfigurableFileCollection

    @get:Classpath
    abstract val jacocoClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val javaSources: ListProperty<ConfigurableFileTree>

    @get:Internal
    abstract val tabWidth: Property<Int>

    @get:OutputDirectory
    abstract val outputReportDir: DirectoryProperty

    override fun doTaskAction() {
        if (!jacocoConnectedTestsCoverageDir.isPresent && !jacocoUnitTestCoverageFile.isPresent) {
            throw IOException("No coverage data found. " +
                    "Please enable code coverage for this build type in build.gradle.")
        }
        val coverageFiles: Set<File> = if (jacocoUnitTestCoverageFile.isPresent) {
            // Unit test coverage:
            setOf(jacocoUnitTestCoverageFile.get().asFile)
        } else {
            // Connected android test coverage:
            val connectedTestJacocoFiles =
                jacocoConnectedTestsCoverageDir.get().asFileTree.files.filter(File::isFile)
            if (connectedTestJacocoFiles.none()) {
                val path = jacocoConnectedTestsCoverageDir.get().asFile.absolutePath
                throw IOException("No coverage data to process in directories [$path]")
            }
            connectedTestJacocoFiles.toSet()
        }

        // Jacoco requires source set directory roots rather than source files to produce
        // source code highlighting in reports.
        val sourceFolders = javaSources.get().map(ConfigurableFileTree::getDir)

        workerExecutor
            .classLoaderIsolation { classpath: ClassLoaderWorkerSpec ->
                classpath.classpath.from(jacocoClasspath.files)
            }
            .submit(JacocoReportWorkerAction::class.java) {
                it.coverageFiles.setFrom(coverageFiles)
                it.reportDir.set(outputReportDir)
                it.classFolders.setFrom(classFileCollection)
                it.sourceFolders.setFrom(sourceFolders)
                it.tabWidth.set(tabWidth)
                it.reportName.set(reportName)
            }
    }

    abstract class BaseCreationAction(
        testComponentProperties: TestComponentCreationConfig,
    protected open val jacocoAntConfiguration: Configuration? = null
    ) : VariantTaskCreationAction<JacocoReportTask, TestComponentCreationConfig>
    (testComponentProperties) {

        override val name: String
            get() = computeTaskName("create", "CoverageReport")
        override val type: Class<JacocoReportTask>
            get() = JacocoReportTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<JacocoReportTask>) {
            super.handleProvider(taskProvider)
            creationConfig.taskContainer.coverageReportTask = taskProvider
        }

        override fun configure(task: JacocoReportTask) {
            super.configure(task)
            task.jacocoClasspath.setFrom(jacocoAntConfiguration)
            task.outputReportDir.set(creationConfig.paths.coverageReportDir)
            task.outputReportDir.disallowChanges()
            task.reportName.setDisallowChanges(creationConfig.testedConfig.name)
            task.tabWidth.setDisallowChanges(4)

            task.classFileCollection.setFrom(creationConfig.testedConfig.artifacts.getAllClasses())
            task.javaSources.setDisallowChanges(creationConfig.testedConfig.sources.java.getAsFileTrees())
        }
    }

    internal class CreateActionUnitTest(
        testComponentProperties: TestComponentCreationConfig,
        override val jacocoAntConfiguration: Configuration? = null
    ) : BaseCreationAction(testComponentProperties, jacocoAntConfiguration) {

        override fun configure(task: JacocoReportTask) {
            super.configure(task)
            task.description = "Generates a Jacoco code coverage report from unit tests."
            creationConfig.artifacts.setTaskInputToFinalProduct(
                InternalArtifactType.UNIT_TEST_CODE_COVERAGE, task.jacocoUnitTestCoverageFile)
            /** Jacoco coverage files are generated from [AndroidUnitTest] */
            task.dependsOn(
                "${JavaPlugin.TEST_TASK_NAME}${creationConfig.name.usLocaleCapitalize()}")
        }
    }

    class CreationActionConnectedTest(
        testComponentProperties: TestComponentImpl,
        override val jacocoAntConfiguration: Configuration
    ) : BaseCreationAction(testComponentProperties, jacocoAntConfiguration) {

        override fun configure(task: JacocoReportTask) {
            super.configure(task)
            task.description =
                "Creates JaCoCo test coverage report from data gathered on the device."
            creationConfig
                .artifacts
                .setTaskInputToFinalProduct(
                    InternalArtifactType.CODE_COVERAGE,
                    task.jacocoConnectedTestsCoverageDir
                )
        }
    }

    class CreationActionManagedDeviceTest(
        testComponentProperties: TestComponentImpl,
        override val jacocoAntConfiguration: Configuration
    ) : BaseCreationAction(testComponentProperties, jacocoAntConfiguration) {

        override val name: String
            get() = computeTaskName("createManagedDevice", "CoverageReport")

        override fun configure(task: JacocoReportTask) {
            super.configure(task)
            task.description =
                "Creates JaCoCo test coverage report from data gathered on the Gradle managed device."
            creationConfig
                .artifacts
                .setTaskInputToFinalProduct(
                    InternalArtifactType.MANAGED_DEVICE_CODE_COVERAGE,
                    task.jacocoConnectedTestsCoverageDir
                )

        }
    }

    interface JacocoWorkParameters : WorkParameters {
        val coverageFiles: ConfigurableFileCollection
        val reportDir: DirectoryProperty
        val classFolders: ConfigurableFileCollection
        val sourceFolders: ConfigurableFileCollection
        val tabWidth: Property<Int>
        val reportName: Property<String>
    }

    abstract class JacocoReportWorkerAction : WorkAction<JacocoWorkParameters> {

        override fun execute() {
            try {
                generateReport(
                    parameters.coverageFiles.files,
                    parameters.reportDir.asFile.get(),
                    parameters.classFolders.files,
                    parameters.sourceFolders.files,
                    parameters.tabWidth.get(),
                    parameters.reportName.get()
                )
            } catch (e: IOException) {
                throw UncheckedIOException("Unable to generate Jacoco report", e)
            }
        }
        companion object {

            val logger = Logging.getLogger(
                JacocoReportWorkerAction::class.java
            )

            @VisibleForTesting
            @JvmStatic
            @Throws(IOException::class)
            fun generateReport(
                coverageFiles: Collection<File>,
                reportDir: File,
                classFolders: Collection<File>,
                sourceFolders: Collection<File>,
                tabWidth: Int,
                reportName: String
            ) {
                // Load data
                val loader = ExecFileLoader()
                for (coverageFile in coverageFiles) {
                    loader.load(coverageFile)
                }
                val sessionInfoStore = loader.sessionInfoStore
                val executionDataStore = loader.executionDataStore

                // Initialize report generator.
                val htmlFormatter = HTMLFormatter()
                htmlFormatter.outputEncoding = "UTF-8"
                htmlFormatter.locale = Locale.US
                htmlFormatter.footerText = (
                        "Generated by the Android Gradle plugin "
                                + Version.ANDROID_GRADLE_PLUGIN_VERSION)
                val output = FileMultiReportOutput(reportDir)
                val htmlReport = htmlFormatter.createVisitor(output)
                val xmlFormatter = XMLFormatter()
                xmlFormatter.setOutputEncoding("UTF-8")
                val xmlReportOutput = output.createFile("report.xml")
                try {
                    val xmlReport = xmlFormatter.createVisitor(xmlReportOutput)
                    val visitor: IReportVisitor =
                        MultiReportVisitor(ImmutableList.of(htmlReport, xmlReport))

                    // Generate report
                    visitor.visitInfo(sessionInfoStore.infos, executionDataStore.contents)
                    val builder = CoverageBuilder()
                    val analyzer = Analyzer(executionDataStore, builder)
                    analyzeAll(analyzer, classFolders)
                    val locator = MultiSourceFileLocator(0)
                    for (folder in sourceFolders) {
                        locator.add(DirectorySourceFileLocator(folder, "UTF-8", tabWidth))
                    }
                    val bundle = builder.getBundle(reportName)
                    visitor.visitBundle(bundle, locator)
                    visitor.visitEnd()
                } finally {
                    try {
                        xmlReportOutput.close()
                    } catch (e: IOException) {
                        logger.error("Could not close xml report file", e)
                    }
                }
            }
            @Throws(IOException::class)
            private fun analyzeAll(
                analyzer: Analyzer, classFolders: Collection<File>
            ) {
                for (folder in classFolders) {
                    analyze(analyzer, folder, classFolders)
                }
            }
            /**
             * Analyzes code coverage on file if it's a class file, or recursively analyzes descendants
             * if file is a folder.
             *
             * @param analyzer Jacoco Analyzer
             * @param file a file or folder
             * @param originalClassFolders the original collection of class folders to be analyzed;
             * e.g., this.classFileCollection.getFiles(). This parameter is included to avoid
             * redundant computation in the case when one of the original class folders is a
             * descendant of another.
             */
            @Throws(IOException::class)
            private fun analyze(
                analyzer: Analyzer,
                file: File,
                originalClassFolders: Collection<File>
            ) {
                if (file.isDirectory) {
                    val files = file.listFiles()
                    if (files != null) {
                        for (f in files) {
                            // check that f is not in originalClassFolders to avoid redundant
                            // computation
                            if (!originalClassFolders.contains(f)) {
                                analyze(analyzer, f, originalClassFolders)
                            }
                        }
                    }
                } else {
                    val name = file.name
                    if (! name.endsWith(".class")
                        || name == "R.class" || name.startsWith("R$")
                        || name == "Manifest.class" || name.startsWith("Manifest$")
                        || name == "BuildConfig.class"
                    ) {
                        return
                    }
                    val `in`: InputStream = FileInputStream(file)
                    try {
                        analyzer.analyzeClass(`in`, file.absolutePath)
                    } finally {
                        Closeables.closeQuietly(`in`)
                    }
                }
            }
        }
    }
}
