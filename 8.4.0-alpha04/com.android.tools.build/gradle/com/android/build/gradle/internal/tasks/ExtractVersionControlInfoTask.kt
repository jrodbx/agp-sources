/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.build.gradle.internal.component.ApplicationCreationConfig
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.buildanalyzer.common.TaskCategory
import com.android.builder.internal.packaging.IncrementalPackager.VERSION_CONTROL_INFO_FILE_NAME
import com.android.tools.idea.insights.proto.BuildStamp
import com.android.tools.idea.insights.proto.RepositoryInfo
import com.android.tools.idea.insights.proto.VersionControlSystem
import com.android.utils.FileUtils
import com.google.protobuf.TextFormat
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault
@BuildAnalyzer(primaryTaskCategory = TaskCategory.METADATA)
abstract class ExtractVersionControlInfoTask: NonIncrementalTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    @get:Optional
    abstract val gitHeadFile: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val gitRefsDir: DirectoryProperty

    @get:OutputFile
    abstract val vcInfoFile: RegularFileProperty

    @get:Input
    abstract val enabledByDefault: Property<Boolean>

    override fun doTaskAction() {
        if (!gitHeadFile.get().asFile.parentFile.exists()) {
            val gitMissingMessage = "When VCS tagging is enabled (which is by default in " +
                "release builds), the root project must be initialized with Git. " +
                "Currently, Git is the only supported VCS for this feature."
            if (enabledByDefault.get()) {
                logger.debug(gitMissingMessage)
                writeErrorInOutput(BuildStamp.GenerateErrorReason.NO_SUPPORTED_VCS_FOUND)
                return
            }
            throw RuntimeException(gitMissingMessage)
        }

        if (!gitHeadFile.get().asFile.exists()) {
            val headFileMissing = missingGitFileMessage("HEAD")
            if (enabledByDefault.get()) {
                logger.debug(headFileMissing)
                writeErrorInOutput(BuildStamp.GenerateErrorReason.NO_VALID_GIT_FOUND)
                return
            } else {
                throw RuntimeException(headFileMissing)
            }
        }

        val headFileContents = gitHeadFile.get().asFile.readText().trim()

        // The HEAD file will contain a ref to the branch file that contains the git SHA
        // If the HEAD is detached, it will contain the SHA
        val sha = if (headFileContents.startsWith("ref: ")) {
            val branchName = headFileContents.substringAfter("ref: refs/heads/")
            val branchFile = FileUtils.join(gitRefsDir.get().asFile, branchName)
            if (branchFile == null || !branchFile.exists()) {
                val branchFileMissing = missingGitFileMessage("refs/heads/$branchName")
                if (enabledByDefault.get()) {
                    logger.debug(branchFileMissing)
                    writeErrorInOutput(BuildStamp.GenerateErrorReason.NO_VALID_GIT_FOUND)
                    return
                } else {
                    throw RuntimeException(branchFileMissing)
                }
            }
            branchFile.readText().trim()
        } else {
            headFileContents
        }

        val repositoryInfoBuilder = RepositoryInfo.newBuilder().apply {
            system = VersionControlSystem.GIT
            localRootPath = "${"$"}PROJECT_DIR"
            revision = sha
        }

        val versionControlInfoBuilder =
            BuildStamp.newBuilder().apply { addRepositories(repositoryInfoBuilder.build()) }

        vcInfoFile.get().asFile.writeText(
            TextFormat.printer().printToString(versionControlInfoBuilder.build()))
    }

    private fun writeErrorInOutput(reason: BuildStamp.GenerateErrorReason) {
        val versionControlInfoBuilder =
            BuildStamp.newBuilder().apply {
                generateErrorReason = reason
            }
        vcInfoFile.get().asFile.writeText(
            TextFormat.printer().printToString(versionControlInfoBuilder.build()))
    }

    private fun missingGitFileMessage(filePath: String) =
        "When VCS tagging is enabled (which is by default in release builds), the project " +
        "must be initialized with Git. The file '.git/$filePath' in the project root is " +
        "missing, so the version control metadata cannot be included in the APK."

    class CreationAction(creationConfig: ApplicationCreationConfig):
        VariantTaskCreationAction<ExtractVersionControlInfoTask, ApplicationCreationConfig>(
            creationConfig
        ) {

        override val name: String
            get() = creationConfig.computeTaskName("extract", "VersionControlInfo")

        override val type: Class<ExtractVersionControlInfoTask>
            get() = ExtractVersionControlInfoTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<ExtractVersionControlInfoTask>) {
            super.handleProvider(taskProvider)
            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                ExtractVersionControlInfoTask::vcInfoFile
            ).withName(VERSION_CONTROL_INFO_FILE_NAME)
                .on(InternalArtifactType.VERSION_CONTROL_INFO_FILE)
        }

        override fun configure(task: ExtractVersionControlInfoTask) {
            super.configure(task)
            val rootDir = creationConfig.services.projectInfo.rootDir
            task.gitHeadFile.set(FileUtils.join(rootDir, ".git", "HEAD"))
            task.gitHeadFile.disallowChanges()
            // Note: whenever any branch has a commit, this will cause a "dirty" state for the task
            task.gitRefsDir.set(FileUtils.join(rootDir, ".git", "refs", "heads"))
            task.gitRefsDir.disallowChanges()
            task.enabledByDefault.setDisallowChanges(creationConfig.includeVcsInfo == null)
        }
    }
}
