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
package com.android.projectmodel

import com.android.ide.common.util.PathString

/**
 * Holds information about what is involved in producing a single Android artifact (a single output
 * of the project).
 *
 * New properties may be added in the future; clients that invoke the constructor are encouraged to
 * use Kotlin named arguments to stay source compatible.
 */
data class Artifact(
        /**
         * Contains the merged project [Config] for this [Artifact]. Note that this contains the resolved information
         * used by the build system to produce the artifact. This will be similar to what would be produced by merging
         * the matching [Config] instances from the [ConfigTable], but it is not guaranteed to be exactly the same.
         *
         * For example, the build system is likely to apply additional smarts when resolving dependencies (in order
         * to resolve conflicts between [Config] instances or remove redundant dependencies). It also may apply additional
         * manifest substitutions at resolve-time that didn't come directly from one of the constituent [Config] instances.
         *
         * For this reason, API consumers that need to know what went into the artifact should generally use this
         * [Config] rather than trying to compute it themselves.
         *
         * For well-formed projects, this [Config] is expected to override all possible metadata and to
         * supply a valid dependency list.
         */
        val resolved: Config = Config(),
        /**
         * List of class folders and .jar files containing the output of java compilation for this artifact.
         */
        val classFolders: List<PathString> = emptyList()
) {
    /**
     * The compile-time dependencies for this artifact. This is a shorthand for accessing the compile time dependencies from [resolved].
     */
    val compileDeps: List<ArtifactDependency>
        get() = resolved.compileDeps ?: emptyList()

    override fun toString(): String = printProperties(this, Artifact())
}
