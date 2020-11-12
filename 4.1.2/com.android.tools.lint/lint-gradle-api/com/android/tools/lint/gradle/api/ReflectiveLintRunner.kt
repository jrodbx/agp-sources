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

package com.android.tools.lint.gradle.api

import com.google.common.base.Throwables
import org.gradle.api.GradleException
import org.gradle.api.invocation.Gradle
import org.gradle.initialization.BuildCompletionListener
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URI
import java.net.URL
import java.net.URLClassLoader

/** Runs lint via reflection in its own class loader */
class ReflectiveLintRunner {
    fun runLint(gradle: Gradle, request: LintExecutionRequest, lintClassPath: Set<File>) {
        try {
            val loader = getLintClassLoader(gradle, lintClassPath)
            val cls = loader.loadClass("com.android.tools.lint.gradle.LintGradleExecution")
            val constructor = cls.getConstructor(LintExecutionRequest::class.java)
            val driver = constructor.newInstance(request)
            val analyzeMethod = driver.javaClass.getDeclaredMethod("analyze")
            analyzeMethod.invoke(driver)
        } catch (e: InvocationTargetException) {
            if (e.targetException is GradleException) {
                // Build error from lint -- pass it on
                throw e.targetException
            }
            throw wrapExceptionAsString(e)
        } catch (t: Throwable) {
            // Reflection problem
            throw wrapExceptionAsString(t)
        }
    }

    fun extractAnnotations(
        gradle: Gradle,
        request: ExtractAnnotationRequest,
        lintClassPath: Set<File>
    ) {
        try {
            val loader = getLintClassLoader(gradle, lintClassPath)
            val cls = loader.loadClass("com.android.tools.lint.gradle.LintExtractAnnotations")
            val driver = cls.newInstance()
            val analyzeMethod = driver.javaClass.getDeclaredMethod(
                "extractAnnotations",
                ExtractAnnotationRequest::class.java
            )
            analyzeMethod.invoke(driver, request)
        } catch (e: ExtractErrorException) {
            throw GradleException(e.message)
        } catch (e: InvocationTargetException) {
            if (e.targetException is GradleException) {
                // Build error from lint -- pass it on
                throw e.targetException
            }
            throw wrapExceptionAsString(e)
        } catch (t: Throwable) {
            throw wrapExceptionAsString(t)
        }
    }

    private fun wrapExceptionAsString(t: Throwable) = RuntimeException(
        "Lint infrastructure error\nCaused by: ${Throwables.getStackTraceAsString(t)}\n"
    )

    companion object {
        // There should only be one Lint class loader, and it should
        // exist for the entire lifetime of the Gradle daemon.
        private var loader: DelegatingClassLoader? = null

        private var buildCompletionListenerRegistered = false

        @Synchronized
        private fun getLintClassLoader(gradle: Gradle, lintClassPath: Set<File>): ClassLoader {
            var l = loader
            if (l == null) {
                val urls = computeUrlsFromClassLoaderDelta(lintClassPath)
                    ?: computeUrlsFallback(lintClassPath)
                l = DelegatingClassLoader(urls.toTypedArray())
                loader = l
            }

            // There can be multiple Lint tasks running in parallel, and we would like them
            // to share the same UastEnvironment (in order to share caches).
            // Thus we do not dispose the UastEnvironment until the entire
            // Gradle invocation finishes.
            if (!buildCompletionListenerRegistered) {
                buildCompletionListenerRegistered = true
                gradle.addListener(BuildCompletionListener {
                    val cls = l.loadClass("com.android.tools.lint.UastEnvironment")
                    val disposeMethod = cls.getDeclaredMethod("disposeApplicationEnvironment")
                    disposeMethod.invoke(null)
                    buildCompletionListenerRegistered = false
                })
            }

            return l
        }

        /**
         * Computes the class loader based on looking at the given [lintClassPath] and
         * subtracting out classes already loaded by the Gradle plugin directly.
         * This may fail if the class loader isn't a URL class loader, or if
         * after some diagnostics we discover that things aren't the way they should be.
         */
        private fun computeUrlsFromClassLoaderDelta(lintClassPath: Set<File>): List<URL>? {
            // Operating on URIs rather than URLs here since URL.equals is a blocking (host name
            // resolving) operation.
            // We map to library names since sometimes the Gradle plugin and the lint class path
            // vary in where they locate things, e.g. builder-model in lintClassPath could be
            //  file:out/repo/com/<truncated>/builder-model/3.1.0-dev/builder-model-3.1.0-dev.jar
            // vs the current class loader pointing to
            //  file:~/.gradle/caches/jars-3/a6fbe15f1a0e37da0962349725f641cc/builder-3.1.0-dev.jar
            val uriMap = HashMap<String, URI>(2 * lintClassPath.size)
            lintClassPath.forEach {
                val uri = it.toURI()
                val name = getLibrary(uri) ?: return null
                uriMap[name] = uri
            }

            val gradleClassLoader = this::class.java.classLoader as? URLClassLoader ?: return null
            for (url in gradleClassLoader.urLs) {
                val uri = url.toURI()
                val name = getLibrary(uri) ?: return null
                uriMap.remove(name)
            }

            // Convert to URLs (and sanity check the result)
            val urls = ArrayList<URL>(uriMap.size)
            var seenLint = false
            for ((name, uri) in uriMap) {
                if (name.startsWith("lint-api")) {
                    seenLint = true
                } else if (name.startsWith("builder-model")) {
                    // This should never be on our class path, something is wrong
                    return null
                }
                urls.add(uri.toURL())
            }

            if (!seenLint) {
                // Something is wrong; fall back to heuristics
                return null
            }

            return urls
        }

        private fun getLibrary(uri: URI): String? {
            val path = uri.path
            val index = uri.path.lastIndexOf('/')
            if (index == -1) {
                return null
            }
            var dash = path.indexOf('-', index)
            while (dash != -1 && dash < path.length) {
                if (path[dash + 1].isDigit()) {
                    return path.substring(index + 1, dash)
                } else {
                    dash = path.indexOf('-', dash + 1)
                }
            }

            return path.substring(index + 1, if (dash != -1) dash else path.length)
        }

        /**
         * Computes the exact set of URLs that we should load into our own
         * class loader. This needs to include all the classes lint depends on,
         * but NOT the classes that are already defined by the gradle plugin,
         * since we'll be passing in data (like Gradle projects, builder model
         * classes, sdklib classes like BuildInfo and so on) and these need
         * to be using the existing class loader.
         *
         * This is based on hardcoded heuristics instead of deltaing class loaders.
         */
        private fun computeUrlsFallback(lintClassPath: Set<File>): List<URL> {
            val urls = mutableListOf<URL>()

            for (file in lintClassPath) {
                val name = file.name

                // The set of jars that lint needs that *aren't* already used/loaded by gradle-core
                if (name.startsWith("uast-") ||
                    name.startsWith("intellij-core-") ||
                    name.startsWith("kotlin-compiler-") ||
                    name.startsWith("asm-") ||
                    name.startsWith("kxml2-") ||
                    name.startsWith("trove4j-") ||
                    name.startsWith("groovy-all-") ||

                    // All the lint jars, except lint-gradle-api jar (self)
                    name.startsWith("lint-") &&
                    // Do *not* load this class in a new class loader; we need to
                    // share the same class as the one already loaded by the Gradle
                    // plugin
                    !name.startsWith("lint-gradle-api-")
                ) {
                    urls.add(file.toURI().toURL())
                }
            }

            return urls
        }
    }

    class ExtractErrorException(override val message: String) : RuntimeException(message)
}
