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
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import javax.inject.Inject

private const val FAKE_DEPENDENCY_JAR = "FakeDependency.jar"
private const val ANDROID_SUBDIR = "android"

abstract class FakeDependencyJarBuildService : BuildService<FakeDependencyJarBuildService.Params>,
    AutoCloseable {

    interface Params : BuildServiceParameters {
        val gradleUserHome: Property<File>
    }

    @get:Inject
    abstract val providerFactory: ProviderFactory

    @get:Inject
    abstract val objectFactory: ObjectFactory

    /**
     * Use [ConfigPhaseFileCreator] to create fake dependency jar during configuration phase to
     * avoid configuration cache miss.
     */
    val lazyCachedFakeJar: File = providerFactory.of(FakeDependencyJarCreator::class.java) {
        it.parameters.fakeDependencyJar.set(
                parameters.gradleUserHome.get().resolve(ANDROID_SUBDIR).resolve(FAKE_DEPENDENCY_JAR)
        )
    }.get()

    abstract class FakeDependencyJarCreator :
            ConfigPhaseFileCreator<File, FakeDependencyJarCreator.Params> {
        interface Params: ConfigPhaseFileCreator.Params {
            val fakeDependencyJar: RegularFileProperty
        }

        override fun obtain(): File {
            val fakeDependencyJar = parameters.fakeDependencyJar.get().asFile
            if (!fakeDependencyJar.exists()) {
                fakeDependencyJar.parentFile.mkdirs()
                JarFlinger(fakeDependencyJar.toPath()).use {}
            }
            return fakeDependencyJar
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
