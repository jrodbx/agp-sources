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
package com.android.builder.model.v2.ide

import java.io.File

/**
 * The base information for all generated artifacts
 */
interface AbstractArtifact {

    /**
     * @return the name of the task used to compile the code.
     */
    val compileTaskName: String?

    /**
     * Returns the name of the task used to generate the artifact output(s).
     *
     * @return the name of the task.
     */
    val assembleTaskName: String?

    /**
     * Set of folders containing the result of the compilation step(s)
     */
    val classesFolders: Set<File>

    /**
     * Returns names of tasks that need to be run when setting up the IDE project. After these
     * tasks have run, all the generated source files etc. that the IDE needs to know about should
     * be in place.
     */
    val ideSetupTaskNames: Set<String>

    /**
     * Returns all the source folders that are generated. This is typically folders for the R,
     * the aidl classes, and the renderscript classes.
     *
     * @return a list of folders.
     * @since 1.2
     */
    val generatedSourceFolders: Collection<File>

    /**
     * Removed: Previously returned model sync files which were never used
     *
     * Method remains temporarily to not break sync if someone syncs and rebuilds AGP without
     * rebuilding Studio. TODO(b/302301386): Remove this, perhaps after branching AGP 8.3.
     */
    @get:Deprecated("Removed: Previously returned model sync files which were never used. ")
    val modelSyncFiles: Collection<Void>

    /**
     * Generated class paths to include in the model. The name of the library to add is mapped to
     * the generated class file.
     */
    val generatedClassPaths: Map<String, File>

    /** List of bytecode transformations applied to this variant. */
    val bytecodeTransformations: Collection<BytecodeTransformation>
}
