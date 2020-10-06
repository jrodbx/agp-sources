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

@file:Suppress("SameParameterValue", "unused")

package com.android.tools.lint.model

import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.VALUE_TRUE
import com.android.ide.common.gradle.model.IdeVariant
import com.android.ide.common.repository.GradleVersion
import com.android.sdklib.AndroidVersion
import com.android.tools.lint.model.LintModelSerialization.LintModelSerializationAdapter
import com.android.tools.lint.model.LintModelSerialization.TargetFile
import com.android.tools.lint.model.LintModelSerialization.readDependencies
import com.android.tools.lint.model.LintModelSerialization.writeModule
import com.android.utils.XmlUtils
import com.google.common.base.Charsets
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_TAG
import org.xmlpull.v1.XmlPullParserException
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.Reader
import java.io.Writer

// TODO: Switch to kotlinx.serialization
//  (https://github.com/Kotlin/kotlinx.serialization)
// but this requires a Kotlin compiler plugin, requiring some changes to the build system,
// so avoiding it temporarily to unblock integrating by writing boring manual encoding
// and decoding code here

object LintModelSerialization : LintModelModuleLoader {
    enum class TargetFile {
        MODULE,
        VARIANT,
        DEPENDENCIES,
        LIBRARY_TABLE,
    }

    interface LintModelSerializationAdapter {
        /** Any path mapping to directory variables */
        val pathVariables: LintModelPathVariables

        /**
         * The file we're reading/writing from if known (only used for error message display)
         */
        fun file(target: TargetFile, variantName: String = "", artifactName: String = ""): File? =
            null

        /** The root folder for the project; used only for making paths relative in persistence */
        val root: File?

        /** Returns the reader to use to read the given target file */
        fun getReader(
            target: TargetFile,
            variantName: String = "",
            artifactName: String = ""
        ): Reader

        /** Returns the writer to use to read the given target file */
        fun getWriter(
            target: TargetFile,
            variantName: String = "",
            artifactName: String = ""
        ): Writer

        /**
         * For a given [file], produce a path with variables which applies the path
         * variable mapping and root file. If [relativeTo] is specified, it will also
         * consider that as a potential root to make the path relative to (without
         * a path variable).  This allows some paths to have a local "root" (this is
         * for example useful for libraries where all the various files (classes,
         * lint jar, etc) are relative to the library root.)
         */
        fun toPathString(file: File, relativeTo: File? = root): String {
            val fullPath = file.path

            for ((prefix, root) in pathVariables) {
                if (fullPath.startsWith(root.path) &&
                    fullPath.length > root.path.length &&
                    fullPath[root.path.length] == File.separatorChar
                ) {
                    val relative = fullPath.substring(root.path.length)
                    return "\$$prefix$relative"
                }
            }

            if (relativeTo != null &&
                fullPath.startsWith(relativeTo.path) &&
                fullPath.length > relativeTo.path.length &&
                fullPath[relativeTo.path.length] == File.separatorChar
            ) {
                return fullPath.substring(relativeTo.path.length + 1)
            }

            return fullPath
        }

        /** Reverses the path string computed by [toPathString] */
        fun fromPathString(path: String, relativeTo: File? = null): File {
            if (path.startsWith("$")) {
                for (i in 1 until path.length) {
                    val c = path[i]
                    if (!c.isJavaIdentifierPart()) {
                        val name = path.substring(1, i)
                        val dir = pathVariables.firstOrNull { it.first == name }?.second
                            ?: error("Path variable \$$name referenced in $path not provided to XML reader")
                        val relativeStart = if (c == '/' || c == '\\') i + 1 else i
                        return File(dir, path.substring(relativeStart))
                    }
                }
            }

            val file = File(path)
            return if (relativeTo != null && !file.isAbsolute) {
                File(relativeTo, path)
            } else {
                file
            }
        }
    }

    /** Default implementation of [LintModelSerializationAdapter] which uses files */
    class LintModelSerializationFileAdapter(
        override val root: File,
        override val pathVariables: LintModelPathVariables = emptyList()
    ) : LintModelSerializationAdapter {

        override fun file(target: TargetFile, variantName: String, artifactName: String): File {
            return when (target) {
                TargetFile.MODULE -> File(root, "module.xml")
                TargetFile.VARIANT -> File(root, getVariantFileName(variantName))
                TargetFile.DEPENDENCIES -> File(
                    root,
                    getDependenciesFileName(variantName, artifactName)
                )
                TargetFile.LIBRARY_TABLE -> File(
                    root,
                    getLibrariesFileName(variantName, artifactName)
                )
            }
        }

        override fun getReader(
            target: TargetFile,
            variantName: String,
            artifactName: String
        ): Reader {
            val file = file(target, variantName, artifactName)
            return BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8))
        }

        override fun getWriter(
            target: TargetFile,
            variantName: String,
            artifactName: String
        ): Writer {
            val file = file(target, variantName, artifactName)
            return file.toWriter()
        }
    }

    override fun getModule(folder: File): LintModelModule = readModule(folder)

    /**
     * Reads in the dependencies. If an (optional) library resolver is provided, merge
     * in any libraries found into that resolver such that it can be used to resolve
     * libraries. If not provided, a local library resolver will be created and associated
     * with the dependencies, available via [LintModelDependencies#getLibraryResolver].
     * The [source] is the folder containing the serialized model.
     */
    fun readDependencies(
        source: File,
        resolver: DefaultLintModelLibraryResolver? = null,
        variantName: String? = null,
        artifactName: String? = null,
        pathVariables: LintModelPathVariables = emptyList()
    ): LintModelDependencies {
        val adapter = LintModelSerializationFileAdapter(source, pathVariables)
        return LintModelDependenciesReader(
            adapter = adapter,
            libraryResolver = resolver,
            root = adapter.root,
            variantName = variantName ?: "",
            artifactName = artifactName ?: "",
            reader = source.bufferedReader()
        ).readDependencies()
    }

    /** Reads in the library definitions. If an (optional) library resolver is provided, merge
     * in any libraries found into that resolver such that it can be used to resolve
     * libraries. If not provided, a local library resolver will be created and associated
     * with the dependencies, available via [LintModelDependencies#getLibraryResolver].
     * The [source] is the folder containing the serialized model.
     */
    fun readLibraries(
        source: File,
        resolver: DefaultLintModelLibraryResolver? = null,
        variantName: String? = null,
        artifactName: String? = null,
        pathVariables: LintModelPathVariables = emptyList()
    ): LintModelLibraryResolver {
        val adapter = LintModelSerializationFileAdapter(source, pathVariables)
        return LintModelLibrariesReader(
            adapter = adapter,
            libraryResolver = resolver,
            root = adapter.root,
            variantName = variantName ?: "",
            artifactName = artifactName ?: "",
            reader = source.bufferedReader()
        ).readLibraries()
    }

    /**
     * Reads an XML descriptor from the given [source] and returns a lint model.
     * The [pathVariables] must include any path variables passed into [writeModule]
     * when the module was written.
     * The [source] is the folder containing the serialized model.
     */
    fun readModule(
        source: File,
        variantNames: List<String>? = null,
        readDependencies: Boolean = true,
        pathVariables: LintModelPathVariables = emptyList()
    ): LintModelModule {
        return readModule(
            adapter = LintModelSerializationFileAdapter(source, pathVariables),
            variantNames = variantNames,
            readDependencies = readDependencies
        )
    }

    /**
     * Reads an XML description from a [adapter] of a model and returns a lint model.
     * If [variantNames] is not null, limit the variants read into the model to just
     * the specified ones.
     */
    fun readModule(
        adapter: LintModelSerializationAdapter,
        variantNames: List<String>? = null,
        readDependencies: Boolean = true
    ): LintModelModule {
        return LintModelModuleReader(adapter).readModule(variantNames, readDependencies)
    }

    /**
     * Reads an XML description from a [reader] of a model and returns a lint dependency model.
     *
     * If an (optional) library resolver is provided, merge
     * in any libraries found into that resolver such that it can be used to resolve
     * libraries. If not provided, a local library resolver will be created and associated
     * with the dependencies, available via [LintModelDependencies#getLibraryResolver].
     *
     * The optional variant name indicates which variant this is intended to be used with.
     */
    fun readDependencies(
        reader: LintModelSerializationAdapter,
        root: File?,
        resolver: DefaultLintModelLibraryResolver? = null,
        variantName: String? = null,
        artifactName: String? = null
    ): LintModelDependencies {
        return LintModelDependenciesReader(
            reader,
            root,
            resolver,
            variantName ?: "",
            artifactName ?: ""
        ).readDependencies()
    }

    /** Reads an XML description from a [reader] of a lint model library table.
     *
     * If an (optional) library resolver is provided, merge
     * in any libraries found into that resolver such that it can be used to resolve
     * libraries. If not provided, a local library resolver will be created and associated
     * with the dependencies, available via [LintModelDependencies#getLibraryResolver].
     *
     * The optional variant name indicates which variant this is intended to be used with.
     */
    fun readLibraries(
        reader: LintModelSerializationAdapter,
        resolver: DefaultLintModelLibraryResolver? = null,
        variantName: String? = null,
        artifactName: String? = null
    ): LintModelLibraryResolver {
        return LintModelLibrariesReader(
            reader,
            resolver,
            reader.root,
            variantName ?: "",
            artifactName ?: ""
        ).readLibraries()
    }

    /**
     * Writes a lint [module] to the given [destination]. If [writeVariants] is not null,
     * it will also write the given variants into files next to the module file. By
     * default this includes all module variants.
     *
     * If applicable, you can also record which tool wrote this file (in the case of lint
     * for example, use LintClient.getClientDisplayRevision()) via the [createdBy] string.
     * Writes a lint [module] to the given [destination]
     */
    fun writeModule(
        module: LintModelModule,
        destination: LintModelSerializationAdapter,
        writeVariants: List<LintModelVariant>? = module.variants,
        writeDependencies: Boolean = true,
        createdBy: String? = null
    ) {
        val writer = LintModelModuleWriter(destination)
        writer.writeModule(module, writeVariants, writeDependencies, createdBy)
    }

    /**
     * Writes a lint [variant] to the given [writer]
     */
    fun writeVariant(
        variant: LintModelVariant,
        writer: LintModelSerializationAdapter,
        writeDependencies: Boolean = true,
        createdBy: String? = null
    ) {
        LintModelVariantWriter(writer, variant.name).writeVariant(
            variant,
            writeDependencies,
            createdBy
        )
    }

    /**
     * Writes a lint [dependencies] model to the given [destination] folder
     */
    fun writeDependencies(
        dependencies: LintModelDependencies,
        destination: File,
        variantName: String = "",
        artifactName: String = "",
        pathVariables: LintModelPathVariables = emptyList()
    ) {
        val adapter = LintModelSerializationFileAdapter(destination, pathVariables)
        val writer =
            LintModelDependenciesWriter(adapter, variantName, artifactName, destination.toWriter())
        writer.writeDependencies(dependencies)
    }

    /**
     * Writes a lint [dependencies] model to the given [writer]
     */
    fun writeDependencies(
        dependencies: LintModelDependencies,
        writer: LintModelSerializationAdapter,
        variantName: String = "",
        artifactName: String = ""
    ) {
        LintModelDependenciesWriter(writer, variantName, artifactName).writeDependencies(
            dependencies
        )
    }

    /**
     * Writes a lint [LintModelLibraryResolver] library table to the given [destination]
     */
    fun writeLibraries(
        libraryResolver: LintModelLibraryResolver,
        destination: File,
        variantName: String = "",
        artifactName: String = "",
        pathVariables: LintModelPathVariables = emptyList()
    ) {
        val adapter = LintModelSerializationFileAdapter(destination, pathVariables)
        val writer =
            LintModelLibrariesWriter(adapter, variantName, artifactName, destination.toWriter())
        writer.writeLibraries(libraryResolver)
    }

    /**
     * Writes a lint [LintModelLibraryResolver] library table to the given [writer]
     */
    fun writeLibraries(
        libraryResolver: LintModelLibraryResolver,
        writer: LintModelSerializationAdapter,
        variantName: String = "",
        artifactName: String = ""
    ) {
        LintModelLibrariesWriter(writer, variantName, artifactName).writeLibraries(libraryResolver)
    }

    /**
     * Writes a lint [module] to the given [destination] folder. If [writeVariants] is not null,
     * it will also write the given variants into files next to the module file. By
     * default this includes all module variants. If [writeDependencies] is set, the dependencies
     * and library files are written as well. The [pathVariables] list lets you
     * specify an ordered list of directories that should have a logical name in the
     * XML file instead. The [readModule] call needs to define the same variable names.
     * This allows the XML files to be relocatable.
     *
     * If applicable, you can also record which tool wrote these files (in the case of lint
     * for example, use LintClient.getClientDisplayRevision()) via the [createdBy] string.
     */
    fun writeModule(
        module: LintModelModule,
        destination: File,
        writeVariants: List<LintModelVariant>? = module.variants,
        writeDependencies: Boolean = true,
        pathVariables: LintModelPathVariables = emptyList(),
        createdBy: String? = null
    ) {
        writeModule(
            module,
            LintModelSerializationFileAdapter(destination, pathVariables),
            writeVariants,
            writeDependencies,
            createdBy
        )
    }

    /**
     * Writes a lint [variant] to the given [destination] folder. If applicable, you can also
     * record which tool wrote this file (in the case of lint for example, use
     * LintClient.getClientDisplayRevision()).
     */
    fun writeVariant(
        variant: LintModelVariant,
        destination: File,
        writeDependencies: Boolean = true,
        pathVariables: LintModelPathVariables = emptyList(),
        createdBy: String? = null
    ) {
        writeVariant(
            variant,
            LintModelSerializationFileAdapter(destination, pathVariables),
            writeDependencies,
            createdBy
        )
    }

    private fun getVariantFileName(variantName: String): String {
        return "${variantName}$DOT_XML"
    }

    private fun getDependenciesFileName(
        variantName: String,
        artifactName: String
    ): String {
        return "$variantName-$artifactName-dependencies$DOT_XML"
    }

    private fun getLibrariesFileName(
        variantName: String,
        artifactName: String
    ): String {
        return "$variantName-$artifactName-libraries$DOT_XML"
    }
}

/**
 * Creates a file writer for the given file, encoded as UTF-8. Not using stdlib's
 * File.bufferedWriter because we want to make sure the parent directory exists
 * and delete previous contents if necessary.
 */
private fun File.toWriter(): Writer {
    if (parentFile?.mkdirs() == false) {
        delete()
    }
    return BufferedWriter(OutputStreamWriter(FileOutputStream(this), Charsets.UTF_8))
}

private open class LintModelWriter(
    protected val adapter: LintModelSerializationAdapter,
    protected val printer: PrintWriter
) {
    protected var root: File? = adapter.root

    protected fun indent(level: Int) {
        for (i in 0 until level) {
            printer.print("  ")
        }
    }

    protected fun PrintWriter.printAttribute(name: String, value: String, indent: Int) {
        println()
        // +1: implicit, +1: hanging indent
        indent(indent + 2)
        print(name)
        print("=\"")
        print(XmlUtils.toXmlAttributeValue(value))
        print("\"")
    }

    protected fun PrintWriter.printName(name: String?, indent: Int) {
        name ?: return
        printAttribute("name", name, indent)
    }

    protected fun PrintWriter.printFile(
        name: String,
        file: File?,
        indent: Int,
        relativeTo: File? = root
    ) {
        file ?: return

        val path = adapter.toPathString(file, relativeTo)
        printAttribute(name, path, indent)
    }

    protected fun PrintWriter.printFiles(
        name: String,
        path: Collection<File>?,
        indent: Int,
        relativeTo: File? = root
    ) {
        path ?: return
        if (path.isEmpty()) {
            return
        }
        printAttribute(
            name,
            path.joinToString(File.pathSeparator) {
                adapter.toPathString(it, relativeTo)
            },
            indent
        )
    }

    protected fun PrintWriter.printStrings(
        name: String,
        strings: Collection<String>,
        indent: Int,
        separator: String = ","
    ) {
        if (strings.isEmpty()) {
            return
        }
        printAttribute(name, strings.joinToString(separator), indent)
    }

    protected fun writeSourceProviders(
        sourceProviders: List<LintModelSourceProvider>,
        tag: String,
        indent: Int
    ) {
        indent(indent)
        printer.print("<")
        printer.print(tag)
        printer.println(">")
        for (sourceProvider in sourceProviders) {
            writeSourceProvider(sourceProvider, indent + 1)
        }
        indent(indent)
        printer.print("</")
        printer.print(tag)
        printer.println(">")
    }

    protected fun writeSourceProvider(
        sourceProvider: LintModelSourceProvider,
        indent: Int,
        tag: String = "sourceProvider"
    ) {
        indent(indent)
        printer.print("<")
        printer.print(tag)
        printer.printFile("manifest", sourceProvider.manifestFile, indent)
        printer.printFiles("javaDirectories", sourceProvider.javaDirectories, indent)
        printer.printFiles("resDirectories", sourceProvider.resDirectories, indent)
        printer.printFiles("assetsDirectories", sourceProvider.assetsDirectories, indent)
        if (sourceProvider.isInstrumentationTest()) {
            printer.printAttribute("androidTest", VALUE_TRUE, indent)
        }
        if (sourceProvider.isUnitTest()) {
            printer.printAttribute("unitTest", VALUE_TRUE, indent)
        }
        if (sourceProvider.isDebugOnly()) {
            printer.printAttribute("debugOnly", VALUE_TRUE, indent)
        }
        printer.println("/>")
    }
}

private class LintModelModuleWriter(
    adapter: LintModelSerializationAdapter
) : LintModelWriter(
    adapter,
    PrintWriter(adapter.getWriter(TargetFile.MODULE))
) {
    fun writeModule(
        module: LintModelModule,
        writeVariants: List<LintModelVariant>? = module.variants,
        writeDependencies: Boolean,
        createdBy: String? = null
    ) {
        this.root = module.dir // write paths relative to the module dir

        val indent = 0
        printer.print("<lint-module")
        printer.printAttribute("format", "1", indent)
        // module root directory: not using printFile() here; don't want this to relative to self
        printer.printAttribute("dir", module.dir.path, indent)
        printer.printName(module.modulePath, indent)
        printer.printAttribute("type", module.type.name, indent)
        createdBy?.let { printer.printAttribute("createdBy", it, indent) }

        module.mavenName?.let { printer.printAttribute("maven", it.toString(), indent) }
        module.gradleVersion?.let { printer.printAttribute("gradle", it.toString(), indent) }
        printer.printFile("buildFolder", module.buildFolder, indent)
        module.resourcePrefix?.let { printer.printAttribute("resourcePrefix", it, indent) }
        printer.printStrings("dynamicFeatures", module.dynamicFeatures, indent)
        printer.printFiles("bootClassPath", module.bootClassPath, indent)
        printer.printAttribute("javaSourceLevel", module.javaSourceLevel, indent)
        printer.printAttribute("compileTarget", module.compileTarget, indent)
        if (module.neverShrinking()) {
            printer.printAttribute("neverShrinking", VALUE_TRUE, indent)
        }
        printer.printFiles("lintRuleJars", module.lintRuleJars, indent)

        printer.println(">")

        writeLintOptions(module.lintOptions, indent + 1)

        if (writeVariants != null) {
            for (variant in writeVariants) {
                writeVariantReference(variant, indent + 1)

                LintModelSerialization.writeVariant(
                    variant, adapter, writeDependencies, createdBy
                )
            }
        }

        printer.println("</lint-module>")
        printer.close()
    }

    private fun writeVariantReference(
        variant: LintModelVariant,
        indent: Int
    ) {
        indent(indent)
        printer.print("<variant name=\"")
        printer.print(variant.name)
        printer.println("\"/>")
    }

    private fun writeLintOptions(
        lintOptions: LintModelLintOptions,
        indent: Int
    ) {
        indent(indent)
        printer.print("<lintOptions")

        printer.printFile("lintConfig", lintOptions.lintConfig, indent)
        printer.printFile("baselineFile", lintOptions.baselineFile, indent)

        if (lintOptions.checkDependencies) {
            printer.printAttribute("checkDependencies", VALUE_TRUE, indent)
        }
        if (lintOptions.checkTestSources) {
            printer.printAttribute("checkTestSources", VALUE_TRUE, indent)
        }

        if (lintOptions.disable.isNotEmpty()) {
            printer.printStrings("disable", lintOptions.disable, indent)
        }
        if (lintOptions.enable.isNotEmpty()) {
            printer.printStrings("enable", lintOptions.enable, indent)
        }
        if (lintOptions.check?.isNotEmpty() == true) {
            printer.printStrings("check", lintOptions.disable, indent)
        }
        if (lintOptions.abortOnError) {
            printer.printAttribute("abortOnError", VALUE_TRUE, indent)
        }
        if (lintOptions.absolutePaths) {
            printer.printAttribute("absolutePaths", VALUE_TRUE, indent)
        }
        if (lintOptions.noLines) {
            printer.printAttribute("noLines", VALUE_TRUE, indent)
        }
        if (lintOptions.quiet) {
            printer.printAttribute("isQuiet", VALUE_TRUE, indent)
        }
        if (lintOptions.checkAllWarnings) {
            printer.printAttribute("checkAllWarnings", VALUE_TRUE, indent)
        }
        if (lintOptions.ignoreWarnings) {
            printer.printAttribute("ignoreWarnings", VALUE_TRUE, indent)
        }
        if (lintOptions.warningsAsErrors) {
            printer.printAttribute("warningsAsErrors", VALUE_TRUE, indent)
        }
        if (lintOptions.ignoreTestSources) {
            printer.printAttribute("ignoreTestSources", VALUE_TRUE, indent)
        }
        if (lintOptions.checkGeneratedSources) {
            printer.printAttribute("checkGeneratedSources", VALUE_TRUE, indent)
        }
        if (lintOptions.checkReleaseBuilds) {
            printer.printAttribute("checkReleaseBuilds", VALUE_TRUE, indent)
        }
        if (lintOptions.explainIssues) {
            printer.printAttribute("explainIssues", VALUE_TRUE, indent)
        }
        if (lintOptions.showAll) {
            printer.printAttribute("showAll", VALUE_TRUE, indent)
        }
        if (lintOptions.textReport) {
            printer.printAttribute("textReport", VALUE_TRUE, indent)
        }
        printer.printFile("textOutput", lintOptions.textOutput, indent)

        if (lintOptions.htmlReport) {
            printer.printAttribute("htmlReport", VALUE_TRUE, indent)
        }
        printer.printFile("htmlOutput", lintOptions.htmlOutput, indent)
        if (lintOptions.xmlReport) {
            printer.printAttribute("xmlReport", VALUE_TRUE, indent)
        }
        printer.printFile("xmlOutput", lintOptions.xmlOutput, indent)

        if (!writeSeverityOverrides(lintOptions.severityOverrides, indent + 1)) {
            printer.println("/>")
        } else {
            indent(indent)
            printer.println("</lintOptions>")
        }
    }

    private fun writeSeverityOverrides(
        severityOverrides: Map<String, LintModelSeverity>?,
        indent: Int
    ): Boolean {
        severityOverrides ?: return false
        if (severityOverrides.isEmpty()) return false

        printer.println(">")
        indent(indent)
        printer.println("<severities>")
        severityOverrides.asSequence().sortedBy { it.key }.forEach { (id, severity) ->
            indent(indent + 1)
            printer.print("<severity")
            printer.printAttribute("id", id, indent)
            printer.printAttribute("severity", severity.name, indent)
            printer.println(" />")
        }
        indent(indent)
        printer.println("</severities>")
        return true
    }
}

private class LintModelVariantWriter(
    adapter: LintModelSerializationAdapter,
    private val variantName: String,
    writer: Writer = adapter.getWriter(TargetFile.VARIANT, variantName)
) : LintModelWriter(
    adapter,
    PrintWriter(writer)
) {
    fun writeVariant(
        variant: LintModelVariant,
        writeDependencies: Boolean = true,
        createdBy: String? = null
    ) {
        this.root = variant.module.dir // write paths relative to the module dir
        val indent = 0
        indent(indent)
        printer.print("<variant")
        printer.printName(variant.name, indent)
        createdBy?.let { printer.printAttribute("createdBy", it, indent) }
        if (variant.useSupportLibraryVectorDrawables) {
            printer.printAttribute("useSupportLibraryVectorDrawables", VALUE_TRUE, indent)
        }
        // These used to be on the mergedFlavor
        variant.`package`?.let { printer.printAttribute("package", it, indent) }
        variant.minSdkVersion?.let { printer.printAttribute("minSdkVersion", it.apiString, indent) }
        variant.targetSdkVersion
            ?.let { printer.printAttribute("targetSdkVersion", it.apiString, indent) }
        if (variant.debuggable) {
            printer.printAttribute("debuggable", VALUE_TRUE, indent)
        }
        if (variant.shrinkable) {
            printer.printAttribute("shrinking", VALUE_TRUE, indent)
        }
        variant.mergedManifest?.let { printer.printFile("mergedManifest", it, indent) }
        variant.manifestMergeReport?.let { printer.printFile("manifestMergeReport", it, indent) }

        printer.printFiles("proguardFiles", variant.proguardFiles, indent)
        printer.printFiles("consumerProguardFiles", variant.consumerProguardFiles, indent)
        printer.printStrings("resourceConfigurations", variant.resourceConfigurations, indent)
        printer.println(">")

        writeBuildFeatures(variant.buildFeatures, indent + 1)
        writeSourceProviders(variant.sourceProviders, "sourceProviders", indent + 1)
        writeSourceProviders(variant.testSourceProviders, "testSourceProviders", indent + 1)

        writeResValues(variant.resValues, indent + 1)
        writeManifestPlaceholders(variant.manifestPlaceholders, indent + 1)

        writeArtifact(variant.mainArtifact, "mainArtifact", indent + 1, writeDependencies)
        variant.androidTestArtifact?.let { artifact ->
            writeArtifact(artifact, "androidTestArtifact", indent + 1, writeDependencies)
        }
        variant.testArtifact?.let { artifact ->
            writeArtifact(artifact, "testArtifact", indent + 1, writeDependencies)
        }

        indent(indent)
        printer.println("</variant>")
        printer.close()
    }

    private fun writeBuildFeatures(
        buildFeatures: LintModelBuildFeatures,
        indent: Int
    ) {
        indent(indent)
        printer.print("<buildFeatures")
        if (buildFeatures.coreLibraryDesugaringEnabled) {
            printer.printAttribute("coreLibraryDesugaring", VALUE_TRUE, indent)
        }
        if (buildFeatures.viewBinding) {
            printer.printAttribute("viewBinding", VALUE_TRUE, indent)
        }
        if (buildFeatures.namespacingMode != LintModelNamespacingMode.DISABLED) {
            printer.printAttribute("namespacing", buildFeatures.namespacingMode.name, indent)
        }
        printer.println("/>")
    }

    private fun writeManifestPlaceholders(manifestPlaceholders: Map<String, String>, indent: Int) {
        if (manifestPlaceholders.isEmpty()) {
            return
        }
        indent(indent)
        printer.println("<manifestPlaceholders>")
        manifestPlaceholders.asSequence().sortedBy { it.key }.forEach {
            val key = it.key
            val value = it.value
            indent(indent + 1)
            printer.print("<placeholder")
            printer.printName(key, indent + 1)
            printer.printAttribute("value", value, indent + 1)
            printer.println(" />")
        }
        indent(indent)
        printer.println("</manifestPlaceholders>")
    }

    private fun writeResValues(resValues: Map<String, LintModelResourceField>, indent: Int) {
        if (resValues.isEmpty()) {
            return
        }
        indent(indent)
        printer.println("<resValues>")
        resValues.asSequence().sortedBy { it.key }.forEach {
            indent(indent + 1)
            printer.print("<resValue")
            val resourceField = it.value
            printer.printAttribute("type", resourceField.type, indent + 1)
            printer.printName(resourceField.name, indent + 1)
            printer.printAttribute("value", resourceField.value, indent + 1)
            printer.println(" />")
        }
        indent(indent)
        printer.println("</resValues>")
    }

    private fun writeArtifact(
        artifact: LintModelArtifact,
        tag: String,
        indent: Int,
        writeDependencies: Boolean
    ) {
        indent(indent)
        printer.print("<")
        printer.print(tag)

        printer.printFiles("classOutputs", artifact.classOutputs, indent)
        if (artifact is LintModelAndroidArtifact) {
            printer.printAttribute("applicationId", artifact.applicationId, indent)
            printer.printFiles("generatedSourceFolders", artifact.generatedSourceFolders, indent)
            printer.printFiles(
                "generatedResourceFolders",
                artifact.generatedResourceFolders,
                indent
            )
        }
        printer.println(">")

        indent(indent)
        printer.print("</")
        printer.print(tag)
        printer.println(">")

        if (writeDependencies) {
            val dependencyWriter = LintModelDependenciesWriter(adapter, variantName, tag)
            dependencyWriter.writeDependencies(artifact.dependencies)

            val libraryWriter = LintModelLibrariesWriter(adapter, variantName, tag)
            libraryWriter.writeLibraries(
                artifact.dependencies.getLibraryResolver(),
                artifact.dependencies
            )
        }
    }
}

typealias LintModelPathVariables = List<Pair<String, File>>

private class LintModelDependenciesWriter(
    adapter: LintModelSerializationAdapter,
    variantName: String,
    artifactName: String,
    writer: Writer = adapter.getWriter(TargetFile.DEPENDENCIES, variantName, artifactName)
) : LintModelWriter(
    adapter,
    PrintWriter(writer)
) {
    fun writeDependencies(dependencies: LintModelDependencies) {
        val indent = 0
        indent(indent)
        printer.println("<dependencies>")

        writeDependencyGraph("compile", dependencies.compileDependencies, indent + 1)
        writeDependencyGraph("package", dependencies.packageDependencies, indent + 1)

        indent(indent)
        printer.println("</dependencies>")
        printer.close()
    }

    private fun writeDependencyGraph(tag: String, graph: LintModelDependencyGraph, indent: Int) {
        val graphItems =
            LinkedHashMap<String, LintModelDependency>() // LinkedHashMap: preserve order
        if (graph.roots.isEmpty()) {
            return
        }
        for (item in graph.roots) {
            addDependencies(item, graphItems)
        }

        indent(indent)
        printer.print("<")
        printer.print(tag)
        val roots = graph.roots.joinToString(separator = ",") { it.artifactAddress }
        printer.printAttribute("roots", roots, indent)
        printer.println(">")
        for (item in graphItems) {
            writeDependency(item.value, indent + 1)
        }
        indent(indent)
        printer.print("</")
        printer.print(tag)
        printer.println(">")
    }

    private fun addDependencies(
        item: LintModelDependency,
        map: MutableMap<String, LintModelDependency>
    ) {
        if (map.containsKey(item.artifactAddress)) {
            return
        }

        map[item.artifactAddress] = item

        for (dependency in item.dependencies) {
            addDependencies(dependency, map)
        }
    }

    private fun writeDependency(library: LintModelDependency, indent: Int) {
        indent(indent)
        printer.print("<dependency")
        printer.printName(library.artifactAddress, indent)
        if (!library.artifactAddress.startsWith(library.artifactName)) {
            printer.printAttribute("simpleName", library.artifactName, indent)
        }
        val requestedCoordinates = library.requestedCoordinates
        if (requestedCoordinates != null && requestedCoordinates != library.artifactAddress) {
            printer.printAttribute("requested", requestedCoordinates, indent)
        }
        val roots = library.dependencies.joinToString(separator = ",") { it.artifactAddress }
        if (roots.isNotEmpty()) {
            printer.printAttribute("dependencies", roots, indent)
        }
        printer.println("/>")
    }
}

private class LintModelLibrariesWriter(
    adapter: LintModelSerializationAdapter,
    variantName: String,
    artifactName: String,
    writer: Writer = adapter.getWriter(TargetFile.LIBRARY_TABLE, variantName, artifactName)
) : LintModelWriter(
    adapter,
    PrintWriter(writer)
) {
    /**
     * Writes out the libraries in the given resolver.
     * If a given dependencies filter is provided, limit the emitted libraries to just
     * those referenced by the specific dependencies instance.
     */
    fun writeLibraries(
        resolver: LintModelLibraryResolver,
        filter: LintModelDependencies? = null
    ) {
        val indent = 0
        indent(indent)
        printer.println("<libraries>")
        val libraries = if (filter != null) {
            val set = filter.compileDependencies.getAllLibraries().toMutableSet()
            set.addAll(filter.packageDependencies.getAllLibraries())
            resolver.getAllLibraries().asSequence().filter { set.contains(it) }
        } else {
            resolver.getAllLibraries().asSequence()
        }
        for (library in libraries) {
            writeLibrary(library, indent + 1)
        }

        indent(indent)
        printer.println("</libraries>")
        printer.close()
    }

    private fun writeLibrary(library: LintModelLibrary, indent: Int) {
        indent(indent)
        printer.print("<library")
        printer.printName(library.artifactAddress, indent)
        if (library is LintModelExternalLibrary) {
            printer.printFiles("jars", library.jarFiles, indent)
            printer.printAttribute("resolved", library.resolvedCoordinates.toString(), indent)
        }
        if (library is LintModelModuleLibrary) {
            library.projectPath.let { printer.printAttribute("project", it, indent) }
        }
        if (library.provided) {
            printer.printAttribute("provided", VALUE_TRUE, indent)
        }
        if (library is LintModelAndroidLibrary) {
            printer.printFile("folder", library.folder, indent)
            printer.printFile("manifest", library.manifest, indent, library.folder)
            printer.printFile("resFolder", library.resFolder, indent, library.folder)
            printer.printFile("assetsFolder", library.assetsFolder, indent, library.folder)
            printer.printFile("lintJar", library.lintJar, indent, library.folder)
            printer.printFile("publicResources", library.publicResources, indent, library.folder)
            printer.printFile("symbolFile", library.symbolFile, indent, library.folder)
            printer.printFile(
                "externalAnnotations",
                library.externalAnnotations,
                indent,
                library.folder
            )
            printer.printFile("proguardRules", library.proguardRules, indent, library.folder)
        }
        printer.println("/>")
    }
}

private abstract class LintModelReader(
    protected val adapter: LintModelSerializationAdapter,
    protected var root: File?,
    reader: Reader
) {
    protected abstract val path: String
    protected val parser = KXmlParser()

    init {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(reader)
    }

    protected fun String.toApiVersion(): AndroidVersion {
        return AndroidVersion(this)
    }

    protected fun String.toMavenCoordinate(): LintModelMavenName? {
        return LintModelMavenName.parse(this)
    }

    protected fun getLocation(): String {
        return "$path:${parser.lineNumber}"
    }

    protected fun expectTag(name: String) {
        if (name != parser.name) {
            error("Unexpected tag `<${parser.name}>` at ${getLocation()}; expected `<$name>`")
        }
    }

    protected fun unexpectedTag() {
        error("Unexpected tag `<${parser.name}>` at ${getLocation()}")
    }

    protected fun missingData() {
        error("Missing data at ${getLocation()}")
    }

    protected fun finishTag(name: String) {
        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                unexpectedTag()
            } else if (eventType == END_TAG) {
                expectTag(name)
                break
            }
        }
    }

    protected fun getOptionalAttribute(name: String): String? {
        return parser.getAttributeValue(null, name)
    }

    protected fun getRequiredAttribute(name: String): String {
        return parser.getAttributeValue(null, name)
            ?: error("Expected `$name` attribute in <${parser.name}> tag at ${getLocation()}")
    }

    protected fun getName(): String = getRequiredAttribute("name")

    protected fun getOptionalFile(attribute: String): File? {
        val path = getOptionalAttribute(attribute) ?: return null
        return adapter.fromPathString(path, root)
    }

    protected fun getRequiredFile(attribute: String, relativeTo: File? = root): File {
        val path = getRequiredAttribute(attribute)
        return adapter.fromPathString(path, relativeTo)
    }

    protected fun getOptionalBoolean(
        attribute: String,
        default: Boolean
    ): Boolean {
        val value = getOptionalAttribute(attribute) ?: return default
        return value == VALUE_TRUE
    }

    // Reverses [printStrings]
    protected fun getStrings(name: String, separator: String = ","): List<String> {
        return getOptionalAttribute(name)?.split(separator) ?: emptyList()
    }

    // Reverses [printFiles]
    protected fun getFiles(name: String): List<File> {
        return getOptionalAttribute(name)?.split(File.pathSeparator)?.map { path ->
            val file = File(path)
            if (root != null && !file.isAbsolute) {
                File(root, path)
            } else {
                file
            }
        } ?: emptyList()
    }

    protected fun readSourceProvider(tag: String = "sourceProvider"): LintModelSourceProvider {
        expectTag(tag)
        val manifestFile = getRequiredFile("manifest")
        val javaDirectories = getFiles("javaDirectories")
        val resDirectories = getFiles("resDirectories")
        val assetsDirectories = getFiles("assetsDirectories")
        val androidTestOnly = getOptionalBoolean("androidTest", false)
        val unitTestOnly = getOptionalBoolean("unitTest", false)
        val debugOnly = getOptionalBoolean("debugOnly", false)
        finishTag(tag)

        return DefaultLintModelSourceProvider(
            manifestFile = manifestFile,
            javaDirectories = javaDirectories,
            resDirectories = resDirectories,
            assetsDirectories = assetsDirectories,
            debugOnly = debugOnly,
            instrumentationTestOnly = androidTestOnly,
            unitTestOnly = unitTestOnly
        )
    }

    protected fun readSourceProviders(tag: String): List<LintModelSourceProvider> {
        expectTag(tag)

        val sourceProviders = mutableListOf<LintModelSourceProvider>()

        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "sourceProvider" -> sourceProviders.add(readSourceProvider())
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag(tag)
                break
            }
        }

        return sourceProviders
    }
}

private class LintModelModuleReader(
    adapter: LintModelSerializationAdapter
) : LintModelReader(adapter, adapter.root, adapter.getReader(TargetFile.MODULE)) {
    override val path: String
        get() = adapter.file(TargetFile.MODULE)?.path ?: "<unknown>"

    private fun readLintOptions(): LintModelLintOptions {
        expectTag("lintOptions")
        val isCheckTestSources = getOptionalBoolean("checkTestSources", false)
        val lintConfig = getOptionalFile("lintConfig")
        val isCheckDependencies = getOptionalBoolean("checkDependencies", false)
        val baselineFile = getOptionalFile("baselineFile")
        var severityOverrides: Map<String, LintModelSeverity>? = null
        val enable = getStrings("enable").let {
            if (it.isNotEmpty()) it.toSet() else emptySet()
        }
        val disable = getStrings("disable").let {
            if (it.isNotEmpty()) it.toSet() else emptySet()
        }
        val check = getStrings("check").let {
            if (it.isNotEmpty()) it.toSet() else null
        }
        val abortOnError = getOptionalBoolean("abortOnError", false)
        val absolutePaths = getOptionalBoolean("absolutePaths", false)
        val noLines = getOptionalBoolean("noLines", false)
        val quiet = getOptionalBoolean("quiet", false)
        val checkAllWarnings = getOptionalBoolean("checkAllWarnings", false)
        val ignoreWarnings = getOptionalBoolean("ignoreWarnings", false)
        val warningsAsErrors = getOptionalBoolean("warningsAsErrors", false)
        val ignoreTestSources = getOptionalBoolean("ignoreTestSources", false)
        val checkGeneratedSources = getOptionalBoolean("checkGeneratedSources", false)
        val checkReleaseBuilds = getOptionalBoolean("checkReleaseBuilds", false)
        val explainIssues = getOptionalBoolean("explainIssues", false)
        val showAll = getOptionalBoolean("showAll", false)
        val textReport = getOptionalBoolean("textReport", false)
        val textOutput = getOptionalFile("textOutput")
        val htmlReport = getOptionalBoolean("htmlReport", false)
        val htmlOutput = getOptionalFile("htmlOutput")
        val xmlReport = getOptionalBoolean("xmlReport", false)
        val xmlOutput = getOptionalFile("xmlOutput")

        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "severities" -> severityOverrides = readSeverities()
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("lintOptions")
                break
            }
        }

        return DefaultLintModelLintOptions(
            checkTestSources = isCheckTestSources,
            lintConfig = lintConfig,
            checkDependencies = isCheckDependencies,
            baselineFile = baselineFile,
            severityOverrides = severityOverrides,
            enable = enable,
            disable = disable,
            check = check,
            abortOnError = abortOnError,
            absolutePaths = absolutePaths,
            noLines = noLines,
            quiet = quiet,
            checkAllWarnings = checkAllWarnings,
            ignoreWarnings = ignoreWarnings,
            warningsAsErrors = warningsAsErrors,
            ignoreTestSources = ignoreTestSources,
            checkGeneratedSources = checkGeneratedSources,
            checkReleaseBuilds = checkReleaseBuilds,
            explainIssues = explainIssues,
            showAll = showAll,
            textReport = textReport,
            textOutput = textOutput,
            htmlReport = htmlReport,
            htmlOutput = htmlOutput,
            xmlReport = xmlReport,
            xmlOutput = xmlOutput
        )
    }

    private fun readSeverities(): Map<String, LintModelSeverity> {
        expectTag("severities")

        val map = mutableMapOf<String, LintModelSeverity>()
        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "severity" -> {
                        val id = getRequiredAttribute("id")
                        val severityString = getRequiredAttribute("severity")
                        val severity = LintModelSeverity.fromName(severityString)
                            ?: error("Unexpected severity $severityString for id $id at ${getLocation()}")
                        map[id] = severity
                        finishTag("severity")
                    }
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("severities")
                break
            }
        }

        return map
    }

    fun readModule(
        variantNames: List<String>? = null,
        readDependencies: Boolean
    ): LintModelModule {
        try {
            parser.nextTag()
            expectTag("lint-module")
            val dir = getRequiredFile("dir")
            root = dir
            val name = getName()
            val type = LintModelModuleType.valueOf(getRequiredAttribute("type"))
            val mavenString = getOptionalAttribute("maven")?.let {
                LintModelMavenName.parse(it)
            }
            val gradleVersion = getOptionalAttribute("gradle")?.let { (GradleVersion.tryParse(it)) }

            val buildFolder = getRequiredFile("buildFolder")
            val resourcePrefix = getOptionalAttribute("resourcePrefix")
            val dynamicFeatures = getStrings("dynamicFeatures")
            val bootClassPath = getFiles("bootClassPath")
            val javaSourceLevel = getRequiredAttribute("javaSourceLevel")
            val compileTarget = getRequiredAttribute("compileTarget")
            val neverShrinking = getOptionalBoolean("neverShrinking", false)
            val variants = mutableListOf<LintModelVariant>()
            val lintRuleJars = getFiles("lintRuleJars")
            var lintOptions: LintModelLintOptions? = null

            loop@ while (parser.next() != END_DOCUMENT) {
                val eventType = parser.eventType
                if (eventType != START_TAG) {
                    continue
                }
                when (parser.name) {
                    "lintOptions" -> lintOptions = readLintOptions()
                    "variant" -> break@loop
                    else -> unexpectedTag()
                }
            }

            if (lintOptions == null) {
                missingData()
            }

            val module = DefaultLintModelModule(
                loader = LintModelSerialization,
                dir = dir,
                modulePath = name,
                type = type,
                mavenName = mavenString,
                gradleVersion = gradleVersion,
                buildFolder = buildFolder,
                lintOptions = lintOptions!!,
                lintRuleJars = lintRuleJars,
                resourcePrefix = resourcePrefix,
                dynamicFeatures = dynamicFeatures,
                bootClassPath = bootClassPath,
                javaSourceLevel = javaSourceLevel,
                compileTarget = compileTarget,
                neverShrinking = neverShrinking,

                // still empty list; will construct it below
                variants = variants,
                oldProject = null
            )

            // Always last; requires separate handling since we need to pass in the
            // constructed module to each variant
            if (parser.name == "variant") {
                readVariantReference(module, variantNames, readDependencies)?.let {
                    variants.add(it)
                }
                while (parser.next() != END_DOCUMENT) {
                    val eventType = parser.eventType
                    if (eventType != START_TAG) {
                        continue
                    }
                    val tag = parser.name
                    if (tag == "variant") {
                        readVariantReference(module, variantNames, readDependencies)?.let {
                            variants.add(it)
                        }
                    } else {
                        unexpectedTag()
                    }
                }
            }

            return module
        } catch (e: XmlPullParserException) {
            throw IOException(e)
        }
    }

    private fun readVariantReference(
        module: LintModelModule,
        variantNames: List<String>?,
        readDependencies: Boolean
    ): LintModelVariant? {
        expectTag("variant")
        val variantName = getName()
        finishTag("variant")
        return if (variantNames == null || variantNames.contains(variantName)) {
            val reader = LintModelVariantReader(adapter, root, variantName)
            reader.readVariant(module, readDependencies)
        } else {
            null
        }
    }
}

private class LintModelVariantReader(
    adapter: LintModelSerializationAdapter,
    root: File?,
    private val variantName: String,
    reader: Reader = adapter.getReader(TargetFile.VARIANT, variantName)
) : LintModelReader(adapter, root, reader) {
    override val path: String
        get() = adapter.file(TargetFile.VARIANT, variantName)?.path ?: "<unknown>"
    private val libraryResolverMap = mutableMapOf<String, LintModelLibrary>()
    private val libraryResolver = DefaultLintModelLibraryResolver(libraryResolverMap)

    private fun readResValues(): Map<String, LintModelResourceField> {
        expectTag("resValues")

        val resValues: MutableMap<String, LintModelResourceField> = mutableMapOf()

        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "resValue" -> {
                        val value = readResValue()
                        resValues[value.name] = value
                    }
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("resValues")
                break
            }
        }

        return resValues
    }

    private fun readResValue(): LintModelResourceField {
        expectTag("resValue")
        val type = getRequiredAttribute("type")
        val name = getName()
        val value = getRequiredAttribute("value")

        finishTag("resValue")
        return DefaultLintModelResourceField(type = type, name = name, value = value)
    }

    private fun readBuildFeatures(): LintModelBuildFeatures {
        expectTag("buildFeatures")
        val coreLibraryDesugaringEnabled = getOptionalBoolean("coreLibraryDesugaring", false)
        val viewBinding = getOptionalBoolean("viewBinding", false)
        val namespacingMode =
            getOptionalAttribute("nameSpacingMode")?.let { LintModelNamespacingMode.valueOf(it) }
                ?: LintModelNamespacingMode.DISABLED

        finishTag("buildFeatures")
        return DefaultLintModelBuildFeatures(
            viewBinding = viewBinding,
            coreLibraryDesugaringEnabled = coreLibraryDesugaringEnabled,
            namespacingMode = namespacingMode
        )
    }

    private fun readManifestPlaceholders(): Map<String, String> {
        expectTag("manifestPlaceholders")

        val placeholders: MutableMap<String, String> = mutableMapOf()

        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "placeholder" -> {
                        val name = getName()
                        val value = getRequiredAttribute("value")
                        finishTag("placeholder")
                        placeholders[name] = value
                    }
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("manifestPlaceholders")
                break
            }
        }

        return placeholders
    }

    private fun readAndroidArtifact(
        tag: String,
        readDependencies: Boolean
    ): LintModelAndroidArtifact {
        return readArtifact(
            tag,
            android = true,
            readDependencies = readDependencies
        ) as LintModelAndroidArtifact
    }

    private fun readJavaArtifact(tag: String, readDependencies: Boolean): LintModelJavaArtifact {
        return readArtifact(
            tag,
            android = false,
            readDependencies = readDependencies
        ) as LintModelJavaArtifact
    }

    private fun readArtifact(
        tag: String,
        android: Boolean,
        readDependencies: Boolean
    ): LintModelArtifact {
        expectTag(tag)

        val classOutputs = getFiles("classOutputs")

        val applicationId: String
        val generatedSourceFolders: Collection<File>
        val generatedResourceFolders: Collection<File>
        if (android) {
            applicationId = getRequiredAttribute("applicationId")
            generatedSourceFolders = getFiles("generatedSourceFolders")
            generatedResourceFolders = getFiles("generatedResourceFolders")
        } else {
            applicationId = ""
            generatedSourceFolders = emptyList()
            generatedResourceFolders = emptyList()
        }
        finishTag(tag)

        val resolver: DefaultLintModelLibraryResolver
        val dependencies: LintModelDependencies
        if (readDependencies) {
            resolver = LintModelLibrariesReader(
                adapter, root = root, variantName = variantName, artifactName = tag
            ).readLibraries()

            dependencies = readDependencies(
                adapter, root = root, variantName = variantName,
                artifactName = tag, resolver = resolver
            )
        } else {
            resolver = DefaultLintModelLibraryResolver(emptyMap())
            val empty = DefaultLintModelDependencyGraph(emptyList(), resolver)
            dependencies = DefaultLintModelDependencies(empty, empty, resolver)
        }

        return if (android) {
            DefaultLintModelAndroidArtifact(
                applicationId = applicationId,
                generatedResourceFolders = generatedResourceFolders,
                generatedSourceFolders = generatedSourceFolders,
                classOutputs = classOutputs,
                dependencies = dependencies
            )
        } else {
            DefaultLintModelJavaArtifact(
                classFolders = classOutputs,
                dependencies = dependencies
            )
        }
    }

    fun readVariant(module: LintModelModule, readDependencies: Boolean = true): LintModelVariant {
        try {
            parser.nextTag()
            expectTag("variant")
            getOptionalFile("dir")?.let { root = it }
            val name = getName()
            val useSupportLibraryVectorDrawables =
                getOptionalBoolean("useSupportLibraryVectorDrawables", false)
            var mainArtifact: LintModelAndroidArtifact? = null
            var testArtifact: LintModelJavaArtifact? = null
            var androidTestArtifact: LintModelAndroidArtifact? = null
            val mergedManifest: File? = getOptionalFile("mergedManifest")
            val manifestMergeReport: File? = getOptionalFile("manifestMergeReport")
            val oldVariant: IdeVariant? = null

            val packageName = getOptionalAttribute("package")
            val minSdkVersion = getOptionalAttribute("minSdkVersion")?.toApiVersion()
            val targetSdkVersion = getOptionalAttribute("targetSdkVersion")?.toApiVersion()
            val debuggable = getOptionalBoolean("debuggable", false)
            val shrinkable = getOptionalBoolean("shrinking", false)
            val proguardFiles = getFiles("proguardFiles")
            val consumerProguardFiles = getFiles("consumerProguardFiles")
            val resourceConfigurations = getStrings("resourceConfigurations")
            var resValues: Map<String, LintModelResourceField> = emptyMap()
            var manifestPlaceholders: Map<String, String> = emptyMap()
            var sourceProviders: List<LintModelSourceProvider> = emptyList()
            var testSourceProviders: List<LintModelSourceProvider> = emptyList()
            var buildFeatures: LintModelBuildFeatures? = null

            expectTag("variant")

            while (parser.next() != END_DOCUMENT) {
                val eventType = parser.eventType
                if (eventType == START_TAG) {
                    when (parser.name) {
                        "resValues" -> resValues = readResValues()
                        "manifestPlaceholders" -> manifestPlaceholders = readManifestPlaceholders()
                        "mainArtifact" ->
                            mainArtifact =
                                readAndroidArtifact(parser.name, readDependencies)
                        "androidTestArtifact" ->
                            androidTestArtifact =
                                readAndroidArtifact(parser.name, readDependencies)
                        "testArtifact" ->
                            testArtifact =
                                readJavaArtifact(parser.name, readDependencies)
                        "sourceProviders" -> sourceProviders = readSourceProviders(parser.name)
                        "testSourceProviders" ->
                            testSourceProviders =
                                readSourceProviders(parser.name)
                        "buildFeatures" -> buildFeatures = readBuildFeatures()
                        else -> unexpectedTag()
                    }
                } else if (eventType == END_TAG) {
                    expectTag("variant")
                    break
                }
            }

            if (mainArtifact == null || buildFeatures == null) {
                missingData()
            }

            return DefaultLintModelVariant(
                module = module,
                name = name,
                useSupportLibraryVectorDrawables = useSupportLibraryVectorDrawables,
                mainArtifact = mainArtifact!!,
                androidTestArtifact = androidTestArtifact,
                testArtifact = testArtifact,
                mergedManifest = mergedManifest,
                manifestMergeReport = manifestMergeReport,
                oldVariant = oldVariant,
                `package` = packageName,
                minSdkVersion = minSdkVersion,
                targetSdkVersion = targetSdkVersion,
                proguardFiles = proguardFiles,
                consumerProguardFiles = consumerProguardFiles,
                resourceConfigurations = resourceConfigurations,
                resValues = resValues,
                manifestPlaceholders = manifestPlaceholders,
                sourceProviders = sourceProviders,
                testSourceProviders = testSourceProviders,
                debuggable = debuggable,
                shrinkable = shrinkable,
                buildFeatures = buildFeatures!!,
                libraryResolver = libraryResolver
            )
        } catch (e: XmlPullParserException) {
            throw IOException(e)
        }
    }
}

// per variant: <variant.xml>, <libraries.xml>, <dependencies.xml>
private class LintModelDependenciesReader(
    adapter: LintModelSerializationAdapter,
    root: File?,
    libraryResolver: DefaultLintModelLibraryResolver? = null,
    private val variantName: String,
    private val artifactName: String,
    reader: Reader = adapter.getReader(TargetFile.DEPENDENCIES, variantName, artifactName)
) : LintModelReader(adapter, root, reader) {
    private val libraryResolverMap: MutableMap<String, LintModelLibrary> =
        libraryResolver?.libraryMap as? MutableMap<String, LintModelLibrary> ?: mutableMapOf()
    private val libraryResolver =
        libraryResolver ?: DefaultLintModelLibraryResolver(libraryResolverMap)

    override val path: String
        get() = adapter.file(TargetFile.DEPENDENCIES, variantName, artifactName)?.path
            ?: "<unknown>"

    @Suppress("MoveVariableDeclarationIntoWhen")
    fun readDependencies(): LintModelDependencies {
        parser.nextTag()
        expectTag("dependencies")
        var compileDependencies: LintModelDependencyGraph? = null
        var packageDependencies: LintModelDependencyGraph? = null
        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                val tag = parser.name
                when (tag) {
                    "compile" -> compileDependencies = readDependencyGraph(tag)
                    "package" -> packageDependencies = readDependencyGraph(tag)
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("dependencies")
                break
            }
        }

        // If omitted from the XML, the dependencies are empty
        if (compileDependencies == null) {
            compileDependencies = DefaultLintModelDependencyGraph(emptyList(), libraryResolver)
        }

        if (packageDependencies == null) {
            packageDependencies = DefaultLintModelDependencyGraph(emptyList(), libraryResolver)
        }

        return DefaultLintModelDependencies(
            compileDependencies = compileDependencies,
            packageDependencies = packageDependencies,
            libraryResolver = libraryResolver
        )
    }

    private fun readDependencyGraph(tag: String): LintModelDependencyGraph {
        expectTag(tag)

        val rootList = getRequiredAttribute("roots").split(",")
        val allNodes = mutableMapOf<String, LazyLintModelDependency>()

        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "dependency" -> {
                        val item = readGraphItem()
                        allNodes[item.artifactAddress] = item
                    }
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag(tag)
                break
            }
        }

        val roots = rootList.mapNotNull { allNodes[it] }

        // Fix up the dependencies properties in the nodes; they were left as empty lists
        // when we read in each node (since they may refer to items that haven't been read in
        // yet); we now have everything in the map indexed by address, so go and add these
        // to the child dependency lists
        for (item in allNodes.values) {
            val dependencyIds = item.dependencyIds
            if (dependencyIds.isEmpty()) {
                continue
            }
            for (address in dependencyIds.split(",")) {
                allNodes[address]?.let { item.dependencies.add(it) }
            }
        }

        return DefaultLintModelDependencyGraph(
            roots = roots,
            libraryResolver = libraryResolver
        )
    }

    private fun readGraphItem(): LazyLintModelDependency {
        expectTag("dependency")

        val artifactAddress = getName()
        val artifactName = getOptionalAttribute("simpleName") ?: run {
            val index1 = artifactAddress.indexOf(':')
            val index2 = artifactAddress.indexOf(':', index1 + 1)
            if (index2 == -1) {
                artifactAddress
            } else {
                artifactAddress.substring(0, index2)
            }
        }
        val requestedCoordinates = getOptionalAttribute("requested") ?: artifactAddress
        val dependencyIds = getOptionalAttribute("dependencies") ?: ""

        finishTag("dependency")
        return LazyLintModelDependency(
            artifactName = artifactName,
            artifactAddress = artifactAddress,
            requestedCoordinates = requestedCoordinates,
            libraryResolver = libraryResolver,
            dependencyIds = dependencyIds
        )
    }
}

// per variant: <variant.xml>, <libraries.xml>, <dependencies.xml>
private class LintModelLibrariesReader(
    adapter: LintModelSerializationAdapter,
    libraryResolver: DefaultLintModelLibraryResolver? = null,
    root: File?,
    private val variantName: String,
    private val artifactName: String,
    reader: Reader = adapter.getReader(TargetFile.LIBRARY_TABLE, variantName, artifactName)
) : LintModelReader(adapter, root, reader) {
    private val libraryResolverMap: MutableMap<String, LintModelLibrary> =
        libraryResolver?.libraryMap as? MutableMap<String, LintModelLibrary> ?: mutableMapOf()
    private val libraryResolver =
        libraryResolver ?: DefaultLintModelLibraryResolver(libraryResolverMap)

    override val path: String
        get() = adapter.file(TargetFile.DEPENDENCIES, variantName, artifactName)?.path
            ?: "<unknown>"

    fun readLibraries(): DefaultLintModelLibraryResolver {
        parser.nextTag()
        expectTag("libraries")

        while (parser.next() != END_DOCUMENT) {
            val eventType = parser.eventType
            if (eventType == START_TAG) {
                when (parser.name) {
                    "library" -> {
                        val library = readLibrary()
                        libraryResolverMap[library.artifactAddress] = library
                    }
                    else -> unexpectedTag()
                }
            } else if (eventType == END_TAG) {
                expectTag("libraries")
                break
            }
        }

        return libraryResolver
    }

    private fun readLibrary(): LintModelLibrary {
        expectTag("library")
        var android = false

        val artifactAddress = getName()
        val jars = getFiles("jars")
        val project = getOptionalAttribute("project")
        val resolved = getOptionalAttribute("resolved")?.toMavenCoordinate()
        val provided = getOptionalBoolean("provided", false)

        // Android library?

        var manifestFile: File? = null
        var resFolder: File? = null
        var assetsFolder: File? = null
        var lintJar: File? = null
        var publicResources: File? = null
        var symbolFile: File? = null
        var externalAnnotations: File? = null
        var proguardRules: File? = null

        val folder = getOptionalFile("folder")
        if (folder != null) {
            android = true

            resFolder = getRequiredFile("resFolder", folder)
            assetsFolder = getRequiredFile("assetsFolder", folder)
            lintJar = getRequiredFile("lintJar", folder)
            publicResources = getRequiredFile("publicResources", folder)
            symbolFile = getRequiredFile("symbolFile", folder)
            manifestFile = getRequiredFile("manifest", folder)
            externalAnnotations = getRequiredFile("externalAnnotations", folder)
            proguardRules = getRequiredFile("proguardRules", folder)
        }

        finishTag("library")

        return when {
            project != null -> DefaultLintModelModuleLibrary(
                projectPath = project,
                artifactAddress = artifactAddress,
                lintJar = lintJar,
                provided = provided
            )
            android -> DefaultLintModelAndroidLibrary(
                artifactAddress = artifactAddress,
                jarFiles = jars,
                manifest = manifestFile!!,
                folder = folder!!,
                resFolder = resFolder!!,
                assetsFolder = assetsFolder!!,
                lintJar = lintJar!!,
                publicResources = publicResources!!,
                symbolFile = symbolFile!!,
                externalAnnotations = externalAnnotations!!,
                proguardRules = proguardRules!!,
                provided = provided,
                resolvedCoordinates = resolved!!
            )
            else -> DefaultLintModelJavaLibrary(
                artifactAddress = artifactAddress,
                jarFiles = jars,
                provided = provided,
                resolvedCoordinates = resolved!!
            )
        }
    }
}

/**
 * Implementation of [LintModelDependency] with a mutable child list such that we
 * can initialize the [dependencies] child list after all nodes have been
 * read in (since some of the dependency id's can refer to elements that have
 * not been read in yet.)
 */
private class LazyLintModelDependency(
    artifactName: String,
    artifactAddress: String,
    requestedCoordinates: String?,
    libraryResolver: LintModelLibraryResolver,
    val dependencyIds: String
) : DefaultLintModelDependency(
    artifactName = artifactName,
    artifactAddress = artifactAddress,
    requestedCoordinates = requestedCoordinates,
    dependencies = emptyList(),
    libraryResolver = libraryResolver
) {
    override var dependencies: MutableList<LintModelDependency> = mutableListOf()
}
