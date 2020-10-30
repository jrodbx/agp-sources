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

import com.android.tools.r8.origin.Origin
import java.io.File

/** Adapter from [DependencyGraphUpdater] to [D8DesugarGraphConsumer]. */
class D8DesugarGraphConsumerAdapter(private val desugarGraphUpdater: DependencyGraphUpdater<File>) :
    D8DesugarGraphConsumer {

    // D8 currently emits the edges in reverse, from `dependency` to `dependent`
    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun accept(dependency: Origin, dependent: Origin) {
        check(dependent != dependency) { "Can't add an edge from a node to itself: $dependent" }

        val dependentFile = D8DesugarGraphGenerator.originToFile(dependent)
        val dependencyFile = D8DesugarGraphGenerator.originToFile(dependency)
        // Compare paths as lint doesn't allow comparing File objects using `equals`
        if (dependentFile.path != dependencyFile.path) {
            desugarGraphUpdater.addEdge(dependentFile, dependencyFile)
        }
    }
}