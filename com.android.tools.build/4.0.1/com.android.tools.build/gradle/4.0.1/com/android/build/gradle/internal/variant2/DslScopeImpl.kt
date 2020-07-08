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

package com.android.build.gradle.internal.variant2

import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.dsl.DslVariableFactory
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.scope.BuildFeatureValues
import com.android.builder.errors.IssueReporter
import org.gradle.api.file.ProjectLayout
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import java.io.File

class DslScopeImpl(
        override val issueReporter: IssueReporter,
        override val deprecationReporter: DeprecationReporter,
        override val objectFactory: ObjectFactory,
        override val logger: Logger,
        override val buildFeatures: BuildFeatureValues,
        override val providerFactory: ProviderFactory,
        override val variableFactory: DslVariableFactory,
        override val projectLayout: ProjectLayout,
        private val fileResolver: (Any) -> File
) : DslScope {
    override fun file(file: Any): File = fileResolver.invoke(file)
}