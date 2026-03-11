/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheBuilderSpec
import com.google.common.cache.CacheLoader
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import java.io.InputStream
import java.net.URLClassLoader
import java.util.Objects
import javax.annotation.concurrent.ThreadSafe

/*
    Build Service used for providing class loader isolation (for Jacoco classpaths) when performing
    instrumentation using Jacoco. This is a mainly a work around for Artifact Transforms which
    cannot use [org.gradle.WorkerExecutor].
 */
@ThreadSafe
abstract class JacocoInstrumentationService
    : BuildService<BuildServiceParameters.None>, AutoCloseable {

    protected open val instrumenterCache = CacheBuilder.from(
        CacheBuilderSpec.parse("softValues")
    ).build(
        object : CacheLoader<ClassLoaderCacheKey, Instrumenter>() {
            override fun load(key: ClassLoaderCacheKey): Instrumenter {
                return Instrumenter(
                    createClassLoader(key.classpath)
                )
            }
        }
    )

    fun instrument(
        input: InputStream,
        name: String,
        classpaths: Iterable<File>,
        jacocoVersion: String
    ): ByteArray {
        val instrumenter = instrumenterCache.get(ClassLoaderCacheKey(jacocoVersion, classpaths))
        return instrumenter.instrument(input, name)
    }

    private fun getPlatformClassLoader(): ClassLoader {
        // AGP is currently compiled against java 8 APIs, so do this by reflection (b/160392650)
        return ClassLoader::class.java.getMethod("getPlatformClassLoader")
            .invoke(null) as ClassLoader
    }

    private fun createClassLoader(classpath: Iterable<File>): URLClassLoader {
        val classpathUrls = classpath.map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(classpathUrls, getPlatformClassLoader())
    }

    override fun close() {
        instrumenterCache.asMap().forEach{ it.value.classLoader.close() }
        instrumenterCache.invalidateAll()
    }

    data class ClassLoaderCacheKey(val jacocoVersion: String, val classpath: Iterable<File>) {

        override fun equals(other: Any?): Boolean {
            return other is ClassLoaderCacheKey && this.jacocoVersion == other.jacocoVersion
        }

        override fun hashCode(): Int {
            return Objects.hash(jacocoVersion)
        }
    }

    class Instrumenter(val classLoader: URLClassLoader) {
        private val jacocoCorePackage = "org.jacoco.core"
        private val offlineInstrumentationAccessGenerator =
            loadClass("$jacocoCorePackage.runtime.OfflineInstrumentationAccessGenerator")
                .getConstructor()
                .newInstance()
        private val iExecutionDataAccessorGeneratorInterface =
            loadClass("$jacocoCorePackage.runtime.IExecutionDataAccessorGenerator",)
        private val instrumenterClass =
            loadClass("$jacocoCorePackage.instr.Instrumenter")
        private val instrumenter = instrumenterClass
            .getConstructor(iExecutionDataAccessorGeneratorInterface)
            .newInstance(offlineInstrumentationAccessGenerator)
        private val instrumentMethod = instrumenterClass
            .getMethod("instrument", InputStream::class.java, String::class.java)

        fun instrument(input: InputStream, name: String): ByteArray {
            return instrumentMethod
                .invoke(instrumenter, input, name) as ByteArray
        }

        private fun loadClass(classToLoad: String) = classLoader.loadClass(classToLoad)
    }

    class RegistrationAction(
        project: Project,
    ) : ServiceRegistrationAction<JacocoInstrumentationService, BuildServiceParameters.None>
        (project, JacocoInstrumentationService::class.java) {

        override fun configure(parameters: BuildServiceParameters.None) {}
    }
}
