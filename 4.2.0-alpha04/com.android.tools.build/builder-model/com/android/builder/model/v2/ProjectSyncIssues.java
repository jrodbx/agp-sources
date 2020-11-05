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

package com.android.builder.model.v2;

import com.android.annotations.NonNull;
import java.util.Collection;

/**
 * Model for a project's {@link SyncIssue}s.
 *
 * <p>This model should be fetched last (after other models), in order to have all the SyncIssue's
 * collected and delivered.
 */
public interface ProjectSyncIssues {

    /** Returns issues found during sync. */
    @NonNull
    Collection<SyncIssue> getSyncIssues();
}