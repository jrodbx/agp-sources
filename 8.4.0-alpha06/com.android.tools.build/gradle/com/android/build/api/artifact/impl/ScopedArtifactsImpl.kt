/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.api.artifact.impl

import com.android.build.api.artifact.Artifact
import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.ScopedArtifactsOperation
import com.android.build.gradle.internal.scope.getDirectories
import com.android.build.gradle.internal.scope.getIntermediateOutputPath
import com.android.build.gradle.internal.scope.getRegularFiles
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import java.util.concurrent.atomic.AtomicBoolean

class ScopedArtifactsImpl(
    private val scopeName: String,
    private val variantIdentifier: String,
    private val projectLayout: ProjectLayout,
    private val fileCollectionCreator: () -> ConfigurableFileCollection,
): ScopedArtifacts {

    /**
     * Internal function to set the initial provider of a [ScopedArtifact]. Because AGP tasks
     * creation happens after the variant API ran, we cannot use the append method as they would
     * be appended after a third party decided to replace the artifacts.
     *
     * Instead, AGP task creation must use these [setInitialContent] methods to declare itself as
     * the initial provider of the artifact after third party's variant block has ran.
     */
    internal fun setInitialContent(type: ScopedArtifact, artifacts: ArtifactsImpl, incomingType: Artifact.Single<*>) {
        getScopedArtifactsContainer(type).initialScopedContent.from(
            artifacts.get(incomingType)
        )
    }

    internal fun setInitialContent(type: ScopedArtifact, fileCollection: FileCollection) {
        getScopedArtifactsContainer(type).initialScopedContent
            .from(fileCollection)
    }

    override fun <T : Task> use(taskProvider: TaskProvider<T>): ScopedArtifactsOperationImpl<T> =
        ScopedArtifactsOperationImpl(
            this,
            taskProvider,
            projectLayout,
            fileCollectionCreator,
        )

    class ScopedArtifactsContainer(
        fileCollectionCreator: () -> ConfigurableFileCollection,
    ) {
        // this is more complicated than it should be but the timing of the variant API makes it
        // necessary to use three [ConfigurableFileCollection] to hold each container data.
        //
        // 1. initialScopeContent is a file collection that will hold the content produced by AGP.
        //    Because the variant API runs before the AGP [Task]s are created, we need to store
        //    those tasks output in a specific file collection. Using the normal API would make
        //    our content not available to people transforming the artifact.
        //
        // 2. currentScopeContent is the file collection that holds the current artifact content.
        //    it is initialized with the initialScopedContent which is empty at the time of
        //    initialization (but will eventually get populated when our TaskManagers run).
        //    Third-party can append to the content of this file collection. Remember that if the
        //    content is transformed, the current file collection will be used as the input to the
        //    transform [Task] so the [FileCollection] cannot just be reset, a new one must be
        //    created to hold the new content.
        //
        // 3. finalScopedContent is the file collection used by all consumers of the artifact. This
        //    file collection never changes, only its content will be reset to the new
        //    currentScopeContent each it changes to ensure code consuming this file collection
        //    will only have access to the last version of the artifact.
        val initialScopedContent = fileCollectionCreator()

        var currentScopedContent = fileCollectionCreator().also {
            it.from(this.initialScopedContent)
        }
        val finalScopedContent = fileCollectionCreator().also {
            it.from(currentScopedContent)
        }

        val artifactsAltered = AtomicBoolean(false)

        private val listOfProviders = mutableListOf(initialScopedContent)
        /**
         * Reset the current provider of the artifact the new file collection and make sure the
         * final version points to the new content.
         */
        @Synchronized
        fun setNewContent(newScopedContent: ConfigurableFileCollection) {
            artifactsAltered.set(true)
            listOfProviders.add(newScopedContent)
            currentScopedContent = newScopedContent
            finalScopedContent.setFrom(currentScopedContent)
        }
    }

    private val scopedArtifacts = mutableMapOf<ScopedArtifact, ScopedArtifactsContainer>()
    private val internalScopedArtifacts = mutableMapOf<InternalScopedArtifact, ScopedArtifactsContainer>()

    internal fun getScopedArtifactsContainer(
        type: ScopedArtifact,
    ) = scopedArtifacts.getOrPut(type) {
        ScopedArtifactsContainer(fileCollectionCreator)
    }

    internal fun getScopedArtifactsContainer(
        type: InternalScopedArtifact,
    ) = internalScopedArtifacts.getOrPut(type) {
        ScopedArtifactsContainer(fileCollectionCreator)
    }

    /**
     * Publish the final version of [type] artifact under a different internal [into] type. This
     * is useful when some code path produce different versions of [into] artifacts from the ones
     * registered under [type] and consumers just want to use the [into] type irrespectively.
     */
    internal fun republish(type: ScopedArtifact, into: InternalScopedArtifact) {
        getScopedArtifactsContainer(into)
            .initialScopedContent
            .from(getFinalArtifacts(type))
    }

    /**
     * Publish the current version of [type] under a different internal [into] type. This is useful
     * when some code path requires to have access to an artifact [type] before certain internal
     * transforms are potentially applied.
     */
    internal fun publishCurrent(type: ScopedArtifact, into: InternalScopedArtifact) {
        getScopedArtifactsContainer(into)
            .initialScopedContent
            .from(
                getScopedArtifactsContainer(type).currentScopedContent
            )
    }

    internal fun getFinalArtifacts(type: ScopedArtifact): FileCollection =
        getScopedArtifactsContainer(type).finalScopedContent

    internal fun getFinalArtifacts(type: InternalScopedArtifact): FileCollection =
        getScopedArtifactsContainer(type).finalScopedContent

    class ScopedArtifactsOperationImpl<T: Task>(
        private val scopedArtifacts: ScopedArtifactsImpl,
        private val taskProvider: TaskProvider<T>,
        private val projectLayout: ProjectLayout,
        private val fileCollectionCreator: () -> ConfigurableFileCollection,
    ): ScopedArtifactsOperation<T> {

        override fun toAppend(to: ScopedArtifact, with: (T) -> Property<out FileSystemLocation>) {
            // register the new content to the current artifact collection
            scopedArtifacts.getScopedArtifactsContainer(to).currentScopedContent
                .from(taskProvider.flatMap(with))
            // and sets the output path.
            taskProvider.configure {
                when (val provider = with(it)) {
                    is RegularFileProperty -> setContentPath(to, provider, taskProvider.name)
                    is DirectoryProperty -> setContentPath(to, provider, taskProvider.name)
                    else -> throw RuntimeException("Only RegularFileProperty or DirectoryProperty" +
                            " instances are supported, got ${provider.javaClass}")
                }
            }
        }

        override fun toGet(
            type: ScopedArtifact,
            inputJars: (T) -> ListProperty<RegularFile>,
            inputDirectories: (T) -> ListProperty<Directory>
        ) {
            val scopedContent = scopedArtifacts.getScopedArtifactsContainer(type)
                .finalScopedContent
            taskProvider.configure { task ->
                inputJars(task).set(scopedContent.getRegularFiles(projectLayout.projectDirectory))
                inputDirectories(task).set(scopedContent.getDirectories(projectLayout.projectDirectory))
            }
        }

        override fun toTransform(
            type: ScopedArtifact,
            inputJars: (T) -> ListProperty<RegularFile>,
            inputDirectories: (T) -> ListProperty<Directory>,
            into: (T) -> RegularFileProperty
        ) {
            val artifactContainer = scopedArtifacts.getScopedArtifactsContainer(type)
            val currentScopedContent = artifactContainer.currentScopedContent
            // set the [Task] input and output fields.
            taskProvider.configure { task ->
                inputJars(task).set(
                    currentScopedContent.getRegularFiles(projectLayout.projectDirectory)
                )
                inputDirectories(task).set(
                    currentScopedContent.getDirectories(projectLayout.projectDirectory)
                )
                setContentPath(type, into(task), taskProvider.name)
            }
            resetContentProvider(type, into)
        }

        internal fun toTransform(
            type: ScopedArtifact,
            inputJars: (T) -> ConfigurableFileCollection,
            inputDirectories: (T) -> ConfigurableFileCollection,
            intoJarDirectory: (T) -> DirectoryProperty,
            intoDirDirectory: (T) -> DirectoryProperty,
        ) {
            val artifactContainer = scopedArtifacts.getScopedArtifactsContainer(type)
            val currentScopedContent = artifactContainer.currentScopedContent
            val newContent = fileCollectionCreator.invoke()
            // set the [Task] input and output fields.
            taskProvider.configure { task ->
                inputJars(task).setFrom(
                    currentScopedContent.getRegularFiles(projectLayout.projectDirectory)
                )
                inputDirectories(task).setFrom(
                    currentScopedContent.getDirectories(projectLayout.projectDirectory)
                )
                setContentPath(type, intoJarDirectory(task), taskProvider.name, "jars")
                setContentPath(type, intoDirDirectory(task), taskProvider.name, "dirs")
            }
            newContent.from(
                // TODO: this is a hack, we should instead have all the tasks like jacoco and asm tasks
                // that transform classes to produce a list of jars instead of a directory containing
                // jars.
                // see bug 254666753
                taskProvider.flatMap { task -> intoJarDirectory(task).map {
                    it.asFileTree.files
                } },
                taskProvider.flatMap { task -> intoDirDirectory(task) }
            )
            scopedArtifacts.getScopedArtifactsContainer(type).setNewContent(newContent)
        }

        internal fun toFork(
            type: ScopedArtifact,
            inputJars: (T) -> ConfigurableFileCollection,
            inputDirectories: (T) -> ConfigurableFileCollection,
            intoJarDirectory: (T) -> DirectoryProperty,
            intoDirDirectory: (T) -> DirectoryProperty,
            intoType: InternalScopedArtifact,
        ) {
            val artifactContainer = scopedArtifacts.getScopedArtifactsContainer(type)
            val currentScopedContent = artifactContainer.currentScopedContent
            val newContent = fileCollectionCreator.invoke()
            // set the [Task] input and output fields.
            taskProvider.configure { task ->
                inputJars(task).setFrom(
                    currentScopedContent.getRegularFiles(projectLayout.projectDirectory)
                )
                inputDirectories(task).setFrom(
                    currentScopedContent.getDirectories(projectLayout.projectDirectory)
                )
                setContentPath(type, intoJarDirectory(task), taskProvider.name, "jars")
                setContentPath(type, intoDirDirectory(task), taskProvider.name, "dirs")
            }
            newContent.from(
                // TODO: this is a hack, we should instead have all the tasks like jacoco and asm tasks
                // that transform classes to produce a list of jars instead of a directory containing
                // jars.
                // see bug 254666753.
                taskProvider.flatMap { task -> intoJarDirectory(task).map {
                    it.asFileTree.files
                } },
                taskProvider.flatMap { task -> intoDirDirectory(task) }
            )
            scopedArtifacts.getScopedArtifactsContainer(intoType)
                .initialScopedContent.from(newContent)
        }

        override fun toReplace(type: ScopedArtifact, into: (T) -> RegularFileProperty) {
            taskProvider.configure { task ->
                setContentPath(type, into(task), taskProvider.name)
            }
            resetContentProvider(type, into)
        }

        private fun setContentPath(
            type: ScopedArtifact,
            into: RegularFileProperty,
            vararg paths: String,
        ) {
            into.set(
                type.getIntermediateOutputPath(
                    buildDirectory = projectLayout.buildDirectory,
                    variantIdentifier = scopedArtifacts.variantIdentifier,
                    paths = arrayOf(scopedArtifacts.scopeName, *paths) ,
                    forceFilename = type.name().lowercase().plus(".jar")
                )
            )
        }

        private fun setContentPath(
            type: ScopedArtifact,
            into: DirectoryProperty,
            vararg paths: String,
        ) {
            into.set(
                type.getIntermediateOutputPath(
                    buildDirectory = projectLayout.buildDirectory,
                    variantIdentifier = scopedArtifacts.variantIdentifier,
                    paths = paths,
                )
            )
        }

        private fun resetContentProvider(
            type: ScopedArtifact,
            into: (T) -> RegularFileProperty,
        ) {
            // create a new holder for the content and update the current provider in the artifact
            // container.
            fileCollectionCreator.invoke().also { newScopedContent ->
                newScopedContent.setFrom(
                    taskProvider.flatMap { task -> into(task) }
                )
                scopedArtifacts.getScopedArtifactsContainer(type).setNewContent(newScopedContent)
            }
        }
    }
}
