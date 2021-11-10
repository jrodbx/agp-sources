/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.CLASSES_JAR
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.NonIncrementalTask
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.dokka.DokkaBootstrapImpl
import org.jetbrains.dokka.DokkaConfiguration.ExternalDocumentationLink
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaDefaults
import org.jetbrains.dokka.DokkaSourceSetID
import org.jetbrains.dokka.DokkaSourceSetImpl
import org.jetbrains.dokka.ExternalDocumentationLinkImpl
import org.jetbrains.dokka.androidSdk
import org.jetbrains.dokka.androidX
import org.jetbrains.dokka.jdk
import org.jetbrains.dokka.kotlinStdlib
import org.jetbrains.dokka.toJsonString
import java.net.URLClassLoader
import java.util.concurrent.Callable
import java.util.function.BiConsumer

/**
 * Generate Java docs for java & kotlin sources using dokka.
 */
@CacheableTask
abstract class JavaDocGenerationTask : NonIncrementalTask() {

    @get:Input
    abstract override val projectPath: Property<String>

    @get:Input
    abstract val moduleVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @get:Classpath
    abstract val dokkaPlugins: ConfigurableFileCollection

    @get:Classpath
    abstract val dokkaRuntimeClasspath: ConfigurableFileCollection

    @TaskAction
    override fun doTaskAction() {
        val dokkaConfiguration =
            buildDokkaConfiguration(
                dokkaPlugins,
                sources,
                classpath,
                outputDirectory,
                path,
                projectPath.get(),
                moduleVersion.get())

        getRuntimeClassLoader(dokkaRuntimeClasspath).use {
            val bootstrapClass = it.loadClass(DokkaBootstrapImpl::class.qualifiedName)
            val bootstrapInstance = bootstrapClass.constructors.first().newInstance()
            val configureMethod =
                bootstrapClass.getMethod("configure", String::class.java, BiConsumer::class.java)
            val generateMethod = bootstrapClass.getMethod("generate")

            configureMethod.invoke(
                bootstrapInstance, dokkaConfiguration.toJsonString(), createProxyLogger())
            generateMethod.invoke(bootstrapInstance)
        }
    }

    /**
     * Dokka needs a separate classpath because it not only uses classes bundled from the kotlin
     * compiler, but also classes from intellij that would heavily interfere with other plugins
     * when loaded directly.
     *
     * Given Gradle leaks kotlin-stdlib to classpath of workers running in classloader isolation
     * and process isolation mode, we shouldn't launch Dokka via Gradle workers. Instead,
     * we need to load Dokka runtime dependencies manually and launch it using reflection.
     */
    private fun getRuntimeClassLoader(runtime: ConfigurableFileCollection): URLClassLoader {
        val runtimeJars = runtime.files
        return URLClassLoader(
            runtimeJars.map { it.toURI().toURL() }.toTypedArray(),
            ClassLoader.getSystemClassLoader().parent
        )
    }

    private fun buildDokkaConfiguration(
        plugins: ConfigurableFileCollection,
        sources: ConfigurableFileCollection,
        classpath: ConfigurableFileCollection,
        outputDirectory: DirectoryProperty,
        taskPath: String,
        moduleName: String,
        moduleVersion: String
    ): DokkaConfigurationImpl {
        return DokkaConfigurationImpl(
            moduleName = moduleName,
            moduleVersion = moduleVersion.takeIf { it != "unspecified" },
            outputDir = outputDirectory.asFile.get(),
            pluginsClasspath = plugins.files.toList(),
            sourceSets = listOf(buildDokkaSourceSetImpl(sources, classpath, taskPath))
        )
    }

    private fun buildDokkaSourceSetImpl(
        sources: ConfigurableFileCollection,
        classpath: ConfigurableFileCollection,
        taskPath: String
    ): DokkaSourceSetImpl {

        return DokkaSourceSetImpl(
            sourceSetID = DokkaSourceSetID(scopeId = taskPath, sourceSetName = taskPath),
            sourceRoots = sources.files ,
            classpath = classpath.files.toList(),
            externalDocumentationLinks = defaultExternalDocumentationLinks()
        )
    }

    private fun createProxyLogger(): BiConsumer<String, String> = BiConsumer { level, message ->
        when (level) {
            "debug" -> logger.debug(message)
            "info" -> logger.info(message)
            "progress" -> logger.lifecycle(message)
            "warn" -> logger.warn(message)
            "error" -> logger.error(message)
        }
    }

    private fun defaultExternalDocumentationLinks(): Set<ExternalDocumentationLinkImpl> {
        val links = mutableSetOf<ExternalDocumentationLinkImpl>()
        links.add(ExternalDocumentationLink.Companion.jdk(DokkaDefaults.jdkVersion))
        links.add(ExternalDocumentationLink.Companion.kotlinStdlib())
        links.add(ExternalDocumentationLink.Companion.androidSdk())
        links.add(ExternalDocumentationLink.Companion.androidX())
        return links
    }

    class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<JavaDocGenerationTask, ComponentCreationConfig> (
        creationConfig
    ) {
        override val type: Class<JavaDocGenerationTask>
            get() = JavaDocGenerationTask::class.java

        override val name: String
            get() = computeTaskName("javaDoc", "Generation")

        override fun handleProvider(taskProvider: TaskProvider<JavaDocGenerationTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts
                .setInitialProvider(taskProvider, JavaDocGenerationTask::outputDirectory)
                .on(InternalArtifactType.JAVA_DOC_DIR)
        }

        override fun configure(task: JavaDocGenerationTask) {
            super.configure(task)

            task.moduleVersion.setDisallowChanges(task.project.version.toString())

            val dokkaPluginConfig = task.project.configurations.detachedConfiguration(
                task.project.dependencies.create(DOKKA_BASE_PLUGIN),
                task.project.dependencies.create(DOKKA_JAVADOC_PLUGIN)
            )
            task.dokkaPlugins.fromDisallowChanges(dokkaPluginConfig)

            val runtimeConfig = task.project.configurations.detachedConfiguration(
                task.project.dependencies.create(DOKKA_CORE),
                task.project.dependencies.create(DOKKA_JAVADOC_PLUGIN)
            )
            task.dokkaRuntimeClasspath.fromDisallowChanges(runtimeConfig)

            task.sources.fromDisallowChanges(
                Callable { creationConfig.variantSources.getSourceFiles { it.javaDirectories } },
                Callable { creationConfig.variantSources.getSourceFiles { it.kotlinDirectories } }
            )

            task.classpath.fromDisallowChanges(
                creationConfig.sdkComponents.bootClasspath,
                creationConfig.getJavaClasspath(COMPILE_CLASSPATH, CLASSES_JAR, null)
            )
        }
    }

    companion object {
        // this version should be same as the version of dokka-core that gradle-core depends on.
        const val DOKKA_VERSION = "1.4.32"
        private const val DOKKA_GROUP = "org.jetbrains.dokka"
        const val DOKKA_CORE = "$DOKKA_GROUP:dokka-core:$DOKKA_VERSION"
        const val DOKKA_JAVADOC_PLUGIN = "$DOKKA_GROUP:javadoc-plugin:$DOKKA_VERSION"
        const val DOKKA_BASE_PLUGIN = "$DOKKA_GROUP:dokka-base:$DOKKA_VERSION"
    }
}
