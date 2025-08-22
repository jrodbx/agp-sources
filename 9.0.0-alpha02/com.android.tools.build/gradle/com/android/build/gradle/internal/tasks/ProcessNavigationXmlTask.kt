/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.SdkConstants.FD_RES_NAVIGATION
import com.android.build.gradle.internal.DependencyResourcesComputer
import com.android.build.gradle.internal.component.AarCreationConfig
import com.android.build.gradle.internal.component.ApkCreationConfig
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.component.ConsumableCreationConfig
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.fromDisallowChanges
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.ide.common.resources.FileResourceNameValidator
import com.android.ide.common.xml.XmlFormatPreferences
import com.android.ide.common.xml.XmlFormatStyle
import com.android.ide.common.xml.XmlPrettyPrinter
import com.android.manifmerger.NavigationXmlLoader
import com.android.resources.ResourceFolderType
import com.android.utils.FileUtils
import com.android.utils.forEach
import com.google.common.io.Files
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider
import org.w3c.dom.Node
import java.io.File

/**
 * Substituting deep links placeholders for navigation xmls
 */
@CacheableTask
@BuildAnalyzer(primaryTaskCategory = TaskCategory.ANDROID_RESOURCES)
abstract class ProcessNavigationXmlTask : NonIncrementalTask() {

    @get:Nested
    abstract val resources: SetProperty<DependencyResourcesComputer.ResourceSourceSetInput>

    @get:Optional
    @get:Classpath
    abstract val runtimeDependenciesNavigationFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val manifestPlaceholders: MapProperty<String, String>

    @get:Input
    abstract val finalNavigationTransformation: Property<Boolean>

    @get:Optional
    @get:Input
    abstract val applicationId: Property<String>

    override fun doTaskAction() {
        val placeholders = getPlaceholders()
        runtimeDependenciesNavigationFiles.forEach { processNavigationResources(it, placeholders) }
        // handle local resources after dependencies in order to overwrite dependencies
        // with application navigation in case names are the same
        resources.get()
            .forEach {
                it.sourceDirectories.files.forEach {
                    processNavigationResources(
                        it,
                        placeholders
                    )
                }
            }
    }

    private fun processNavigationResources(file: File, placeholders: Map<String, String>) {
        val resources = File(file, FD_RES_NAVIGATION).listFiles() ?: arrayOf<File>()

        validateResourceFileNames(resources)

        resources.forEach { transformFile(it, placeholders) }
    }

    private fun validateResourceFileNames(files: Array<File>) {
        files.forEach { file ->
            FileResourceNameValidator.validate(file, ResourceFolderType.XML)
        }
    }

    private fun getPlaceholders() = manifestPlaceholders.get().let {
        if (finalNavigationTransformation.get()) {
            it.plus(
                "applicationId" to applicationId.get()
            )
        } else it
    }

    private fun transformFile(original: File, placeholdersValues: Map<String, String>) {
        val navigationId = original.name.replace(Regex("\\.xml$"), "")
        original.inputStream().use { inputStream ->
            val document = NavigationXmlLoader.load(navigationId, original, inputStream)
            val root = document.getXml() ?: return
            performPlaceHolderSubstitution(root, placeholdersValues)
            val updatedXml = XmlPrettyPrinter.prettyPrint(
                root,
                XmlFormatPreferences.defaults(),
                XmlFormatStyle.get(root),
                null,
                false
            )

            val toBeGenerated =
                FileUtils.join(outputDir.get().asFile, FD_RES_NAVIGATION, original.name)
            Files.createParentDirs(toBeGenerated)
            toBeGenerated.createNewFile()
            toBeGenerated.writeText(updatedXml)
        }
    }

    private fun performPlaceHolderSubstitution(
        node: Node,
        placeholdersValues: Map<String, String>
    ) {
        if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == "deepLink") {
            node.attributes?.forEach { attribute ->
                var content = attribute.nodeValue
                placeholdersValues.forEach { (key, value) ->
                    content = content.replace("\${$key}", value)
                }
                attribute.nodeValue = content
            }
            return
        }

        // First fix the attributes.
        node.attributes?.forEach {
            performPlaceHolderSubstitution(it, placeholdersValues)
        }

        // Now fix the children.
        node.childNodes.forEach {
            if (it.nodeType != Node.TEXT_NODE) performPlaceHolderSubstitution(it, placeholdersValues)
        }
    }

    abstract class CreationAction(
        creationConfig: ComponentCreationConfig
    ) : VariantTaskCreationAction<ProcessNavigationXmlTask, ComponentCreationConfig>(
        creationConfig
    )
    {
        override val name: String
            get() = creationConfig.computeTaskNameInternal(
                "process",
                "NavigationResources"
            )
        override val type: Class<ProcessNavigationXmlTask>
            get() = ProcessNavigationXmlTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ProcessNavigationXmlTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ProcessNavigationXmlTask::outputDir
            ).on(InternalArtifactType.UPDATED_NAVIGATION_XML)
        }

        override fun configure(task: ProcessNavigationXmlTask) {
            super.configure(task)
            task.manifestPlaceholders.setDisallowChanges(
                creationConfig.manifestPlaceholdersCreationConfig?.placeholders,
                handleNullable = {
                    empty()
                }
            )

            creationConfig.sources.res { resSources ->
                val resourceMap = resSources.getVariantSourcesWithFilter { !it.isGenerated }

                resourceMap.map { (name, providerOfDirectories) ->
                    task.resources.add(
                        creationConfig.services.newInstance(DependencyResourcesComputer.ResourceSourceSetInput::class.java)
                            .also { it.sourceDirectories.fromDisallowChanges(providerOfDirectories) }
                    )
                }
            }
        }
    }

    open class LibraryCreationAction(
        creationConfig: ComponentCreationConfig
    ) : CreationAction(creationConfig) {

        override val type: Class<ProcessNavigationXmlTask>
            get() = ProcessNavigationXmlTask::class.java

        override fun configure(task: ProcessNavigationXmlTask) {
            super.configure(task)
            task.finalNavigationTransformation.setDisallowChanges(false)
            task.applicationId.disallowChanges()
            task.runtimeDependenciesNavigationFiles.disallowChanges()
        }
    }

    class ApplicationCreationAction(creationConfig: ComponentCreationConfig) :
        CreationAction(creationConfig) {

        override fun configure(task: ProcessNavigationXmlTask) {
            super.configure(task)
            task.finalNavigationTransformation.setDisallowChanges(creationConfig.componentType.isApk)
            task.applicationId.set(creationConfig.applicationId)
            task.runtimeDependenciesNavigationFiles.fromDisallowChanges(
                creationConfig.variantDependencies.getArtifactCollection(
                    AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                    AndroidArtifacts.ArtifactScope.ALL,
                    AndroidArtifacts.ArtifactType.NAVIGATION_XML
                ).artifactFiles
            )
        }
    }
}
