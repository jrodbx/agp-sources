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

package com.android.build.gradle.internal.cxx.services

import com.android.build.gradle.internal.cxx.model.CxxModuleModel
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.builder.errors.IssueReporter


/**
 * Private service key for [IssueReporter].
 */
private val EVAL_ISSUE_REPORTER_SERVICE_KEY = object : CxxServiceKey<IssueReporter> {
    override val type = IssueReporter::class.java
}

fun CxxModuleModel.issueReporter() : IssueReporter =
    services[EVAL_ISSUE_REPORTER_SERVICE_KEY]

internal fun createIssueReporterService(
    global: GlobalScope,
    services: CxxServiceRegistryBuilder) {
    services.registerFactory(EVAL_ISSUE_REPORTER_SERVICE_KEY) {
        global.dslScope.issueReporter
    }
}