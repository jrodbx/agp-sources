/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.Version
import com.android.build.gradle.options.StringOption
import com.android.tools.build.jetifier.core.config.ConfigParser
import com.android.tools.build.jetifier.processor.FileMapping
import com.android.tools.build.jetifier.processor.Processor
import com.android.tools.build.jetifier.processor.transform.bytecode.AmbiguousStringJetifierException
import com.android.tools.build.jetifier.processor.transform.bytecode.InvalidByteCodeException
import com.google.common.base.Preconditions
import com.google.common.base.Splitter
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

/**
 * [TransformAction] to convert a third-party library that uses old support libraries into an
 * equivalent library that uses AndroidX.
 */
abstract class JetifyTransform : TransformAction<JetifyTransform.Parameters> {

    interface Parameters : GenericTransformParameters {
        @get:Input
        val skipIfPossible: Property<Boolean>

        @get:Input
        val blackListOption: Property<String>
    }

    companion object {

        /**
         * The Jetifier processor.
         */
        private val jetifierProcessor: Processor by lazy {
            Processor.createProcessor3(
                config = ConfigParser.loadDefaultConfig()!!,
                dataBindingVersion = Version.ANDROID_GRADLE_PLUGIN_VERSION,
                allowAmbiguousPackages = false,
                stripSignatures = true
            )
        }
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    /**
     * Computes the Jetifier blacklist of type [Regex] from a string containing a comma-separated
     * list of regular expressions. The string may be empty.
     *
     * If a library's absolute path contains a substring that matches one of the regular
     * expressions, the library won't be jetified.
     *
     * For example, if the regular expression is  "doNot.*\.jar", then "/path/to/doNotJetify.jar"
     * won't be jetified.
     */
    private fun getJetifierBlackList(blackListOption: String): List<Regex> {
        val blackList = mutableListOf<String>()
        if (blackListOption.isNotEmpty()) {
            blackList.addAll(Splitter.on(",").trimResults().splitToList(blackListOption))
        }

        // Jetifier should not jetify itself (http://issuetracker.google.com/119135578)
        blackList.add("jetifier-.*\\.jar")

        return blackList.map { Regex(it) }
    }

    override fun transform(transformOutputs: TransformOutputs) {
        val aarOrJarFile = inputArtifact.get().asFile
        Preconditions.checkArgument(
            aarOrJarFile.name.endsWith(".aar", ignoreCase = true)
                    || aarOrJarFile.name.endsWith(".jar", ignoreCase = true)
        )

        /*
         * The aars or jars can be categorized into 4 types:
         *  - AndroidX libraries
         *  - Old support libraries
         *  - Other libraries that are blacklisted
         *  - Other libraries that are not blacklisted
         * In the following, we handle these cases accordingly.
         */
        // Case 1: If this is an AndroidX library, no need to jetify it
        if (jetifierProcessor.isNewDependencyFile(aarOrJarFile)) {
            transformOutputs.file(aarOrJarFile)
            return
        }

        // Case 2: If this is an old support library, it means that it was not replaced during
        // dependency substitution earlier, either because it does not yet have an AndroidX version,
        // or because its AndroidX version is not yet available on remote repositories. Again, no
        // need to jetify it.
        if (jetifierProcessor.isOldDependencyFile(aarOrJarFile)) {
            transformOutputs.file(aarOrJarFile)
            return
        }

        val jetifierBlackList: List<Regex> = getJetifierBlackList(parameters.blackListOption.get())

        // Case 3: If the library is blacklisted, do not jetify it
        if (jetifierBlackList.any { it.containsMatchIn(aarOrJarFile.absolutePath) }) {
            transformOutputs.file(aarOrJarFile)
            return
        }

        // Case 4: For the remaining libraries, let's jetify them
        val outputFile = transformOutputs.file("jetified-${aarOrJarFile.name}")
        val result = try {
            jetifierProcessor.transform2(
                input = setOf(FileMapping(aarOrJarFile, outputFile)),
                copyUnmodifiedLibsAlso = true,
                skipLibsWithAndroidXReferences = parameters.skipIfPossible.get()
            )
        } catch (exception: Exception) {
            var message =
                "Failed to transform '$aarOrJarFile' using Jetifier." +
                        " Reason: ${exception.javaClass.simpleName}, message: ${exception.message}." +
                        " (Run with --stacktrace for more details.)"
            message += if (exception is InvalidByteCodeException /* Bug 140747218 */
                || exception is AmbiguousStringJetifierException /* Bug 116745353 */) {
                "\nThis is a known exception, and Jetifier won't be able to jetify this library.\n" +
                        "Suggestions:\n" +
                        " - If you believe this library doesn't need to be jetified (e.g., if it" +
                        " already supports AndroidX, or if it doesn't use support" +
                        " libraries/AndroidX at all), please add" +
                        " ${StringOption.JETIFIER_BLACKLIST.propertyName} = {comma-separated list" +
                        " of regular expressions (or simply names) of the libraries that you" +
                        " don't want to be jetified} to the gradle.properties file.\n" +
                        " - If you believe this library needs to be jetified (e.g., if it uses" +
                        " old support libraries and breaks your app if it isn't jetified), please" +
                        " contact the library's authors to update this library to support" +
                        " AndroidX and use the supported version once it is released.\n" +
                        "If you need further help, please file a bug at" +
                        " http://issuetracker.google.com/issues/new?component=460323."
            } else {
                "\nPlease file a bug at http://issuetracker.google.com/issues/new?component=460323."
            }
            throw RuntimeException(message, exception)
        }

        check(result.librariesMap.size == 1)
        check(result.librariesMap[aarOrJarFile] == outputFile)
        check(outputFile.exists())
    }
}

