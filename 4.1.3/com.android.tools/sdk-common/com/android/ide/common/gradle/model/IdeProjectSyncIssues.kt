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

package com.android.ide.common.gradle.model

import com.android.builder.model.ProjectSyncIssues
import com.android.builder.model.SyncIssue
import java.io.Serializable
import java.util.Collections

/** Creates a deep copy of a {@link ProjectSyncIssues}. */
data class IdeProjectSyncIssues(val syncIssues: Collection<SyncIssue> = Collections.emptyList()) :
    Serializable {

    constructor(project: ProjectSyncIssues, modelCache: ModelCache) : this(copySyncIssues(project, modelCache))

    companion object {
        private const val serialVersionUID: Long = 2L

        fun copySyncIssues(project: ProjectSyncIssues, modelCache: ModelCache): Collection<SyncIssue> {
            return IdeModel.copy(
              project.syncIssues, modelCache) { issue -> IdeSyncIssue(issue) }
        }
    }
}