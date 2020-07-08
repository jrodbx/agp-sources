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
import java.io.File
import java.lang.reflect.InvocationTargetException

/** Runs lint via reflection in its own class loader */
class ReflectiveLintRunner {
    fun runLint(
        classLoaderProvider: LintClassLoaderProvider,
        request: LintExecutionRequest,
        lintClassPath: Set<File>
    ) {
        try {
            val loader = classLoaderProvider.getClassLoader(lintClassPath)
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
        classLoaderProvider: LintClassLoaderProvider,
        request: ExtractAnnotationRequest,
        lintClassPath: Set<File>
    ) {
        try {
            val loader = classLoaderProvider.getClassLoader(lintClassPath)
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

    class ExtractErrorException(override val message: String) : RuntimeException(message)
}
