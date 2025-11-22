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

package com.android.builder.dexing

import com.android.tools.r8.DesugarGraphConsumer
import com.android.tools.r8.origin.ArchiveEntryOrigin
import com.android.tools.r8.origin.Origin
import com.android.tools.r8.origin.PathOrigin
import java.io.File

/** Adapter from [DependencyGraphUpdater] to [DesugarGraphConsumer]. */
class D8DesugarGraphConsumerAdapter(private val desugarGraphUpdater: DependencyGraphUpdater<File>) :
    DesugarGraphConsumer {

    override fun accept(dependent: Origin, dependency: Origin) {
        check(dependent != dependency) { "Can't add an edge from a node to itself: $dependent" }

        val dependentFile = originToFile(dependent)
        val dependencyFile = originToFile(dependency)
        // Compare paths as lint doesn't allow comparing File objects using `equals`
        if (dependentFile.path != dependencyFile.path) {
            desugarGraphUpdater.addEdge(dependentFile, dependencyFile)
        }
    }

    /**
     * Returns the path to the regular file if the given [Origin] points to a regular file, or the
     * containing jar if the given [Origin] points to a jar entry.
     */
    private fun originToFile(origin: Origin): File {
        // This is the reverse of D8DiagnosticsHandler.getOrigin(ClassFileEntry)
        return when (origin) {
            is PathOrigin -> File(origin.part())
            is ArchiveEntryOrigin -> File(origin.parent().part())
            else -> error("Unexpected type ${origin.javaClass}")
        }
    }

    override fun finished() {
        // Nothing to do here
    }
}