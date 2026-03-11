/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.ide.common.r8

import java.io.File
import java.io.InputStream
import java.util.function.Consumer
import kotlin.text.contains

/**
 * Detects global options which should not be used in consumer keep rules.
 *
 * The custom filtering/detection logic in this class is planned to be replaced by logic built into
 * R8, which is more robust in rule parsing / exception generation. See b/437139566.
 */
object ConsumerRuleGlobalGuardian {

    /** Global keep options are not allowed in consumer rules, regardless of arguments */
    fun isBannedOption(optionName: String): Boolean {
        when (optionName) {
            "dontoptimize",
            "dontobfuscate",
            "dontshrink",
            "dontrepackage",
            "include",
            "basedirectory",
            "injars",
            "outjars",
            "libraryjars",
            "renamesourcefileattribute",
            "printblastradius",
            "printconfiguration",
            "printmapping",
            "printusage",
            "printseeds",
            "processkotlinnullchecks",
            "allowaccessmodification",
            "overloadaggressively",
            "ignorewarnings",
            "addconfigurationdebugging",
            "applymapping",
            "obfuscationdictionary",
            "classobfuscationdictionary",
            "packageobfuscationdictionary" -> return true
        }
        return false
    }
    /** Global keep options which are not allowed in consumer rules without package argument */
    fun isBannedOptionWithoutPackageArg(optionName: String): Boolean {
        when (optionName) {
            "repackageclasses",
            "flattenpackagehierarchy" -> return true
        }
        return false
    }

    class BannedLinePattern(val requiresArgumentInConsumerRules: Boolean, val option: String) {
        fun asExceptionMessage(isDynamicFeature: Boolean, path: String): String {
            val baseMessage =
                if (isDynamicFeature) {
                    // Avoid term "consumerProguardFile" in feature modules, since that's not
                    // the api where they're specified.
                    "Global keep option -$option was specified in $path. "
                } else {
                    "Global keep option -$option was specified as a consumerProguardFile in $path. "
                }
            return baseMessage +
                    if (isBannedOption(option)) {
                        if (isDynamicFeature) {
                            "It should not be specified in this module. It can be specified in the base" +
                                    " module instead."
                        } else {
                            "It should not be used in a consumer configuration file."
                        }
                    } else { // banned due to missing arguments
                        if (isDynamicFeature) {
                            "It should not be specified in this module without specifying a package. Add" +
                                    " a package scope, or specify this option in the base module instead."
                        } else {
                            "It should not be used in a consumer configuration file without specifying a" +
                                    " package."
                        }
                    }
        }
    }

    /**
     * A regular expression to parse a single line of a Keep rule file.
     *
     * Explanation:
     * - `\s*`: Optional leading whitespace
     * - `-`: Hyphen that proceeds option name
     * - `([a-zA-Z0-9]+)`: Capturing Group 1 (the "option"). Matches one or more alphanumeric
     *   characters
     * - `(?:\s+([^#]*?))?`: An optional non-capturing group for the arguments.
     * - `\s+`: Matches one or more whitespace characters (required separator).
     * - `([^#]*?)`: Capturing Group 2 (the "args"). Non-greedily matches any character that is not a
     *   '#' (comment marker).
     * - `?`: Makes this entire "whitespace + args" group optional.
     * - `\s*`: Matches optional trailing whitespace (between args and comment/end-of-line).
     * - `(?:#.*)?`: An optional non-capturing group for comments. Matches '#' followed by any
     *   characters to the end of the line.
     */
    private val KEEP_OPTION_ARGS_REGEX = """\s*-([a-zA-Z0-9]+)(?:\s+([^#]*?))?\s*(?:#.*)?""".toRegex()

    private fun checkForBannedLine(keepLine: String): BannedLinePattern? {
        val match = KEEP_OPTION_ARGS_REGEX.matchEntire(keepLine)
        if (match != null) {
            val option = match.groups[1]!!.value
            val args = match.groups[2]?.value

            if (isBannedOption(option)) {
                return BannedLinePattern(option = option, requiresArgumentInConsumerRules = false)
            } else if (isBannedOptionWithoutPackageArg(option) && args.isNullOrEmpty()) {
                return BannedLinePattern(option = option, requiresArgumentInConsumerRules = true)
            }
        }
        return null
    }

    class DiscoveredIssue(
        val requiresArgumentInConsumerRules: Boolean,
        val globalOption: String,
        val errorMessage: String,
    )

    /**
     * Checks consumer proguard file for invalid global options, used for validating locally defined
     * consumer rules.
     *
     * These checks should be guarded by
     * [BooleanOption.R8_GLOBAL_OPTIONS_IN_CONSUMER_RULES_DISALLOWED].
     *
     * Note that consumer files may not exist, since [BooleanOption.FAIL_ON_MISSING_PROGUARD_FILES]
     * may be false.
     */
    fun validateConsumerRulesHasNoBannedGlobals(
        consumerKeepRulesFile: File,
        isDynamicFeature: Boolean,
        exceptionHandler: Consumer<DiscoveredIssue>,
    ) {
        if (!consumerKeepRulesFile.exists()) return

        consumerKeepRulesFile.forEachLine { rawLine ->
            checkForBannedLine(rawLine)?.apply {
                exceptionHandler.accept(
                    DiscoveredIssue(
                        globalOption = option,
                        requiresArgumentInConsumerRules = requiresArgumentInConsumerRules,
                        errorMessage = asExceptionMessage(isDynamicFeature, consumerKeepRulesFile.path),
                    )
                )
            }
        }
    }

    /**
     * Loads consumer keep rules from an input stream to filter out unsupported global rules.
     *
     * These checks should be guarded by
     * [BooleanOption.R8_GLOBAL_OPTIONS_IN_CONSUMER_RULES_DISALLOWED].
     *
     * Note that consumer files may not exist, since [BooleanOption.FAIL_ON_MISSING_PROGUARD_FILES]
     * may be false.
     *
     * Note that the [InputStream] must not be closed so that more items can be read from the zip.
     */
    fun readConsumerKeepRulesRemovingBannedGlobals(
        inputStream: InputStream,
        shouldRemoveBannedGlobals: Boolean,
    ): String {
        return if (shouldRemoveBannedGlobals) {
            val output = StringBuilder()
            // NOTE: we use lineSequence here to avoid closing the zip stream
            inputStream.bufferedReader().lineSequence().forEach { line ->
                if (checkForBannedLine(line) != null) {
                    output.append("# REMOVED CONSUMER RULE: ")
                }
                output.append(line).append(System.lineSeparator())
            }
            output.toString()
        } else {
            inputStream.readBytes().decodeToString()
        }
    }
}
