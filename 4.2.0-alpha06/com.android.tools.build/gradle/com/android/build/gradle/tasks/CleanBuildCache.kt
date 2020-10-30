/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.StringOption
import com.android.prefs.AndroidLocation
import com.android.utils.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/** Task to clean the build cache. */
abstract class CleanBuildCache : DefaultTask() {

    // This task is never up-to-date
    @get:Internal
    abstract val buildCacheDir: Property<String>

    @TaskAction
    fun clean() {
        FileUtils.deletePath(File(buildCacheDir.get()))
        logger.warn(
            "The Android Gradle plugin's build cache has been deprecated.\n" +
                    DeprecationReporter.DeprecationTarget.AGP_BUILD_CACHE.getDeprecationTargetMessage() + "\n" +
                    "The $CLEAN_BUILD_CACHE_TASK_NAME task will also be removed in the same version."
        )
    }

    class CreationAction(private val globalScope: GlobalScope) :
        TaskCreationAction<CleanBuildCache>() {

        override val name: String = CLEAN_BUILD_CACHE_TASK_NAME
        override val type: Class<CleanBuildCache> = CleanBuildCache::class.java

        override fun configure(task: CleanBuildCache) {
            task.description = "Deletes the build cache directory."
            task.group = BasePlugin.BUILD_GROUP

            val buildCacheDir = globalScope.projectOptions[StringOption.BUILD_CACHE_DIR]?.let {
                globalScope.project.rootProject.file(it)
            } ?: File(AndroidLocation.getFolder(), "build-cache")
            task.buildCacheDir.setDisallowChanges(buildCacheDir.path)
        }
    }
}

private const val CLEAN_BUILD_CACHE_TASK_NAME = "cleanBuildCache"