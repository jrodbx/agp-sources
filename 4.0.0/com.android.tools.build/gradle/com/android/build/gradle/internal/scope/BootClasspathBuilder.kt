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

package com.android.build.gradle.internal.scope

import com.android.builder.core.LibraryRequest
import com.android.builder.errors.IssueReporter
import com.android.sdklib.AndroidVersion
import com.android.sdklib.OptionalLibrary
import com.google.common.base.Verify
import com.google.common.collect.ImmutableList
import java.io.File
import java.util.Objects
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import java.util.concurrent.ConcurrentHashMap

/** Utility methods for computing class paths to use for compilation.  */
object BootClasspathBuilder {

    private data class CacheKey(val target: AndroidVersion, val addAllOptionalLibraries: Boolean, val libraryRequests: List<LibraryRequest>)

    private val classpathCache = ConcurrentHashMap<CacheKey, List<File>>()

    /**
     * Computes the classpath for compilation.
     *
     * @param project target project
     * @param issueReporter sync issue reporter to report missing optional libraries with.
     * @param target the lazy provider for sdk information
     * @param annotationsJar the lazy provider for annotations jar file.
     * @param addAllOptionalLibraries true if all optional libraries should be included.
     * @param libraryRequests list of optional libraries to find and include.
     * @return a classpath as a [FileCollection]
     */
    fun computeClasspath(
        project: Project,
        issueReporter: IssueReporter,
        targetBootClasspath: Provider<List<File>>,
        targetAndroidVersion: Provider<AndroidVersion>,
        additionalLibraries: Provider<List<OptionalLibrary>>,
        optionalLibraries: Provider<List<OptionalLibrary>>,
        annotationsJar: Provider<File>,
        addAllOptionalLibraries: Boolean,
        libraryRequests: List<LibraryRequest>
    ): FileCollection {

        return project.files(
            targetBootClasspath.map { bootClasspath ->
                val target = targetAndroidVersion.get()
                val key = CacheKey(target, addAllOptionalLibraries, libraryRequests)

                classpathCache.getOrPut(key) {
                    val files = ImmutableList.builder<File>()
                    files.addAll(bootClasspath)

                    // add additional and requested optional libraries if any
                    files.addAll(
                        computeAdditionalAndRequestedOptionalLibraries(
                            additionalLibraries.get(), optionalLibraries.get(), addAllOptionalLibraries, libraryRequests, issueReporter
                        )
                    )

                    // add annotations.jar if needed.
                    if (target.apiLevel <= 15) {
                        files.add(annotationsJar.get())
                    }

                    files.build()
                }
            })
    }

    /**
     * Calculates the list of additional and requested optional library jar files
     *
     * @param androidTarget the Android Target
     * @param addAllOptionalLibraries overrides {@code libraryRequestsArg} and add all optional
     * libraries available.
     * @param libraryRequestsArg the list of requested optional libraries
     * @param issueReporter the issueReporter which is written to if a requested library is not
     * found
     * @return a list of File to add to the classpath.
     */
    fun computeAdditionalAndRequestedOptionalLibraries(
        additionalLibraries: List<OptionalLibrary>,
        optionalLibraries: List<OptionalLibrary>,
        addAllOptionalLibraries: Boolean,
        libraryRequestsArg: List<LibraryRequest>,
        issueReporter: IssueReporter
    ): List<File> {

        // iterate through additional libraries first, in case they contain
        // a requested library
        val libraryRequests = libraryRequestsArg.map { it.name }.toMutableSet()
        val files = ImmutableList.builder<File>()
        additionalLibraries
            .stream()
            .map<File> { lib ->
                val jar = lib.jar
                Verify.verify(
                    jar != null,
                    "Jar missing from additional library %s.",
                    lib.name
                )
                // search if requested, and remove from libraryRequests if so
                if (libraryRequests.contains(lib.name)) {
                    libraryRequests.remove(lib.name)
                }
                jar
            }
            .filter { Objects.nonNull(it) }
            .forEach { files.add(it) }

        // then iterate through optional libraries
        optionalLibraries
            .stream()
            .map<File> { lib ->
                // add to jar and remove from requests
                val libraryRequested = libraryRequests.contains(lib.name)
                if (addAllOptionalLibraries || libraryRequested) {
                    val jar = lib.jar
                    Verify.verify(
                        jar != null,
                        "Jar missing from optional library %s.",
                        lib.name
                    )
                    if (libraryRequested) {
                        libraryRequests.remove(lib.name)
                    }
                    jar
                } else {
                    null
                }
            }
            .filter { Objects.nonNull(it) }
            .forEach { files.add(it) }

        // look for not found requested libraries.
        for (library in libraryRequests) {
            issueReporter.reportError(
                IssueReporter.Type.OPTIONAL_LIB_NOT_FOUND,
                "Unable to find optional library: $library",
                library)
        }
        return files.build()
    }
}
