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

package com.android.build.gradle.internal.lint

import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask.Companion.HANDLED_ERRORS
import com.android.utils.JvmWideVariable
import com.google.common.reflect.TypeToken
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.lang.ref.SoftReference
import java.lang.reflect.InvocationTargetException
import java.net.URI
import java.net.URLClassLoader
import javax.annotation.concurrent.GuardedBy

@Suppress("UnstableApiUsage")
abstract class AndroidLintWorkAction : WorkAction<AndroidLintWorkAction.LintWorkActionParameters> {

    abstract class LintWorkActionParameters : WorkParameters {
        abstract val mainClass: Property<String>
        abstract val arguments: ListProperty<String>
        abstract val classpath: ConfigurableFileCollection
        abstract val versionKey: Property<String>
        abstract val android: Property<Boolean>
        abstract val fatalOnly: Property<Boolean>
        abstract val runInProcess: Property<Boolean>
        abstract val returnValueOutputFile: RegularFileProperty
        abstract val lintMode: Property<LintMode>
        abstract val hasBaseline: Property<Boolean>
    }

    override fun execute() {
        val logger = Logging.getLogger(this.javaClass)
        val arguments = parameters.arguments.get()
        logger.debug("Running lint " + arguments.joinToString(" "))
        if (!parameters.runInProcess.get()) {
            logger.info(
                "Max memory for Android Lint: {}m\n(can be configured by {}=2G in gradle.properties)",
                Runtime.getRuntime().maxMemory() / 1024 / 1024,
                com.android.build.gradle.options.StringOption.LINT_HEAP_SIZE.propertyName
            )
        }
        val execResult = runLint(arguments)
        logger.debug("Lint returned $execResult")
        parameters.returnValueOutputFile.orNull?.asFile?.let {
            it.writeText("$execResult")
            if (execResult in HANDLED_ERRORS) {
                // return early in this case because a subsequent task will throw the corresponding
                // exception if necessary.
                return@execute
            }
        }
        maybeThrowException(
            execResult,
            parameters.android.get(),
            parameters.fatalOnly.get(),
            parameters.lintMode.get(),
            abbreviatedLintOutput = null,
            parameters.hasBaseline.get()
        )
    }

    private fun runLint(arguments: List<String>): Int {
        val classLoader = getClassloader(parameters.versionKey.get(), parameters.classpath)
        val currentContextClassLoader = Thread.currentThread().contextClassLoader
        try {
            Thread.currentThread().contextClassLoader = null
            return invokeLintMainRunMethod(classLoader, arguments)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        } finally {
            Thread.currentThread().contextClassLoader = currentContextClassLoader
        }
    }

    private fun invokeLintMainRunMethod(classLoader: ClassLoader, arguments: List<String>): Int {
        // val returnValue: Int = Main().run(arguments.toTypedArray())
        val cls = classLoader.loadClass(parameters.mainClass.get())
        val method = cls.getMethod("run", Array<String>::class.java)
        val lintMain = cls.getDeclaredConstructor().newInstance()
        val returnValue = method.invoke(lintMain, arguments.toTypedArray()) as Int

        // If running lint in process, we call dispose() via LintClassLoaderBuildService.close()
        // after all lint invocations; otherwise, we call dispose() here.
        if (!parameters.runInProcess.get()) {
            dispose()
        }

        return returnValue
    }


    companion object {

        private const val ERRNO_SUCCESS = 0
        const val ERRNO_ERRORS = 1
        private const val ERRNO_USAGE = 2
        private const val ERRNO_EXISTS = 3
        private const val ERRNO_HELP = 4
        private const val ERRNO_INVALID_ARGS = 5
        const val ERRNO_CREATED_BASELINE = 6
        private const val ERRNO_APPLIED_SUGGESTIONS = 7

        /**
         * Cache the classloaders across the daemon, even if the buildscipt classpath changes
         *
         * Use a soft reference so the cache doesn't end up keeping a classloader that isn't
         * actually reachable.
         *
         * JvmWideVariable must only use built in types to avoid leaking the current classloader.
         */
        private val cachedClassloader: JvmWideVariable<MutableMap<String, SoftReference<URLClassLoader>>> = JvmWideVariable(
            AndroidLintWorkAction::class.java,
            "cachedClassloader",
            object: TypeToken<MutableMap<String, SoftReference<URLClassLoader>>>() {}
        ) { HashMap() }


        /**
         * Classloaders used during this build that will have disposeApplicationEnvironment called
         * at the end of the build.
         *
         * This is cleared at the end of each build
         */
        @GuardedBy("this")
        private val toDispose = mutableMapOf<String, URLClassLoader>()

        private val logger get() = Logging.getLogger(AndroidLintWorkAction::class.java)

        @Synchronized
        private fun getClassloader(key: String, classpath: FileCollection): ClassLoader {

            val uris = classpath.files.map { it.toURI() }
            return cachedClassloader.executeCallableSynchronously {
                val map = cachedClassloader.get()
                val classloader = map[key]?.get()?.also {
                    logger.info("Android Lint: Reusing lint classloader {}", key)
                } ?: createClassLoader(key, uris).also { map[key] = SoftReference(it) }
                toDispose[key] = classloader
                maintainClassloaders(map)
                classloader
            }
        }

        @Synchronized
        private fun maintainClassloaders(map: MutableMap<String, SoftReference<URLClassLoader>>) {
            if (map.size <= 1) return
            for (otherKey in ArrayList(map.keys)) {
                if (map[otherKey]?.get() == null) {
                    logger.info("Android Lint: Classloader was garbage collected {}", otherKey)
                    map.remove(otherKey)
                }
            }
            if (map.size <= 1) return
            logger.info(
                "Android Lint: There are multiple lint class loaders in this gradle daemon, " +
                        "which can lead to jvm metaspace pressure.\n" +
                        "This be caused when developing lint (usually by using a -dev version) " +
                        "or switching lint versions.\n" +
                        "   Classloaders used in this build: {}\n" +
                        "   Classloaders in this daemon: {}",
                        toDispose.keys.joinToString(", "),
                        map.keys.joinToString(", ")
            )
        }


        private fun createClassLoader(key: String, classpath: List<URI>): URLClassLoader {
            logger.info("Android Lint: Creating lint class loader {}", key)
            val classpathUrls = classpath.map { it.toURL() }.toTypedArray()
            return URLClassLoader(classpathUrls, getPlatformClassLoader())
        }

        private fun getPlatformClassLoader(): ClassLoader {
            // AGP is currently compiled against java 8 APIs, so do this by reflection (b/160392650)
            return ClassLoader::class.java.getMethod("getPlatformClassLoader").invoke(null) as ClassLoader
        }

        @Synchronized
        fun dispose() {

            if (toDispose.isNotEmpty()) {
                logger.info(
                    "Android Lint: Disposing Uast application environment in lint classloader{} [{}]",
                    if (toDispose.size == 1) "" else "s",
                    toDispose.keys.joinToString(", "))
                try {
                    toDispose.values.forEach { classloader ->
                        classloader.loadClass("com.android.tools.lint.UastEnvironment")
                            .getDeclaredMethod("disposeApplicationEnvironment")
                            .invoke(null)
                    }
                } finally {
                    toDispose.clear()
                }
            }
        }

        @JvmStatic
        fun maybeThrowException(
            execResult: Int,
            android: Boolean,
            fatalOnly: Boolean,
            lintMode: LintMode,
            abbreviatedLintOutput: String?,
            hasBaseline: Boolean
        ) {
            when (execResult) {
                ERRNO_SUCCESS -> {}
                ERRNO_ERRORS -> {
                    throw RuntimeException(
                        getErrorMessage(android, fatalOnly, abbreviatedLintOutput, hasBaseline)
                    )
                }
                ERRNO_USAGE -> throw IllegalStateException("Internal Error: Unexpected lint usage")
                ERRNO_EXISTS -> throw RuntimeException("Unable to write lint output")
                ERRNO_HELP -> throw IllegalStateException("Internal error: Unexpected lint help call")
                ERRNO_INVALID_ARGS -> throw IllegalStateException("Internal error: Unexpected lint invalid arguments")
                ERRNO_CREATED_BASELINE -> {
                    if (lintMode != LintMode.UPDATE_BASELINE) {
                        throw RuntimeException("Aborting build since new baseline file was created")
                    }
                }
                ERRNO_APPLIED_SUGGESTIONS -> throw RuntimeException("Aborting build since sources were modified to apply quickfixes after compilation")
                else -> throw IllegalStateException("Internal error: unexpected lint return value $execResult")
            }
        }

        private fun getErrorMessage(
            android: Boolean,
            fatalOnly: Boolean,
            abbreviatedLintOutput: String?,
            hasBaseline: Boolean
        ) : String {
            val prefix =
                if (fatalOnly && android) {
                    "Lint found fatal errors while assembling a release target."
                } else {
                    "Lint found errors in the project; aborting build."
                }
            val suggestion =
                when {
                    hasBaseline ->
                        "Fix the issues identified by lint, or add the issues to the lint baseline via `gradlew updateLintBaseline`."
                    android ->
                        """
                            Fix the issues identified by lint, or create a baseline to see only new errors.
                            To create a baseline, run `gradlew updateLintBaseline` after adding the following to the module's build.gradle file:
                            ```
                            android {
                                lint {
                                    baseline = file("lint-baseline.xml")
                                }
                            }
                            ```
                        """.trimIndent()
                    else ->
                        """
                            Fix the issues identified by lint, or create a baseline to see only new errors.
                            To create a baseline, run `gradlew updateLintBaseline` after adding the following to the module's build.gradle file:
                            ```
                            lint {
                                baseline = file("lint-baseline.xml")
                            }
                            ```
                        """.trimIndent()
                }
            val moreInfo =
                "For more details, see https://developer.android.com/studio/write/lint#snapshot"
            val suffix = abbreviatedLintOutput ?: ""
            return prefix + "\n\n" + suggestion + "\n" + moreInfo + "\n\n" + suffix
        }
    }
}
