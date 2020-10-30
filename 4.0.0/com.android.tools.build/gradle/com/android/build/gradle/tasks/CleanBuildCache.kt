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

import com.android.annotations.concurrency.Immutable
import com.android.build.gradle.internal.tasks.factory.TaskCreationAction
import com.android.builder.utils.FileCache
import java.io.IOException
import org.gradle.api.DefaultTask
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.TaskAction
import java.lang.NullPointerException

/** Task to clean the build cache.  */
abstract class CleanBuildCache : DefaultTask() {

    private lateinit var buildCache: FileCache

    fun setBuildCache(buildCache: FileCache) {
        this.buildCache = buildCache
    }

    @TaskAction
    @Throws(IOException::class)
    fun clean() {
        buildCache.delete()
    }

    @Immutable
    class CreationAction(private val buildCache: FileCache) :
        TaskCreationAction<CleanBuildCache>() {

        override val name: String = "cleanBuildCache"
        override val type: Class<CleanBuildCache> = CleanBuildCache::class.java

        override fun configure(task: CleanBuildCache) {
            task.description = "Deletes the build cache directory."
            task.group = BasePlugin.BUILD_GROUP
            task.setBuildCache(buildCache)
        }
    }
}
