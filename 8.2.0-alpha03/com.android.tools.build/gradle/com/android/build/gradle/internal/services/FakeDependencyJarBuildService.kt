/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.builder.packaging.JarFlinger
import com.android.builder.utils.SynchronizedFile
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File

private const val FAKE_DEPENDENCY_JAR = "FakeDependency.jar"
private const val ANDROID_SUBDIR = "android"

abstract class FakeDependencyJarBuildService : BuildService<FakeDependencyJarBuildService.Params>,
    AutoCloseable {

    interface Params : BuildServiceParameters {
        val gradleUserHome: Property<File>
    }

    val lazyCachedFakeJar: File by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SynchronizedFile.getInstanceWithMultiProcessLocking(
            parameters.gradleUserHome.get().resolve(ANDROID_SUBDIR)
        ).write {
            val fakeJar = it.resolve(FAKE_DEPENDENCY_JAR)
            if (!fakeJar.exists()) {
                fakeJar.parentFile.mkdirs()
                JarFlinger(fakeJar.toPath()).use {}
            }
            fakeJar
        }
    }

    class RegistrationAction(project: Project) :
        ServiceRegistrationAction<FakeDependencyJarBuildService, Params>(
            project,
            FakeDependencyJarBuildService::class.java
        ) {

        override fun configure(parameters: Params) {
            parameters.gradleUserHome.set(project.gradle.gradleUserHomeDir)
        }
    }

    override fun close() {
        // do nothing
    }
}
