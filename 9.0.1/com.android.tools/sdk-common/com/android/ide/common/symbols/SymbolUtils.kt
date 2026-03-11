/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

@file: JvmName("SymbolUtils")

package com.android.ide.common.symbols

import com.android.SdkConstants
import com.android.ide.common.xml.AndroidManifestParser
import com.android.ide.common.xml.ManifestData
import com.android.io.FileWrapper
import com.android.resources.ResourceType
import com.android.xml.AndroidManifest
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.CharMatcher
import com.google.common.base.Splitter
import com.google.common.collect.HashMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Writer
import java.util.SortedSet
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.set

/** Helper methods related to Symbols and resource processing. */

private val NORMALIZED_VALUE_NAME_CHARS =
    CharMatcher.anyOf(".:").precomputed()

private const val ANDROID_UNDERSCORE_PREFIX = "android_"
private const val KEEP_RULE_PREFIX = "-keep class "
private const val KEEP_RULE_SUFFIX = " { <init>(...); }"

fun mergeAndRenumberSymbols(
    mainPackageName: String,
    librarySymbols: SymbolTable,
    dependencySymbols: Collection<SymbolTable>,
    platformSymbols: SymbolTable,
    idProvider: IdProvider = IdProvider.sequential()
): SymbolTable {

    /*
     For most symbol types, we are simply going to loop on all the symbols, and merge them in
     the final table while renumbering them.
     For Styleable arrays we will handle things differently. We cannot rely on the array values,
     as some R.txt were published with placeholder values. We are instead simply going to merge
     the children list from all the styleable, and create the symbol from this list.
    */

    // Merge the library symbols into the same collection as the dependencies. There's no
    // order or preference, and this allows just looping on them all
    val tables = ArrayList<SymbolTable>(dependencySymbols.size + 1)
    tables.add(librarySymbols)
    tables.addAll(dependencySymbols)

    // first pass, we use two different multi-map to record all symbols.
    // 1. resourceType -> name. This is for all by the Styleable symbols
    // 2. styleable name -> children. This is for styleable only.
    val newSymbolMap = HashMultimap.create<ResourceType, String>()
    val arrayToAttrs = HashMap<String, MutableSet<String>>()

    tables.forEach { table ->
        table.symbols.values().forEach { symbol ->
            when (symbol) {
                is Symbol.AttributeSymbol -> newSymbolMap.put(
                    ResourceType.ATTR,
                    symbol.canonicalName
                )
                is Symbol.NormalSymbol -> newSymbolMap.put(
                    symbol.resourceType,
                    symbol.canonicalName
                )
                is Symbol.StyleableSymbol -> {
                    arrayToAttrs
                        .getOrPut(symbol.canonicalName) { HashSet() }
                        .addAll(symbol.children)
                }
                else -> throw IOException("Unexpected symbol $symbol")
            }
        }
    }

    // the builder for the table
    val tableBuilder = SymbolTable.builder().tablePackage(mainPackageName)

    // let's keep a map of the new ATTR names to symbol so that we can find them easily later
    // when we process the styleable
    val attrToValue = HashMap<String, Symbol.AttributeSymbol>()

    // process the normal symbols
    for (resourceType in newSymbolMap.keySet()) {
        val symbolNames = Lists.newArrayList(newSymbolMap.get(resourceType))
        symbolNames.sort()

        for (symbolName in symbolNames) {
            val value = idProvider.next(resourceType)
            val newSymbol: Symbol
            if (resourceType == ResourceType.ATTR) {
                newSymbol = Symbol.attributeSymbol(symbolName, value, false)
                // Also store the new ATTR value in the map.
                attrToValue[symbolName] = newSymbol
            } else {
                newSymbol = Symbol.normalSymbol(
                    resourceType = resourceType,
                    name = symbolName, // All names are canonical at this point.
                    canonicalName = symbolName,
                    intValue = value
                )
            }
            tableBuilder.add(newSymbol)
        }
    }

    // process the arrays.
    arrayToAttrs.forEach { arrayName, children ->
        val attributes = children.sorted()

        // now get the attributes values using the new symbol map
        val attributeValues = ImmutableList.builder<Int>()
        for (attribute in attributes) {
            // Resources coming from this module might have the "android:" prefix, but the ones
            // coming from dependencies might have the "android_" prefix.
            if (attribute.startsWith(SdkConstants.ANDROID_NS_NAME_PREFIX)
                || attribute.startsWith(ANDROID_UNDERSCORE_PREFIX)
            ) {
                val name = attribute.substring(SdkConstants.ANDROID_NS_NAME_PREFIX_LEN)

                val platformSymbol = platformSymbols.symbols.get(ResourceType.ATTR, name)
                if (platformSymbol != null) {
                    attributeValues.add(platformSymbol.intValue)
                    continue
                }
            }

            // If it's not a resource from the platform, try finding the ID in the attrToValue map
            val symbol = attrToValue[attribute]
            if (symbol != null) {
                // symbol can be null if the symbol table is broken. This is possible
                // some non-final AAR built with non final Gradle.
                // e.g.  com.android.support:appcompat-v7:26.0.0-beta2
                attributeValues.add(symbol.intValue)
            } else {
                // If we couldn't find the ID, add a non-valid ID of "0" so that the R.txt file is
                // still parse-able.
                attributeValues.add(0)
            }
        }

        tableBuilder.add(
            Symbol.styleableSymbol(
                arrayName,
                attributeValues.build(),
                ImmutableList.copyOf(attributes),
                canonicalName = arrayName // All names are canonical at this point.
            )
        )
    }

    return tableBuilder.build()
}

/**
 * Pulls out the package name from the given android manifest.
 *
 * @param manifestFile manifest file of the library
 * @return package name held in the manifest
 * @throws IOException if there is a problem reading the manifest or if the manifest does not
 *     contain a package name
 */
@Throws(IOException::class)
fun getPackageNameFromManifest(manifestFile: File): String {
    val manifestData = try {
        AndroidManifestParser.parse(FileWrapper(manifestFile))
    } catch (e: SAXException) {
        throw IOException(
            "Failed to parse android manifest XML file at path: '${manifestFile.absolutePath}'",
            e
        )
    } catch (e: IOException) {
        throw IOException(
            "Failed to parse android manifest XML file at path: '${manifestFile.absolutePath}'",
            e
        )
    }
    return manifestData.`package`
}

/**
 * Pulls out the package name from the given parsed android manifest.
 *
 * @param manifest the parsed manifest of the library
 * @return package name held in the manifest
 */
fun getPackageNameFromManifest(manifest: ManifestData): String = manifest.`package`

/**
 * Generates keep rules based on the nodes declared in the manifest file.
 *
 * <p>Used in the new resource processing, since aapt is not used in processing libraries'
 * resources and the {@code aapt_rules.txt} file and its rules are required by minify.
 *
 * <p>Goes through all {@code application}, {@code instrumentation}, {@code activity}, {@code
 * service}, {@code provider} and {@code receiver} keep class data in the manifest, generates
 * keep rules for each of them and returns them as a list.
 *
 * <p>For examples refer to {@code SymbolUtilsTest.java}.
 *
 * @param manifest containing keep class data
 */
fun generateMinifyKeepRules(manifest: ManifestData, mergedResources: File?): Set<String> {
    return generateKeepRules(manifest, false, mergedResources)
}

@VisibleForTesting
fun generateKeepRules(
    manifest: ManifestData,
    isMainDex: Boolean,
    mergedResources: File?
): Set<String> {
    val rules: SortedSet<String> = sortedSetOf()
    rules.add("# Generated by the gradle plugin")

    // Find all the rules based on the AndroidManifest
    for (keepClass in manifest.keepClasses) {
        if (isMainDex) {
            // When creating keep rules for Dex, we should sometimes omit some activity, service
            // provider and receiver nodes. It is based on the process declared in their node or,
            // if none was specified or was empty, on the default process of the application.
            // If the process was not declared, was empty or starts with a colon symbol (last
            // case meaning private process), we do not need to keep that class.
            val type = keepClass.type
            val process = keepClass.process
            if ((type == AndroidManifest.NODE_ACTIVITY
                        || type == AndroidManifest.NODE_SERVICE
                        || type == AndroidManifest.NODE_PROVIDER
                        || type == AndroidManifest.NODE_RECEIVER)
                && (process == null || process.isEmpty() || process.startsWith(":"))
            ) {
                continue
            }
        }
        rules.add("$KEEP_RULE_PREFIX${keepClass.name}$KEEP_RULE_SUFFIX")
    }

    // Now go through all the layout files and find classes that need to be kept
    if (mergedResources != null) {
        try {
            val documentBuilderFactory =
                DocumentBuilderFactory.newInstance()
            val documentBuilder = documentBuilderFactory.newDocumentBuilder()

            for (typeDir in mergedResources.listFiles()) {
                if (typeDir.isDirectory && typeDir.name.startsWith("layout")) {
                    for (layoutXml in typeDir.listFiles()) {
                        if (layoutXml.isFile) {
                            generateKeepRulesFromLayoutXmlFile(
                                layoutXml, documentBuilder, rules
                            )
                        }
                    }
                }
            }
        } catch (e: ParserConfigurationException) {
            throw IOException("Failed to read merged resources", e)
        }
    }

    return rules
}

@Throws(IOException::class)
fun generateKeepRulesFromLayoutXmlFile(
    layout: File,
    documentBuilder: DocumentBuilder,
    rules: SortedSet<String>
) {
    try {
        val xmlDocument = documentBuilder.parse(layout)
        val root = xmlDocument.documentElement
        if (root != null) {
            generateKeepRulesFromXmlNode(root, rules)
        }
    } catch (e: SAXException) {
        throw IOException(
            "Failed to parse XML resource file " + layout.absolutePath, e
        )
    } catch (e: IOException) {
        throw IOException(
            "Failed to parse XML resource file " + layout.absolutePath, e
        )
    }
}

private fun generateKeepRulesFromXmlNode(node: Element, rules: SortedSet<String>) {
    val tag = node.tagName
    if (tag.contains(".")) {
        rules.add("$KEEP_RULE_PREFIX$tag$KEEP_RULE_SUFFIX")
    }

    var current: Node? = node.firstChild
    while (current != null) {
        if (current.nodeType == Node.ELEMENT_NODE) {
            // handle its children
            generateKeepRulesFromXmlNode(current as Element, rules)
        }
        current = current.nextSibling
    }
}

@Throws(IOException::class)
fun parseMinifiedKeepRules(proguardRulesFile: File): SortedSet<String> {
    val keepClasses = sortedSetOf<String>()
    if (!proguardRulesFile.isFile) {
        throw IOException("Failed to parse proguard rules at path: " +
          proguardRulesFile.absolutePath)
    }

    proguardRulesFile.forEachLine {
        if (it.startsWith(KEEP_RULE_PREFIX) && it.endsWith(KEEP_RULE_SUFFIX)) {
            keepClasses.add(it)
        }
    }
    return keepClasses
}

@Throws(IOException::class)
fun parseManifest(manifestFile: File): ManifestData {
    return try {
        AndroidManifestParser.parse(FileWrapper(manifestFile))
    } catch (e: SAXException) {
        throw IOException(
            "Failed to parse android manifest XML file at path: '"
                    + manifestFile.absolutePath,
            e
        )
    } catch (e: IOException) {
        throw IOException(
            "Failed to parse android manifest XML file at path: '"
                    + manifestFile.absolutePath,
            e
        )
    }
}

/**
 * Updates the value resource name to mimic aapt's behaviour - replaces all dots and colons with
 * underscores.
 *
 * <p>If the name contains whitespaces or other illegal characters, they are not checked in this
 * method, but caught in the Symbol constructor call to {@link
 * Symbol#createAndValidateSymbol(ResourceType, String, SymbolJavaType, String, List)}.
 *
 * @param name the resource name to be updated
 * @return a valid resource name
 */
fun canonicalizeValueResourceName(name: String): String =
    NORMALIZED_VALUE_NAME_CHARS.replaceFrom(name, '_')

private val VALUE_ID_SPLITTER = Splitter.on(',').trimResults()

fun valueStringToInt(valueString: String) =
    if (valueString.startsWith("0x")) {
        Integer.parseUnsignedInt(valueString.substring(2), 16)
    } else {
        Integer.parseInt(valueString)
    }

fun parseArrayLiteral(size: Int, valuesString: String): ImmutableList<Int> {
    if (size == 0) {
        if (!valuesString.subSequence(1, valuesString.length - 1).isBlank()) {
            failParseArrayLiteral(size, valuesString)
        }
        return ImmutableList.of()
    }
    val ints = ImmutableList.builder<Int>()

    val values = VALUE_ID_SPLITTER.split(
        valuesString.subSequence(
            1,
            valuesString.length - 1
        )
    ).iterator()
    for (i in 0 until size) {
        if (!values.hasNext()) {
            failParseArrayLiteral(size, valuesString)
        }
        val value = values.next()
        // Starting S, android attrs might be unstable and in that case instead of a value we will
        // have a reference to the android.R here instead (e.g. android.R.attr.lStar). In that case
        // just parse it as a zero, and then re-create when writing the R.jar (the name matches the
        // child exactly, e.g. a child attr "android:foo" will reference android.R.attr.foo).
        if (value.startsWith("android")) {
            ints.add(0)
        } else {
            ints.add(valueStringToInt(value))
        }
    }
    if (values.hasNext()) {
        failParseArrayLiteral(size, valuesString)
    }

    return ints.build()
}

fun failParseArrayLiteral(size: Int, valuesString: String): Nothing {
    throw IOException("""Values string $valuesString should have $size item(s).""")
}

/**
 * A visitor to process symbols in a lightweight way.
 *
 * Calls should only be made in the sequence exactly once.
 * [visit] ([symbol] ([child])*)* [visitEnd]
 */
interface SymbolListVisitor {
    fun visit()
    fun symbol(resourceType: CharSequence, name: CharSequence)
    /** Visit a child of a styleable symbol, only ever called after styleable symbols. */
    fun child(name: CharSequence)

    fun visitEnd()
}

/**
 * Read a symbol table from [lines] and generate events for the given [visitor].
 */
@Throws(IOException::class)
fun readAarRTxt(lines: Iterator<String>, visitor: SymbolListVisitor) {

    visitor.visit()
    // When a styleable parent is encountered,
    // consume any children if the line starts with
    var styleableChildPrefix: String? = null
    while (lines.hasNext()) {
        val line = lines.next()
        if (styleableChildPrefix != null && line.startsWith(styleableChildPrefix)) {
            // Extract the child name and write it to the same line.
            val start = styleableChildPrefix.length + 1
            val end = line.indexOf(' ', styleableChildPrefix.length)
            if (end != -1) {
                visitor.child(line.substring(start, end))
            }
            continue
        }

        // Ignore out-of-order styleable children
        if (line.startsWith("int styleable ")) {
            continue
        }
        //          start     middle          end
        //            |         |              |
        //      "int[] styleable AppCompatTheme {750,75..."

        // Allows the symbol list with package name writer to only keep the type and the name,
        // so the example becomes "styleable AppCompatTheme <child> <child>"
        val start = line.indexOf(' ') + 1
        if (start == 0) {
            continue
        }
        val middle = line.indexOf(' ', start) + 1
        if (middle == 0) {
            continue
        }
        val end = line.indexOf(' ', middle) + 1
        if (end == 0) {
            continue
        }
        visitor.symbol(line.subSequence(start, middle - 1), line.subSequence(middle, end - 1))
        if (line.startsWith("int[] ")) {
            styleableChildPrefix = "int styleable " + line.substring(middle, end - 1)
        } else {
            styleableChildPrefix = null
        }
    }
    visitor.visitEnd()
}

/** Generate events of an empty symbol table for the given [visitor] */
fun visitEmptySymbolTable(visitor: SymbolListVisitor) {
    visitor.visit()
    visitor.visitEnd()
}

/**
 * Writes symbols in the AGP internal 'Symbol list with package name' format.
 *
 * This collapses the styleable children so the subsequent lines have the format
 * `"<type> <canonical_name>[ <child>[ <child>[ ...]]]"`
 *
 * See [SymbolIo.writeSymbolListWithPackageName] for use.
 *
 * @param packageName The package name for the project.
 *                    If not null, it will be written as the first line of output.
 * @param writer The writer to write the resulting symbol table with package name to.
 */
class SymbolListWithPackageNameWriter(
    private val packageName: String?,
    private val writer: Writer
) : SymbolListVisitor,
    Closeable {

    override fun visit() {
        packageName?.let { writer.append(it) }
    }

    override fun symbol(resourceType: CharSequence, name: CharSequence) {
        writer.append('\n')
        writer.append(resourceType)
        writer.append(' ')
        writer.append(name)
    }

    override fun child(name: CharSequence) {
        writer.append(' ')
        writer.append(name)
    }

    override fun visitEnd() {
        writer.append('\n')
    }

    override fun close() {
        writer.close()
    }
}

/**
 * Collects symbols in an in-memory SymbolTable.
 *
 * @param packageName The package for the symbol table
 *
 */
class SymbolTableBuilder(packageName: String) : SymbolListVisitor {
    private val symbolTableBuilder: SymbolTable.Builder =
        SymbolTable.builder().tablePackage(packageName)

    private var currentStyleable: String? = null
    private var children = ImmutableList.builder<String>()

    private var _symbolTable: SymbolTable? = null

    /**
     * The collected symbols.
     * Will throw [IllegalStateException] if called before the symbol table has been visited.
     */
    val symbolTable: SymbolTable
        get() = _symbolTable
            ?: throw IllegalStateException("Must finish visit before getting table.")

    override fun visit() {
    }

    override fun symbol(resourceType: CharSequence, name: CharSequence) {
        symbol(ResourceType.fromClassName(resourceType.toString())!!, name.toString())
    }

    private fun writeCurrentStyleable() {
        currentStyleable?.let {
            symbolTableBuilder.add(Symbol.styleableSymbol(it, ImmutableList.of(), children.build()))
            currentStyleable = null
            children = ImmutableList.builder()
        }
    }

    private fun symbol(resourceType: ResourceType, name: String) {
        writeCurrentStyleable()
        when (resourceType) {
            ResourceType.STYLEABLE -> currentStyleable = name
            ResourceType.ATTR -> symbolTableBuilder.add(Symbol.attributeSymbol(name, 0))
            else -> symbolTableBuilder.add(Symbol.normalSymbol(resourceType, name, 0))
        }
    }

    override fun child(name: CharSequence) {
        children.add(name.toString())
    }

    override fun visitEnd() {
        writeCurrentStyleable()
        _symbolTable = symbolTableBuilder.build()
    }
}

fun rTxtToSymbolTable(inputStream: InputStream, packageName: String): SymbolTable {
    val symbolTableBuilder = SymbolTableBuilder(packageName)
    inputStream.bufferedReader().use {
        readAarRTxt(it.lines().iterator(), symbolTableBuilder)
    }
    return symbolTableBuilder.symbolTable
}
