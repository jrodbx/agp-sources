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

package com.android.build.gradle.internal.tasks

import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.pipeline.OriginalStream
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.VariantScope
import com.google.common.base.Charsets
import com.google.common.io.Files
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.concurrent.Callable

private const val PROGUARD_CONCURRENCY_LIMIT = 4
private val proguardWorkLimiter: WorkLimiter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    WorkLimiter(PROGUARD_CONCURRENCY_LIMIT)
}

@CacheableTask
abstract class ProguardTask : ProguardConfigurableTask() {

    @get:OutputFile
    abstract val shrunkJar: RegularFileProperty

    @get:OutputFile
    val seedsFile: File by lazy {
        mappingFile.get().asFile.resolveSibling("seeds.txt")
    }

    @get:OutputFile
    val usageFile: File by lazy {
        mappingFile.get().asFile.resolveSibling("usage.txt")
    }

    @get:Classpath
    abstract val bootClasspath: ConfigurableFileCollection
    @get:Classpath
    abstract val fullBootClasspath: ConfigurableFileCollection

    @get:Input
    abstract val keepRules: ListProperty<String>
    @get:Input
    abstract val dontWarnRules: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val obfuscationEnabled: Property<Boolean>
    @get:Input
    @get:Optional
    abstract val optimizationEnabled: Property<Boolean>
    @get:Input
    @get:Optional
    abstract val shrinkingEnabled: Property<Boolean>

    override fun doTaskAction() {
        // only run PROGUARD_CONCURRENCY_LIMIT proguard invocations at a time (across projects)
        try {
            if (!mappingFile.isPresent) {
                throw RuntimeException("printMapping not initialized")
            }
            val printMappingFile = mappingFile.get().asFile
            proguardWorkLimiter
                .limit(
                    Callable<Void> {
                        ProguardDelegate(
                            classes.files,
                            resources.files,
                            referencedClasses.files,
                            referencedResources.files,
                            shrunkJar.get().asFile,
                            mappingFile.get().asFile,
                            seedsFile,
                            usageFile,
                            testedMappingFile.singleOrNull(),
                            configurationFiles.files,
                            bootClasspath.files,
                            fullBootClasspath.files,
                            keepRules.get(),
                            dontWarnRules.get(),
                            optimizationEnabled.getOrNull(),
                            shrinkingEnabled.getOrNull(),
                            obfuscationEnabled.getOrNull()
                        ).run()
                        // make sure the mapping file is always created. Since the file is
                        // always published as
                        // an artifact, it's important that it is always present even if
                        // empty so that it
                        // can be published to a repo.
                        if (!printMappingFile.isFile) {
                            Files.asCharSink(printMappingFile, Charsets.UTF_8).write("")
                        }
                        null
                    })

        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RuntimeException(e)
        }

    }

    class CreationAction(variantScope: VariantScope, isTestApplication: Boolean) :
        ProguardConfigurableTask.CreationAction<ProguardTask>(variantScope, isTestApplication) {

        override val name = variantScope.getTaskName("minify", "WithProguard")
        override val type = ProguardTask::class.java

        private val keepRules = mutableListOf<String>()
        private val dontWarnRules = mutableListOf<String>()
        private var obfuscationEnabled: Boolean? = null
        private var optimizationEnabled: Boolean? = null
        private var shrinkingEnabled: Boolean? = null

        override val defaultObfuscate = true

        init {
            // Publish the Proguarded classes and resources back to a Stream
            val shrunkClassesAndResourcesProvider = variantScope.artifacts
                .getFinalProduct(InternalArtifactType.SHRUNK_JAR)
            val project = variantScope.globalScope.project
            variantScope.transformManager.addStream(
                OriginalStream.builder(project, "shrunk_classes_and_resources")
                    .addContentTypes(TransformManager.CONTENT_JARS)
                    .addScopes(inputScopes)
                    .setFileCollection(project.layout.files(shrunkClassesAndResourcesProvider))
                    .build()
            )
        }

        override fun keep(keep: String) {
            keepRules.add(keep)
        }

        override fun keepAttributes() {
            // Intentional no-op
        }

        override fun dontWarn(dontWarn: String) {
            dontWarnRules.add(dontWarn)
        }

        override fun setActions(actions: PostprocessingFeatures) {
            obfuscationEnabled = actions.isObfuscate
            optimizationEnabled = actions.isOptimize
            shrinkingEnabled = actions.isRemoveUnusedCode
        }

        override fun configure(task: ProguardTask) {
            super.configure(task)

            task.bootClasspath.from(variantScope.bootClasspath)
            task.fullBootClasspath.from(variantScope.globalScope.fullBootClasspath)

            task.keepRules.set(this.keepRules)
            task.dontWarnRules.set(this.dontWarnRules)
            task.obfuscationEnabled.set(this.obfuscationEnabled)
            task.optimizationEnabled.set(this.optimizationEnabled)
            task.shrinkingEnabled.set(this.shrinkingEnabled)
        }

        override fun handleProvider(taskProvider: TaskProvider<out ProguardTask>) {
            super.handleProvider(taskProvider)

            variantScope.artifacts.producesFile(
                InternalArtifactType.SHRUNK_JAR,
                taskProvider,
                ProguardTask::shrunkJar,
                "minified.jar"
            )
        }
    }
}
