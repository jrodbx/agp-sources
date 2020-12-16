/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.model.PrefabConfigurationState
import com.android.build.gradle.tasks.ExternalNativeBuildTaskUtils
import java.io.File

/**
 * Logic responsible for determining whether configuration JSON for the given ABI should be
 * regenerated.
 */
class JsonGenerationInvalidationState(
    private val forceRegeneration: Boolean,
    private val expectedJson: File,
    private val commandFile: File,
    currentBuildCommand: String,
    previousBuildCommand: String,
    private val dependentBuildFiles: List<File>,
    currentPrefabConfiguration: PrefabConfigurationState,
    previousPrefabConfiguration: PrefabConfigurationState) {
    private val rebuildDueToMissingJson = !expectedJson.exists()
    private val rebuildDueToMissingPreviousCommand = !commandFile.exists()
    private val rebuildDueToChangeInCommandFile = previousBuildCommand != currentBuildCommand
    private val dependentBuildFilesChanged : List<File>
        get() {
            if (!expectedJson.exists()) {
                return listOf()
            }
            return dependentBuildFiles.filter { buildFile ->
                !ExternalNativeBuildTaskUtils.fileIsUpToDate(buildFile, expectedJson)
            }
        }
    private val rebuildDueToDependentBuildFileChanged = dependentBuildFilesChanged.isNotEmpty()
    private val rebuildDueToPrefabConfigurationChange =
        currentPrefabConfiguration != previousPrefabConfiguration
    val softRegeneration = rebuildDueToDependentBuildFileChanged
            && !forceRegeneration
            && !rebuildDueToMissingJson
            && !rebuildDueToMissingPreviousCommand
            && !rebuildDueToChangeInCommandFile
    val rebuild = forceRegeneration
            || rebuildDueToMissingJson
            || rebuildDueToMissingPreviousCommand
            || rebuildDueToChangeInCommandFile
            || rebuildDueToDependentBuildFileChanged
            || rebuildDueToPrefabConfigurationChange
    val rebuildReasons: List<String>
        get()  {
            val messages = mutableListOf<String>()
            val softRegenerateMessage =
                if (softRegeneration) ""
                else ", will remove stale json folder"
            if (forceRegeneration) {
                messages += "- force flag, will remove stale json folder"
            }

            if (rebuildDueToMissingJson) {
                messages += "- expected json $expectedJson file is not present$softRegenerateMessage"
            }

            if (rebuildDueToMissingPreviousCommand) {
                messages += "- missing previous command file $commandFile$softRegenerateMessage"
            }

            if (rebuildDueToChangeInCommandFile) {
                messages += "- command changed from previous$softRegenerateMessage"
            }

            if (rebuildDueToDependentBuildFileChanged) {
                messages += "- a dependent build file changed"
                for (dependentBuildFile in dependentBuildFilesChanged) {
                    messages += "  - ${dependentBuildFile.absolutePath}"
                }
            }
            if (rebuildDueToPrefabConfigurationChange) {
                messages += "- prefab configuration has changed"
            }

            return messages
        }
}
