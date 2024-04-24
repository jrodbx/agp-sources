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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.lint.AndroidLintWorkAction
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.google.common.io.Files
import java.net.URLClassLoader
import org.gradle.api.Project
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.net.URI
import javax.annotation.concurrent.GuardedBy

/**
 * Build service used to remove locks from jar files from lint's [URLClassLoader] after all lint
 * tasks have finished executing.
 */
abstract class LintClassLoaderBuildService :
    BuildService<BuildServiceParameters.None>, AutoCloseable
{

    // Whether or not to call AndroidLintWorkAction.dispose() in close() method.
    @get:Synchronized
    @set:Synchronized
    var shouldDispose: Boolean = false

    /**
     * Cache of jar hashes during a build, to avoid rehashing the jars repeatedly
     *
     * This is cleared at the end of each build
     */
    @GuardedBy("this")
    private val jarsToHashCode : MutableMap<List<URI>, HashCode> = mutableMapOf()

    //** Hash the contents of the given file collection */
    @Synchronized
    internal fun hashJars(classpath: Iterable<FileSystemLocation>): String {
        val uris = classpath.map { it.asFile.toURI() }
        val hashCode = jarsToHashCode.getOrPut(uris) {
            Hashing.combineOrdered(classpath.map {
                Files.asByteSource(it.asFile).hash(Hashing.murmur3_128())
            })
        }
        return hashCode.toString()
    }


    override fun close() {
        jarsToHashCode.clear()
        if (shouldDispose) {
            AndroidLintWorkAction.dispose()
        }
    }

    class RegistrationAction(
        project: Project
    ) : ServiceRegistrationAction<LintClassLoaderBuildService, BuildServiceParameters.None>(
            project,
            LintClassLoaderBuildService::class.java
        ) {

        override fun configure(parameters: BuildServiceParameters.None) {
        }
    }
}
