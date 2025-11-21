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
import com.google.common.base.Splitter
import org.gradle.api.artifacts.transform.CacheableTransform
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
@CacheableTransform
abstract class JetifyTransform : TransformAction<JetifyTransform.Parameters> {

    interface Parameters : GenericTransformParameters {

        @get:Input
        val ignoreListOption: Property<String>
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
     * Computes the Jetifier ignore list of type [Regex] from a string containing a comma-separated
     * list of regular expressions. The string may be empty.
     *
     * If a library's absolute path contains a substring that matches one of the regular
     * expressions, the library won't be jetified.
     *
     * For example, if the regular expression is  "doNot.*\.jar", then "/path/to/doNotJetify.jar"
     * won't be jetified.
     */
    private fun getJetifierIgnoreList(ignoreListOption: String): List<Regex> {
        val ignoreList = mutableListOf<String>()
        if (ignoreListOption.isNotEmpty()) {
            ignoreList.addAll(Splitter.on(",").trimResults().splitToList(ignoreListOption))
        }

        // Jetifier should not jetify itself (http://issuetracker.google.com/119135578)
        ignoreList.add("jetifier-.*\\.jar")

        return ignoreList.map { Regex(it) }
    }

    override fun transform(transformOutputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        check(
            inputFile.name.endsWith(".aar", ignoreCase = true)
                    || inputFile.name.endsWith(".jar", ignoreCase = true)
        ) {
            "Transform's input file is not .aar or .jar: ${inputFile.path}"
        }
        check(inputFile.isFile) {
            "Transform's input file does not exist: ${inputFile.path}." +
                    " (See https://issuetracker.google.com/issues/158753935)"
        }

        /*
         * The aars or jars can be categorized into 4 types:
         *  - AndroidX libraries
         *  - Old support libraries
         *  - Other libraries that are ignored
         *  - Other libraries that are not ignored
         * In the following, we handle these cases accordingly.
         */
        // Case 1: If this is an AndroidX library, no need to jetify it
        if (jetifierProcessor.isNewDependencyFile(inputFile)) {
            transformOutputs.file(inputFile)
            return
        }

        // Case 2: If this is an old support library, it means that it was not replaced during
        // dependency substitution earlier, either because it does not yet have an AndroidX version,
        // or because its AndroidX version is not yet available on remote repositories. Again, no
        // need to jetify it.
        if (jetifierProcessor.isOldDependencyFile(inputFile)) {
            transformOutputs.file(inputFile)
            return
        }

        val jetifierIgnoreList: List<Regex> = getJetifierIgnoreList(parameters.ignoreListOption.get())

        // Case 3: If the library is ignored, do not jetify it
        if (jetifierIgnoreList.any { it.containsMatchIn(inputFile.absolutePath) }) {
            transformOutputs.file(inputFile)
            return
        }

        // Case 4: For the remaining libraries, let's jetify them
        val outputFile = transformOutputs.file("jetified-${inputFile.name}")
        val result = try {
            jetifierProcessor.transform2(
                input = setOf(FileMapping(inputFile, outputFile)),
                copyUnmodifiedLibsAlso = true,
                skipLibsWithAndroidXReferences = true
            )
        } catch (exception: Exception) {
            throw RuntimeException(
                """
                Jetifier failed to transform: $inputFile
                The error was: ${exception.javaClass.name} - ${exception.message}
                To fix this issue, see https://issuetracker.google.com/issues/184622491
                """.trimIndent(),
                exception
            )
        }

        check(result.librariesMap.size == 1)
        check(result.librariesMap[inputFile] == outputFile)
        check(outputFile.exists())
    }
}

