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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.instrumentation.ClassesDataCache
import com.android.build.gradle.internal.instrumentation.ClassesHierarchyResolver
import com.android.build.gradle.internal.instrumentation.InstrumentationIssueHandler
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * A build service for creating [ClassesHierarchyResolver] objects that share the same cache.
 */
abstract class ClassesHierarchyBuildService : BuildService<BuildServiceParameters.None>,
    AutoCloseable {

    val issueHandler = InstrumentationIssueHandler()

    private val classesDataCache = ClassesDataCache()

    fun getClassesHierarchyResolverBuilder(): ClassesHierarchyResolver.Builder {
        return ClassesHierarchyResolver.Builder(classesDataCache)
    }

    override fun close() {
        classesDataCache.close()
        issueHandler.close()
    }

    class RegistrationAction(project: Project) :
        ServiceRegistrationAction<ClassesHierarchyBuildService, BuildServiceParameters.None>(
            project,
            ClassesHierarchyBuildService::class.java
        ) {

        override fun configure(parameters: BuildServiceParameters.None) {
            // do nothing
        }
    }
}
