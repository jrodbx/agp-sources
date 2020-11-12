/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.scope.InternalArtifactType
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import java.util.function.Consumer

/**
 * Implementation of a [ArtifactCollection] on top of a main collection, and a component
 * filter, coming from a list of files published by sub-modules as [InternalArtifactType.PACKAGED_DEPENDENCIES]

 *
 * The main use case for this is building an ArtifactCollection that represents the runtime
 * dependencies of a test app, minus the runtime dependencies of the tested app (to avoid duplicated
 * classes during runtime).
 */
class FilteredArtifactCollection(private val filteringSpec: FilteringSpec) : ArtifactCollection {
    override fun getArtifactFiles() = filteringSpec.getFilteredFileCollection()
    override fun getArtifacts() = filteringSpec.getArtifactFiles()
    override fun getFailures(): Collection<Throwable> = filteringSpec.artifacts.failures
    override fun iterator() = artifacts.iterator()
    override fun spliterator() = artifacts.spliterator()
    override fun forEach(action: Consumer<in ResolvedArtifactResult>) = artifacts.forEach(action)
}
