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
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.io.Closeables
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
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
import java.util.concurrent.Callable
import java.util.stream.Collectors
import javax.inject.Inject

/** Simple Jacoco report task that calls the Ant version.  */
abstract class JacocoReportTask : NonIncrementalTask() {
    private var jacocoClasspath: FileCollection? = null

    @get:Classpath
    var classFileCollection: FileCollection? = null
        private set

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    var sourceFolders: FileCollection? = null
        private set

    @get:OutputDirectory
    var reportDir: File? = null

    @get:Input
    var reportName: String? = null

    @get:Input
    var tabWidth = 4
    @Deprecated("")
    fun setCoverageFile(coverageFile: File?) {
        logger.info("JacocoReportTask.setCoverageDir is deprecated and has no effect.")
    }

    // PathSensitivity.NONE since only the contents of the files under the directory matter as input
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    @get:InputDirectory
    abstract val coverageDirectories: DirectoryProperty
    @Classpath
    fun getJacocoClasspath(): FileCollection? {
        return jacocoClasspath
    }

    fun setJacocoClasspath(jacocoClasspath: FileCollection?) {
        this.jacocoClasspath = jacocoClasspath
    }

    @kotlin.jvm.Throws(IOException::class)
    override fun doTaskAction() {
        val coverageFiles = coverageDirectories
            .get()
            .asFileTree
            .files
            .stream()
            .filter { obj: File -> obj.isFile }
            .collect(Collectors.toSet())
        if (coverageFiles.isEmpty()) {
            throw IOException(
                String.format(
                    "No coverage data to process in directories [%1\$s]",
                    coverageDirectories.get().asFile.absolutePath
                )
            )
        }
        workerExecutor
            .classLoaderIsolation {
                it.classpath.from(jacocoClasspath?.files)
            }
            .submit(
                JacocoReportWorkerAction::class.java
            ) {
                it?.coverageFiles?.set(coverageFiles)
                it?.getReportDir()?.set(reportDir)
                it?.classFolders?.set(classFileCollection!!.files)
                it?.getSourceFolders()?.set(sourceFolders!!.files)
                it?.getTabWidth()?.set(tabWidth)
                it?.getReportName()?.set(reportName)
            }
    }

    class CreationAction(
        testComponentProperties: TestComponentImpl,
        private val jacocoAntConfiguration: Configuration
    ) : VariantTaskCreationAction<JacocoReportTask, TestComponentImpl>(testComponentProperties) {
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
            task.description = ("Creates JaCoCo test coverage report from data gathered on the "
                    + "device.")
            task.reportName = creationConfig.name
            val testedVariant = creationConfig.testedVariant
            task.jacocoClasspath = jacocoAntConfiguration
            creationConfig
                .artifacts
                .setTaskInputToFinalProduct(
                    InternalArtifactType.CODE_COVERAGE,
                    task.coverageDirectories
                )
            task.classFileCollection = testedVariant.artifacts.getAllClasses()
            task.sourceFolders = creationConfig
                .services.fileCollection(testedVariant::javaSources)
            task.reportDir = testedVariant.paths.coverageReportDir.get().asFile
        }
    }

    internal interface JacocoWorkParameters : WorkParameters {
        val coverageFiles: SetProperty<File>
        fun getReportDir(): DirectoryProperty
        val classFolders: SetProperty<File>
        fun getSourceFolders(): SetProperty<File>
        fun getTabWidth(): Property<Int>
        fun getReportName(): Property<String>
    }

    internal abstract class JacocoReportWorkerAction @Inject constructor() :
        WorkAction<JacocoWorkParameters> {
        override fun execute() {
            try {
                generateReport(
                    parameters.coverageFiles.get(),
                    parameters.getReportDir().asFile.get(),
                    parameters.classFolders.get(),
                    parameters.getSourceFolders().get(),
                    parameters.getTabWidth().get(),
                    parameters.getReportName().get()
                )
            } catch (e: IOException) {
                throw UncheckedIOException("Unable to generate Jacoco report", e)
            }
        }

        companion object {
            private val logger = Logging.getLogger(
                JacocoReportWorkerAction::class.java
            )

            @JvmStatic
            @VisibleForTesting
            @kotlin.jvm.Throws(IOException::class)
            fun generateReport(
                coverageFiles: Collection<File>,
                reportDir: File,
                classFolders: Collection<File>,
                sourceFolders: Collection<File?>,
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
                    if (!name.endsWith(".class")
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
