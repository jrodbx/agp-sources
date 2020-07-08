/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.utils.FileUtils
import com.google.common.base.Charsets
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import com.google.common.collect.Lists
import com.google.common.io.Files
import org.gradle.api.logging.Logging
import proguard.ClassPath
import proguard.ClassPathEntry
import proguard.ClassSpecification
import proguard.Configuration
import proguard.ConfigurationParser
import proguard.KeepClassSpecification
import proguard.ParseException
import proguard.ProGuard
import proguard.classfile.util.ClassUtil
import proguard.util.ListUtil
import java.io.File
import java.io.IOException

private val JAR_FILTER: List<String> = listOf("!META-INF/MANIFEST.MF")

private val LOG = Logging.getLogger(ProguardDelegate::class.java)

internal class ProguardDelegate(
    private val classes: Collection<File>,
    private val resources: Collection<File>,
    private val referencedClasses: Collection<File>,
    private val referencedResources: Collection<File>,
    private val outFile: File,
    private val mappingFile: File,
    private val seedsFile: File,
    private val usageFile: File,
    private val testedMappingFile: File?,
    private val configurationFiles: Collection<File>,
    private val bootClasspath: Collection<File>,
    private val fullBootClasspath: Collection<File>,
    private val keepRules: Collection<String>,
    private val dontWarnRules: Collection<String>,
    private val optimizationEnabled: Boolean?,
    private val shrinkingEnabled: Boolean?,
    private val obfuscationEnabled: Boolean?
) {

    fun run() {
        try {
            val configuration = Configuration()
            configuration.useMixedCaseClassNames = false
            configuration.programJars = ClassPath()
            configuration.libraryJars = ClassPath()

            // Add -keep and -dontwarn rules
            keepRules.forEach { configuration.addKeepRule(it) }
            dontWarnRules.forEach { configuration.addDontWarnRule(it) }

            // Allow the default values of these parameters if values were not given
            optimizationEnabled?.let { configuration.optimize = optimizationEnabled }
            shrinkingEnabled?.let { configuration.shrink = shrinkingEnabled }
            obfuscationEnabled?.let { configuration.obfuscate = obfuscationEnabled }

            // Set the mapping file if there is one.
            testedMappingFile?.let { configuration.applyMapping = testedMappingFile }

            // Map to track added input files and avoid duplicates
            val fileToFilter: ListMultimap<File, List<String>> = ArrayListMultimap.create()

            // --- InJars / LibraryJars ---
            addAllInputsToConfiguration(configuration, fileToFilter)

            // libraryJars: the runtime jars, with all optional libraries.
            bootClasspath.forEach {
                configuration.libraryJars.inputJar(it, fileToFilter)
            }
            fullBootClasspath.forEach {
                configuration.libraryJars.inputJar(it, fileToFilter)
            }

            // --- Out files ---
            configuration.outJar(outFile)

            // Proguard doesn't verify that the seed/mapping/usage folders exist and will fail
            // if they don't, so create them.
            FileUtils.cleanOutputDir(mappingFile.parentFile)
            FileUtils.cleanOutputDir(seedsFile.parentFile)
            FileUtils.cleanOutputDir(usageFile.parentFile)

            for (configFile in configurationFiles) {
                LOG.info("Applying ProGuard configuration file {}", configFile)
                configuration.applyConfigurationFile(configFile)
            }

            configuration.printMapping = mappingFile
            configuration.printSeeds = seedsFile
            configuration.printUsage = usageFile

            // Same as -forceprocessing
            configuration.lastModified = Long.MAX_VALUE

            // Run ProGuard
            ProGuard(configuration).execute()
        } catch (e: Exception) {
            if (e is IOException) {
                throw e
            }

            throw IOException(e)
        }
    }

    private fun addAllInputsToConfiguration(
        configuration: Configuration,
        fileToFilter: ListMultimap<File, List<String>>
    ) {
        // Add the inputs from the consumed streams as program inputs
        // Set different filters for inputs containing only classes, only resources, and both types
        val classesSet = classes.toSet()
        val resourcesSet = resources.toSet()
        classesSet.minus(resourcesSet)
            .forEach { configuration.programJars.inputJar(it, fileToFilter, listOf("**.class")) }
        resourcesSet.minus(classesSet)
            .forEach { configuration.programJars.inputJar(it, fileToFilter, listOf("!**.class")) }
        classesSet.intersect(resourcesSet)
            .forEach { configuration.programJars.inputJar(it, fileToFilter) }

        // Add the inputs from the non-consumed streams as library inputs
        // Set different filters for inputs containing only classes, only resources, and both types
        val referencedClassesSet = referencedClasses.toSet()
        val referencedResourcesSet = referencedResources.toSet()

        referencedClassesSet.minus(referencedResourcesSet)
            .forEach {
                configuration.libraryJars.inputJar(
                    it,
                    fileToFilter,
                    listOf("**.class") + JAR_FILTER
                )
            }
        referencedResourcesSet.minus(referencedClassesSet)
            .forEach { configuration.libraryJars.inputJar(it, fileToFilter, listOf("!**.class")) }
        referencedClassesSet.intersect(referencedResourcesSet)
            .forEach { configuration.libraryJars.inputJar(it, fileToFilter, JAR_FILTER) }
    }
}

private fun Configuration.addKeepRule(keep: String) {
    if (this.keep == null) this.keep = mutableListOf<Any>()

    val classSpecification: ClassSpecification
    try {
        val parser = ConfigurationParser(arrayOf(keep), null)
        classSpecification = parser.parseClassSpecificationArguments()
    } catch (e: IOException) {
        // No IO happens when parsing in-memory strings.
        throw AssertionError(e)
    } catch (e: ParseException) {
        throw RuntimeException(e)
    }

    //noinspection unchecked
    this.keep.add(
        KeepClassSpecification(
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            null,
            classSpecification
        )
    )
}

private fun Configuration.addDontWarnRule(rule: String) {
    if (this.warn == null) this.warn = Lists.newArrayList<Any>()

    this.warn.addAll(ListUtil.commaSeparatedList(ClassUtil.internalClassName(rule)))
}

private fun ClassPath.inputJar(
    file: File,
    fileToFilter: ListMultimap<File, List<String>>,
    filter: List<String>? = null
) {

    if (!file.exists() || fileToFilter.containsEntry(file, filter)) {
        return
    }

    fileToFilter.put(file, filter)

    val classPathEntry = ClassPathEntry(file, false /*output*/)

    filter?.let {
        classPathEntry.filter = filter
    }

    this.add(classPathEntry)
}

private fun Configuration.outJar(file: File) {
    val classPathEntry = ClassPathEntry(file, true /*output*/)
    programJars.add(classPathEntry)
}

private fun Configuration.applyConfigurationFile(file: File) {
    // file might not actually exist if it comes from a sub-module library where publication
    // happen whether the file is there or not.
    if (!file.isFile) {
        return
    }

    this.applyConfigurationText(
        Files.asCharSource(file, Charsets.UTF_8).read(),
        "file '${file.path}'",
        file.parentFile
    )
}

private fun Configuration.applyConfigurationText(
    lines: String,
    description: String,
    baseDir: File?
) {
    val parser = ConfigurationParser(lines, description, baseDir, System.getProperties())
    try {
        parser.parse(this)
    } finally {
        parser.close()
    }
}
