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

import com.android.tools.lint.gradle.api.LintClassLoaderProvider
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File

// Cache class loader across builds. Recreating it on every build introduces huge performance
// regression. See http://b/159760367 for details. Tracked in http://b/159781509.
// It is enough to synchronize on build service methods when accessing this field, as there is
// a single build service in the same class loader as this property during the build.
private var cachedClassLoader: ClassLoader? = null

/**
 * A build service that extends [LintClassLoaderProvider] so that a single class loaders is shared
 * between all tasks in the build.
 */
abstract class LintClassLoaderBuildService : LintClassLoaderProvider(),
    BuildService<BuildServiceParameters.None>, AutoCloseable {

    @Synchronized
    override fun getClassLoader(lintClassPath: Set<File>): ClassLoader {
        return cachedClassLoader ?: super.getClassLoader(lintClassPath).also {
            cachedClassLoader = it
        }
    }

    @Synchronized
    override fun close() {
        cachedClassLoader?.let {
            disposeApplicationEnvironment(it)
        }
    }

    class RegistrationAction(project: Project) :
        ServiceRegistrationAction<LintClassLoaderBuildService, BuildServiceParameters.None>(
            project,
            LintClassLoaderBuildService::class.java
        ) {
        override fun configure(parameters: BuildServiceParameters.None) {
            // do nothing
        }
    }
}