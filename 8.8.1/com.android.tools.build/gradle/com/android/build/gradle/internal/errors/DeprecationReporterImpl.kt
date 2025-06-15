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

package com.android.build.gradle.internal.errors

import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.Option
import com.android.build.gradle.options.OptionalBooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.build.gradle.options.StringOption
import com.android.builder.errors.IssueReporter
import com.android.builder.errors.IssueReporter.Type
import java.io.File

class DeprecationReporterImpl(
        private val issueReporter: IssueReporter,
        private val projectOptions: ProjectOptions,
        private val projectPath: String) : DeprecationReporter {

    private val suppressedOptionWarnings: Set<String> =
        projectOptions[StringOption.SUPPRESS_UNSUPPORTED_OPTION_WARNINGS]?.splitToSequence(',')?.toSet()
                ?: setOf()

    override fun reportDeprecatedUsage(
            newDslElement: String,
            oldDslElement: String,
            deprecationTarget: DeprecationTarget) {
        issueReporter.reportWarning(
                Type.DEPRECATED_DSL,
                "DSL element '$oldDslElement' is obsolete and has been replaced with '$newDslElement'.\n" +
                        deprecationTarget.getDeprecationTargetMessage(),
                "$oldDslElement::$newDslElement::${deprecationTarget.name}")
    }

    override fun reportDeprecatedApi(
        newApiElement: String?,
        oldApiElement: String,
        url: String,
        deprecationTarget: DeprecationTarget
    ) {
        if (!checkAndSet(oldApiElement)) {
            val debugApi = projectOptions.get(BooleanOption.DEBUG_OBSOLETE_API)
            val firstLine = if(newApiElement != null) {
                "API '$oldApiElement' is obsolete and has been replaced with '$newApiElement'."
            } else {
                "API '$oldApiElement' is obsolete."
            }

            val messageStart = firstLine + "\n" +
                    deprecationTarget.getDeprecationTargetMessage() +
                    "\nFor more information, see $url."
            var messageEnd = ""

            if (debugApi) {
                val traces = Thread.currentThread().stackTrace

                // special check for the Kotlin plugin.
                val kotlin = traces.filter {
                    it.className.startsWith("org.jetbrains.kotlin.gradle.plugin.")
                }

                messageEnd = if (kotlin.isNotEmpty()) {
                    "REASON: The Kotlin plugin is currently calling this deprecated API." +
                            " Watch https://youtrack.jetbrains.com/issue/KT-25428 and, if possible," +
                            " use a newer version of the Kotlin plugin that has fixed this issue."
                } else {
                    // other cases.
                    getCallingSite(traces)

                } + "\nWARNING: Debugging obsolete API calls can take time during configuration. It's recommended to not keep it on at all times."
            } else {
                messageEnd = "To determine what is calling $oldApiElement, use -P${BooleanOption.DEBUG_OBSOLETE_API.propertyName}=true on the command line to display more information."
            }

            issueReporter.reportWarning(
                Type.DEPRECATED_DSL,
                "$messageStart\n$messageEnd")

        }
    }

    override fun reportRemovedApi(
        oldApiElement: String,
        url: String,
        deprecationTarget: DeprecationTarget
    ) {
        if (!checkAndSet(oldApiElement)) {
            val debugApi = projectOptions.get(BooleanOption.DEBUG_OBSOLETE_API)
            val firstLine = "API '$oldApiElement' is removed."

            val messageStart = firstLine + "\n" +
                    "\nFor more information, see $url."
            var messageEnd = ""

            if (debugApi) {
                val traces = Thread.currentThread().stackTrace
                getCallingSite(traces) + "\nWARNING: Debugging obsolete API calls can take time during configuration. It's recommended to not keep it on at all times."
            } else {
                messageEnd = "To determine what is calling $oldApiElement, use -P${BooleanOption.DEBUG_OBSOLETE_API.propertyName}=true on the command line to display more information."
            }

            issueReporter.reportError(
                Type.REMOVED_API,
                "$messageStart\n$messageEnd")

        }
    }

    // look to see if we get a fileName that's a full path and is a known gradle file.
    private fun getCallingSite(traces: Array<StackTraceElement>): String {
        // other cases.
        // look to see if we get a fileName that's a full path and is a known gradle file.
        val gradleFile = traces.asSequence().filter {
            it?.fileName?.let { fileName ->
                val file = File(fileName)
                file.isAbsolute && file.isFile && (fileName.endsWith(".gradle") || fileName.endsWith(
                    ".gradle.kts"
                ))
            } ?: false
        }.map {
            "${it.fileName}:${it.lineNumber}"
        }.firstOrNull()

        return if (gradleFile != null) {
            "REASON: Called from: $gradleFile"

        } else {
            val formattedTraces = traces.map { "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})\n" }

            "REASON: It is currently called from the following trace:\n" + formattedTraces.joinToString(
                separator = "",
                prefix = "",
                postfix = ""
            )
        }
    }

    override fun reportObsoleteUsage(oldDslElement: String,
            deprecationTarget: DeprecationTarget) {
        issueReporter.reportWarning(
                Type.DEPRECATED_DSL,
                "DSL element '$oldDslElement' is obsolete and should be removed.\n" +
                        deprecationTarget.getDeprecationTargetMessage(),
                "$oldDslElement::::${deprecationTarget.name}")
    }

    override fun reportRenamedConfiguration(
            newConfiguration: String,
            oldConfiguration: String,
            deprecationTarget: DeprecationTarget) {
        issueReporter.reportWarning(
                Type.USING_DEPRECATED_CONFIGURATION,
            "Configuration '$oldConfiguration' is obsolete and has been replaced with '$newConfiguration'.\n" +
                    deprecationTarget.getDeprecationTargetMessage(),
                "$oldConfiguration::$newConfiguration::${deprecationTarget.name}")
    }

    override fun reportDeprecatedConfiguration(
        newDslElement: String,
        oldConfiguration: String,
        deprecationTarget: DeprecationTarget
    ) {
        issueReporter.reportWarning(
            Type.USING_DEPRECATED_CONFIGURATION,
            "Configuration '$oldConfiguration' is obsolete and has been replaced with DSL element '$newDslElement'.\n" +
                    deprecationTarget.getDeprecationTargetMessage(),
            "$oldConfiguration::$newDslElement::${deprecationTarget.name}")
    }

    override fun reportDeprecatedValue(dslElement: String,
            oldValue: String,
            newValue: String?,
            deprecationTarget: DeprecationTarget) {
        issueReporter.reportWarning(Type.USING_DEPRECATED_DSL_VALUE,
                "DSL element '$dslElement' has a value '$oldValue' which is obsolete " +
                        (if (newValue != null)
                            "and has been replaced with '$newValue'.\n"
                        else
                            "and has not been replaced.\n") +
                        deprecationTarget.getDeprecationTargetMessage())
    }

    override fun reportOptionIssuesIfAny(option: Option<*>, value: Any) {
        if (checkAndSet(option, value)) {
            return
        }
        if (suppressedOptionWarnings.contains(option.propertyName)) {
            return
        }

        val defaultValueMessage =
            option.defaultValue?.let { "\nThe current default is '$it'." } ?: ""
        when (val status = option.status) {
            is Option.Status.EXPERIMENTAL -> {
                if (option.defaultValue != value) {
                    issueReporter.reportWarning(
                        Type.UNSUPPORTED_PROJECT_OPTION_USE,
                        "The option setting '${option.propertyName}=$value' is experimental."
                                + defaultValueMessage,
                        option.propertyName
                    )
                }
            }
            Option.Status.STABLE -> { // No issues
            }
            is Option.Status.Deprecated -> {

                // In some cases, we would like to encourage users to use OptionalBooleanOption flag
                // to opt-in some future behavior to help them migrate smoothly. Therefore, we don't
                // want to warn users of using this flag with recommended value even though this
                // flag is going to be deprecated soon.
                val useRecommendedValue =
                    option is OptionalBooleanOption
                            && option.recommendedValue != null
                            && option.recommendedValue == value

                if (option.defaultValue != value && !useRecommendedValue) {
                    issueReporter.reportWarning(
                        Type.UNSUPPORTED_PROJECT_OPTION_USE,
                        "The option setting '${option.propertyName}=$value' is deprecated."
                                + defaultValueMessage
                                + "\n" + status.getDeprecationTargetMessage(),
                        option.propertyName
                    )
                }
            }
            is Option.Status.Removed -> {
                // Many tests still use BooleanOption.ENABLE_DEPRECATED_NDK even though the feature
                // has been removed, so we always produce a warning for that option to avoid
                // breaking tests. TODO: Remove those tests and remove the special treatment for
                // ENABLE_DEPRECATED_NDK.
                // Also, report "android.enableR8=true" as warning, otherwise as error.
                if (option.defaultValue == value
                    || option == BooleanOption.ENABLE_DEPRECATED_NDK
                    || (value == true && option == OptionalBooleanOption.ENABLE_R8)) {
                    issueReporter.reportWarning(
                        Type.UNSUPPORTED_PROJECT_OPTION_USE,
                        "The option '${option.propertyName}' is deprecated."
                                + defaultValueMessage
                                + "\n" + status.getRemovedVersionMessage(),
                        option.propertyName
                    )
                } else {
                    issueReporter.reportError(
                        Type.UNSUPPORTED_PROJECT_OPTION_USE,
                        "The option '${option.propertyName}' is deprecated."
                                + defaultValueMessage
                                + "\n" + status.getRemovedVersionMessage(),
                        option.propertyName
                    )
                }
            }
        }
    }

    companion object {
        /**
         * Set of obsolete APIs that have been warned already.
         */
        private val obsoleteApis = mutableSetOf<String>()

        /** Options that have already been checked for issues. */
        private val options = mutableSetOf<OptionInfo>()

        /**
         * Checks if the given API is part of the set already and adds it if not.
         *
         * @return true if the api is already part of the set.
         */
        fun checkAndSet(api: String): Boolean = synchronized(obsoleteApis) {
            return if (obsoleteApis.contains(api)) {
                true
            } else {
                obsoleteApis.add(api)
                false
            }
        }

        /**
         * Checks if the given Option has already been checked for issues, and records it if not.
         *
         * @return true if the Option has already been checked for issues
         */
        fun checkAndSet(option: Any, value: Any): Boolean = synchronized(options) {
            val info = OptionInfo(option, value)
            return if (options.contains(info)) {
                true
            } else {
                options.add(info)
                false
            }
        }

        fun clean() {
            synchronized(obsoleteApis) {
                obsoleteApis.clear()
            }
            synchronized(options) {
                options.clear()
            }
        }
    }
}

data class OptionInfo(
    val option: Any,
    val value: Any
)
