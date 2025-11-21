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

package com.android.build.gradle.internal.errors

import com.android.builder.errors.IssueReporter
import com.android.builder.model.SyncIssue
import com.google.common.collect.ImmutableList

/**
 * Error reporter to be used during configuration only.
 *
 * This should not be used during tasks execution. Prefer [DefaultIssueReporter] inside tasks.
 */
abstract class SyncIssueReporter : IssueReporter() {

    abstract fun isInStandardEvaluationMode(): Boolean

    abstract val syncIssues: ImmutableList<SyncIssue>

    /**
     * Lock this issue handler and if any issue is reported after this is called, the handler
     * will throw just like as like it's running in non-sync mode.
     */
    abstract fun lockHandler()
}
