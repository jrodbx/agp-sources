/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.lint.model

import java.io.File

interface LintModelArtifact {
    val dependencies: LintModelDependencies
    val classOutputs: List<File>

    /**
     * Finds the library with the given [mavenName] (group id and artifact id)
     * in any transitive compile-dependency.
     *
     * Convenience method for accessing the compile dependencies and then looking up
     * the library there, since this is a very common operation.
     */
    fun findCompileDependency(mavenName: String): LintModelLibrary? =
        dependencies.compileDependencies.findLibrary(mavenName, direct = false)
}

interface LintModelJavaArtifact : LintModelArtifact

interface LintModelAndroidArtifact : LintModelArtifact {
    val applicationId: String
    val generatedResourceFolders: Collection<File>
    val generatedSourceFolders: Collection<File>
}

open class DefaultLintModelArtifact(
    override val dependencies: LintModelDependencies,
    override val classOutputs: List<File>
) : LintModelArtifact

class DefaultLintModelJavaArtifact(
    classFolders: List<File>,
    dependencies: LintModelDependencies
) : DefaultLintModelArtifact(
    dependencies = dependencies,
    classOutputs = classFolders
),
    LintModelJavaArtifact

class DefaultLintModelAndroidArtifact(
    override val applicationId: String,
    override val generatedResourceFolders: Collection<File>,
    override val generatedSourceFolders: Collection<File>,
    classOutputs: List<File>,
    dependencies: LintModelDependencies
) : DefaultLintModelArtifact(
    dependencies = dependencies,
    classOutputs = classOutputs
),
    LintModelAndroidArtifact
