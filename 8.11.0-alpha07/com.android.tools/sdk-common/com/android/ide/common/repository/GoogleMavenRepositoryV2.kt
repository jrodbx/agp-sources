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
package com.android.ide.common.repository

import com.android.ide.common.gradle.Dependency
import com.android.ide.common.gradle.Version
import java.util.function.Predicate

/**
 * Represents the repository that provides access to metadata about packages (aka groups),
 * artifacts, versions and their dependencies hosted on "maven.google.com".
 */
interface GoogleMavenRepositoryV2 {

    /**
     * Returns [Version] for a given group, artifact and [Predicate].
     */
    fun findVersion(
        groupId: String,
        artifactId: String,
        filter: Predicate<Version>?,
        allowPreview: Boolean = false
    ): Version?

    /**
     * Returns [Version] for a given group, artifact and filter.
     */
    fun findVersion(
        groupId: String,
        artifactId: String,
        filter: ((Version) -> Boolean)? = null,
        allowPreview: Boolean = false
    ): Version?

    /**
     * Returns [Dependency]s for a given group, artifact and [Version].
     */
    fun findCompileDependencies(
        groupId: String,
        artifactId: String,
        version: Version
    ): List<Dependency>

    companion object {

        /** Creates an instance of [GoogleMavenRepositoryV2]. */
        fun create(): GoogleMavenRepositoryV2 {
            return GoogleMavenRepositoryV2Impl()
        }
    }
}

private class GoogleMavenRepositoryV2Impl : GoogleMavenRepositoryV2 {

    override fun findVersion(
        groupId: String,
        artifactId: String,
        filter: Predicate<Version>?,
        allowPreview: Boolean
    ): Version? = null

    override fun findVersion(
        groupId: String,
        artifactId: String,
        filter: ((Version) -> Boolean)?,
        allowPreview: Boolean
    ): Version? = null

    override fun findCompileDependencies(
        groupId: String,
        artifactId: String,
        version: Version
    ): List<Dependency> = emptyList()
}
