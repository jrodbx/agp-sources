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
@file:JvmName("SymbolExportUtils")

package com.android.builder.symbols

import com.android.ide.common.symbols.IdProvider
import com.android.ide.common.symbols.RGeneration
import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.getPackageNameFromManifest
import com.android.ide.common.symbols.mergeAndRenumberSymbols
import com.android.ide.common.symbols.parseManifest
import com.android.resources.ResourceType
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import java.io.File
import java.io.IOException
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path

/**
 * Processes the symbol table and generates necessary files: R.txt, R.java. Afterwards generates
 * `R.java` or `R.jar` for all libraries the main library depends on.
 *
 * @param librarySymbols table with symbols of resources for the library.
 * @param depSymbolTables symbol tables of the libraries which this library depends on
 * @param mainPackageName package name of this library
 * @param manifestFile manifest file
 * @param sourceOut directory to contain R.java
 * @param rClassOutputJar file to output R.jar.
 * @param symbolFileOut R.txt file location
 * @param nonTransitiveRClass if true, the generated R class for this library and the  R.txt will
 *                         contain only the resources defined in this library, otherwise they will
 *                         contain all the resources merged from the transitive dependencies.
 */
@Throws(IOException::class)
fun processLibraryMainSymbolTable(
        librarySymbols: SymbolTable,
        depSymbolTables: List<SymbolTable>,
        mainPackageName: String?,
        manifestFile: File,
        rClassOutputJar: File?,
        symbolFileOut: File?,
        platformSymbols: SymbolTable,
        nonTransitiveRClass: Boolean,
        generateDependencyRClasses: Boolean,
        idProvider: IdProvider
) {

    // Parse the manifest only when necessary.
    val finalPackageName = mainPackageName ?: getPackageNameFromManifest(parseManifest(manifestFile))

    // Get symbol tables of the libraries we depend on.
    val tablesToWrite =
        processLibraryMainSymbolTable(
            finalPackageName,
            librarySymbols,
            depSymbolTables,
            platformSymbols,
            nonTransitiveRClass,
            symbolFileOut?.toPath(),
            generateDependencyRClasses,
            idProvider
        )

    if (rClassOutputJar != null) {
        FileUtils.deleteIfExists(rClassOutputJar)
        exportToCompiledJava(tablesToWrite, rClassOutputJar.toPath())
    }
}

@VisibleForTesting
internal fun processLibraryMainSymbolTable(
    finalPackageName: String,
    librarySymbols: SymbolTable,
    depSymbolTables: List<SymbolTable>,
    platformSymbols: SymbolTable,
    nonTransitiveRClass: Boolean,
    symbolFileOut: Path?,
    generateDependencyRClasses: Boolean = true,
    idProvider: IdProvider = IdProvider.sequential()
): List<SymbolTable> {
    // Merge all the symbols together.
    // We have to rewrite the IDs because some published R.txt inside AARs are using the
    // wrong value for some types, and we need to ensure there is no collision in the
    // file we are creating.
    val allSymbols: SymbolTable = mergeAndRenumberSymbols(
        finalPackageName, librarySymbols, depSymbolTables, platformSymbols, idProvider
    )

    val mainSymbolTable = if (nonTransitiveRClass) allSymbols.filter(librarySymbols) else allSymbols

    // Generate R.txt file.
    symbolFileOut?.let {
        Files.createDirectories(it.parent)
        SymbolIo.writeForAar(mainSymbolTable, it)
    }

    return if (generateDependencyRClasses) {
        RGeneration.generateAllSymbolTablesToWrite(allSymbols, mainSymbolTable, depSymbolTables)
    } else {
        ImmutableList.of(mainSymbolTable)
    }
}


fun writeSymbolListWithPackageName(table: SymbolTable, writer: Writer) {
    writer.write(table.tablePackage)
    writer.write('\n'.code)

    for (resourceType in ResourceType.values()) {
        val symbols = table.getSymbolByResourceType(resourceType)
        for (symbol in symbols) {
            writer.write(resourceType.getName())
            writer.write(' '.code)
            writer.write(symbol.canonicalName)
            if (symbol is Symbol.StyleableSymbol) {
                for (child in symbol.children) {
                    writer.write(' '.code)
                    writer.write(child)
                }
            }
            writer.write('\n'.code)
        }
    }
}

/**
 * Writes the symbol table treating all symbols as public in the AAR R.txt format.
 *
 * See [SymbolIo.readFromPublicTxtFile] for the reading counterpart.
 *
 * The format does not include styleable children (see `SymbolExportUtilsTest`)
 */
fun writePublicTxtFile(table: SymbolTable, writer: Writer) {
    for (resType in ResourceType.values()) {
        val symbols =
            table.getSymbolByResourceType(resType)
        for (s in symbols) {
            writer.write(s.resourceType.getName())
            writer.write(' '.code)
            writer.write(s.canonicalName)
            writer.write('\n'.code)
        }
    }
}
