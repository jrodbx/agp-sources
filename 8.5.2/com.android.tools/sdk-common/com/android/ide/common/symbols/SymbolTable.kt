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

package com.android.ide.common.symbols

import com.android.SdkConstants
import com.android.annotations.concurrency.Immutable
import com.android.resources.ResourceType
import com.android.resources.ResourceVisibility
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableTable
import com.google.common.collect.Interner
import com.google.common.collect.Maps
import com.google.common.collect.Table
import com.google.common.collect.Tables
import java.io.File
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import javax.lang.model.SourceVersion

private const val ANDROID_ATTR_PREFIX = "android_"

/**
 * List of [Symbols] identifying resources in an Android application. [SymbolTables] do not only
 * exist for applications: they can be used for other building blocks for applications, such as
 * libraries or atoms.
 *
 * A [SymbolTable] keeps a list of instances of [Symbol], each one with a unique pair of class and
 * name. [SymbolTable]s also have a [tablePackage] property. This should be unique and is used
 * to generate the `R.java` file.
 */
@Immutable
abstract class SymbolTable protected constructor() {

    abstract val tablePackage: String
    abstract val symbols: ImmutableTable<ResourceType, String, Symbol>

    private data class SymbolTableImpl(
            override val tablePackage: String,
            override val symbols: ImmutableTable<ResourceType, String, Symbol>) : SymbolTable() {

        override fun toString() =
            symbols.values().joinToString("\n  ", "SymbolTable ($tablePackage)\n ")
    }

    /**
     * Produces and returns a subset of this [SymbolTable] including only [Symbol]s that are
     * also included in [table]. In other words, a [Symbol] `s` will exist in the result if and only
     * if `s` exists in `this` and there is a symbol `s1` in [table] such that
     * `s.resourceType == s1.resourceType && s.name == s1.name`.
     */
    fun filter(table: SymbolTable): SymbolTable {
        val builder = ImmutableTable.builder<ResourceType, String, Symbol>()

        for (resourceType in table.symbols.rowKeySet()) {
            val allowedNames = this.symbols.row(resourceType).keys
            for (s1 in table.symbols.row(resourceType).values) {
                if (s1.canonicalName in allowedNames) {
                    this.symbols.get(resourceType, s1.canonicalName)?.let { s ->
                        builder.put(resourceType, s1.canonicalName, s)
                    }
                }
            }
        }

        return SymbolTableImpl(tablePackage, builder.build())
    }

    /** Returns the result of calling [Companion.merge] on `this` and [m]. */
    fun merge(m: SymbolTable) = merge(listOf(this, m))

    /**.
     * Builds and returns a new [SymbolTable] with the same symbols as `this`, but renamed with
     * [tablePackage].
     */
    fun rename(tablePackage: String) : SymbolTable = SymbolTableImpl(tablePackage, symbols)

    /**
     * Returns a [List] of all the [Symbol]s in the table with a particular [ResourceType].
     *
     * The [List] is sorted by name to make output predictable for testing.
     */
    fun getSymbolByResourceType(type: ResourceType): List<Symbol> =
        Collections.unmodifiableList(symbols.row(type).values.sortedBy(Symbol::canonicalName))

    /**
     * Returns a [List] of all the [Symbol]s in the table with a particular [ResourceVisibility].
     *
     * The [List] is sorted by name to make output predictable for testing.
     */
    fun getSymbolByVisibility(visibility: ResourceVisibility): List<Symbol> {
        val symbols = symbols.values().filter { it.resourceVisibility == visibility }
            .sortedBy(Symbol::canonicalName)
        return Collections.unmodifiableList(symbols)
    }

    /** Returns whether the table contains a [Symbol] with matching [type] and [canonicalName]. */
    fun containsSymbol(type: ResourceType, canonicalName: String): Boolean {
        if (symbols.contains(type, canonicalName)) return true
        // If the symbol is a styleable it is likely that we're looking for a styleable child.
        // These are stored under the parent's symbol, so try finding the parent first and then the
        // child under it. If we haven't found the parent, then the child doesn't exist either.
        return type == ResourceType.STYLEABLE
                && maybeGetStyleableParentSymbolForChild(canonicalName) != null
    }

    /**
     * Tries to retrieve a `declare-styleable`'s parent for the given child name. For example:
     * <pre>
     *     <declare-styleable name="s1">
     *         <item name="foo"/>
     *     </declare-styleable>
     * </pre>
     * Calling `containsStyleableSymbol("s1_foo")` would return the parent [Symbol], but calling
     * `containsStyleableSymbol("foo")` or `containsSymbol(STYLEABLE, "foo")` would both return
     * `null`.
     */
    tailrec fun maybeGetStyleableParentSymbolForChild(canonicalName: String, start: Int = 0):
            Symbol.StyleableSymbol? {
        val index = canonicalName.indexOf('_', start)
        if (index < 0) return null

        val parentName = canonicalName.take(index)
        val parent = symbols.get(ResourceType.STYLEABLE, parentName) as? Symbol.StyleableSymbol
        if (parent != null) {
            val childName = canonicalName.drop(index + 1) // Drop the parent and the underscore.
            if (childName in parent.children) return parent
            // Styleable children of the format <parent>_android_<child> could have been either
            // declared as <item name="android_foo"/> or <item name="android:foo>.
            // If we didn't find the "android_" child, look for one in the "android:" namespace.
            if (childName.startsWith(ANDROID_ATTR_PREFIX)) {
                val childNameWithColon = SdkConstants.ANDROID_NS_NAME_PREFIX +
                        childName.drop(ANDROID_ATTR_PREFIX.length)
                if (childNameWithColon in parent.children) return parent
            }
        }
        return maybeGetStyleableParentSymbolForChild(canonicalName, index + 1)
    }

    /**
     * Creates a table with all the symbols taken from [this] table, but the values are from
     * [mainSymbolTable]
     */
    fun withValuesFrom(mainSymbolTable: SymbolTable): SymbolTable
            = mainSymbolTable.filter(this).rename(this.tablePackage)

    /** Returns the [ResourceType]s present in the table. */
    val resourceTypes: Set<ResourceType> get() = symbols.rowKeySet()

    /**
     * Builder that creates a symbol table. Use [FastBuilder], if possible, instead of this class.
     */
    class Builder {

        private var tablePackage = ""

        private val symbols: Table<ResourceType, String, Symbol> =
                Tables.newCustomTable(Maps.newEnumMap(ResourceType::class.java), ::HashMap)

        /**
         * Adds [symbol] to the table to be built. The table must not contain a [Symbol] with the
         * same resource type and name.
         */
        fun add(symbol: Symbol) = apply {
            require (!symbols.contains(symbol.resourceType, symbol.canonicalName)) {
                "Duplicate symbol in table with resource type '${symbol.resourceType}' and " +
                        "symbol name '${symbol.canonicalName}'"

            }
            symbols.put(symbol.resourceType, symbol.canonicalName, symbol)
        }

        /**
         * Adds all [Symbol]s in [symbols] to the table. This is semantically equivalent
         * to calling [add] for each element in [symbols].
         */
        fun addAll(symbols: Collection<Symbol>) = apply { symbols.forEach { add(it) } }

        /** Adds all [Symbol]s in [symbols] to the table, ignoring duplicates. */
        fun addAllIfNotExist(symbols: Collection<Symbol>) = apply {
            symbols.forEach { if (!contains(it)) add(it) }
        }

        /**
         * Adds [symbol] if it doesn't exist in the table yet. If [symbol] is already present,
         * chooses the correct resource accessibility.
         */
        private fun addFromPartialHelper(symbol: Symbol) {
            val existing = symbols.get(symbol.resourceType, symbol.canonicalName)
            if (existing == null) {
                // If this symbol hasn't been encountered yet, simply add it as is.
                symbols.put(symbol.resourceType, symbol.canonicalName, symbol)
                return
            }

            // If we already encountered it, check the qualifiers.
            // - if it's a styleable and visibilities don't conflict, merge them into one
            //   with the highest visibility of the two
            // - if they're the same, leave the existing one (the existing one overrode the
            //   new one)
            // - if the existing one is PRIVATE_XML_ONLY, use the new one (overriding
            //   resource was defined as PRIVATE or PUBLIC)
            // - if the new one is PRIVATE_XML_ONLY, leave the existing one (overridden
            //   resource was defined as PRIVATE or PUBLIC)
            // - if neither of them is PRIVATE_XML_ONLY, and they differ, that's an error
            if (existing.resourceVisibility != symbol.resourceVisibility
                && existing.resourceVisibility != ResourceVisibility.PRIVATE_XML_ONLY
                && symbol.resourceVisibility != ResourceVisibility.PRIVATE_XML_ONLY) {
                // Conflicting visibilities.
                throw IllegalResourceVisibilityException(
                    "Symbol with resource type ${symbol.resourceType} and name " +
                            "${symbol.canonicalName} defined both as " +
                            "${symbol.resourceVisibility} and ${existing.resourceVisibility}.")
            }
            if (symbol.resourceType != ResourceType.STYLEABLE) {
                if (symbol.resourceVisibility > existing.resourceVisibility) {
                    // We only need to replace the existing symbol with the new one if the
                    // visibilities differ and the new visibility is higher than the old one.
                    this.symbols.remove(existing.resourceType, existing.canonicalName)
                    this.symbols.put(symbol.resourceType, symbol.canonicalName, symbol)
                }
                return
            }
            // Otherwise these are Styleables.
            // Merge them, joining the children, sorting by name, and discarding dupes.
            val children =
                ImmutableList.copyOf((symbol.children + existing.children).distinct().sorted())

            val visibility =
                ResourceVisibility.max(symbol.resourceVisibility, existing.resourceVisibility)

            this.symbols.remove(existing.resourceType, existing.canonicalName)
            this.symbols.put(
                symbol.resourceType,
                symbol.canonicalName,
                Symbol.styleableSymbol(
                    symbol.canonicalName,
                    ImmutableList.of(),
                    children,
                    visibility))
        }

        /**
         * Adds all [Symbol]s from [table] if they don't exist in the table yet. If a [Symbol]
         * is already present, chooses the correct resource accessibility.
         */
        internal fun addFromPartial(table: SymbolTable) = apply {
            table.symbols.values().forEach { addFromPartialHelper(it) }
        }

        /** Sets the table package to be [tablePackage]. See [SymbolTable] description. */
        fun tablePackage(tablePackage: String) = apply {
            this.tablePackage = validate(tablePackage)
        }

        /**
         * Returns whether a [Symbol] with the same resource type and name as [symbol] has been
         * added.
         */
        operator fun contains(symbol: Symbol): Boolean =
            contains(symbol.resourceType, symbol.canonicalName)

        /**
         * Returns whether the table contains a [Symbol] with the [resourceType] and
         * [canonicalName].
         */
        fun contains(resourceType: ResourceType, canonicalName: String): Boolean =
            symbols.contains(resourceType, canonicalName)

        /** Returns the [Symbol] from the table matching the provided [symbol], or `null`. */
        operator fun get(symbol: Symbol): Symbol? =
            symbols.get(symbol.resourceType, symbol.canonicalName)

        fun remove(resourceType: ResourceType, canonicalName: String): Symbol? =
            symbols.remove(resourceType, canonicalName)

        fun isEmpty(): Boolean = symbols.isEmpty

        /** Builds and returns a [SymbolTable] with all [Symbol]s added. */
        fun build(): SymbolTable = SymbolTableImpl(tablePackage, ImmutableTable.copyOf(symbols))
    }

    /** A [Builder] that creates a [SymbolTable]. Use instead of [Builder], if possible. */
    class FastBuilder(private val symbolInterner: Interner<Symbol>) {

        private var tablePackage = ""

        private val symbols: ImmutableTable.Builder<ResourceType, String, Symbol> =
            ImmutableTable.builder()

        /**
         * Adds [symbol] to the table to be built. The table must not contain a [Symbol] with the
         * same resource type and name.
         */
        fun add(symbol: Symbol) {
            symbols.put(symbol.resourceType, symbol.canonicalName, symbolInterner.intern(symbol))
        }

        /**
         * Adds all [Symbol]s in [symbols] to the table. This is semantically equivalent
         * to calling [add] for each element of [symbols].
         */
        fun addAll(symbols: Collection<Symbol>) {
            symbols.forEach { this.add(it) }
        }

        /** Sets the table package to [tablePackage]. See [SymbolTable]. */
        fun tablePackage(tablePackage: String) {
            this.tablePackage = validate(tablePackage)
        }

        /** Builds a [SymbolTable] with all [Symbol]s added. */
        fun build(): SymbolTable = SymbolTableImpl(tablePackage, symbols.build())
    }

    companion object {
        val EMPTY: SymbolTable = builder().build()

        /**
         * Merges each [SymbolTable] in [tables] into a single [SymbolTable].
         *
         * The merge is order-sensitive: when multiple [Symbols] with the same class / name exist
         * in multiple [SymbolTables], the first one will be used. The package of the resultant
         * [SymbolTable] will be either that of the first element of [tables] or the default if
         * [tables] is empty.
         */
        @JvmStatic
        fun merge(tables: List<SymbolTable>): SymbolTable {
            if (tables.size == 1) return tables.first()  // Trivial merge.

            val builder = ImmutableTable.builder<ResourceType, String, Symbol>()
            // We only want to keep the first one we see.
            val seenNames = mutableSetOf<String>()

            // Use nested loops instead of a functional approach to minimize intermediate
            // allocations as this is a hotspot.
            for (resourceType in ResourceType.values()) {
                for (table in tables) {
                    for (symbol in table.symbols.row(resourceType).values) {
                        if (seenNames.add(symbol.canonicalName)) {
                            builder.put(resourceType, symbol.canonicalName, symbol)
                        }
                    }
                }
            }

            return SymbolTableImpl(tables.firstOrNull()?.tablePackage ?: "", builder.build())
        }

        /**
         * Merges all partial R [File]s in [tables], with package name [packageName].
         * See 'package-info.java' for a detailed description of the merging algorithm.
         */
        @JvmStatic
        fun mergePartialTables(tables: List<File>, packageName: String): SymbolTable {
            val symbolIo = SymbolIo()
            val builder = builder().tablePackage(packageName)

            // A set to keep the names of the visited layout files.
            val visitedLayoutFiles = HashSet<String>()

            // Use a reversed view of the file list, since we have to start from the 'highest'
            // source-set (base source-set will be last).
            tables.asReversed().filter {
                // When a layout file is overridden, its contents get overridden too. That is why we
                // need to keep the 'highest' version of the file. Partial R files for values XML
                // files and non-XML files need to be parsed always. The order matters for
                // declare-styleables and for resource accessibility.
                !it.name.startsWith("layout") || visitedLayoutFiles.add(it.name)
            }.forEach {
                try {
                    builder.addFromPartial(symbolIo.readFromPartialRFile(it, null))
                } catch (e: Exception) {
                    throw PartialRMergingException(
                        "An error occurred during merging of the partial R files", e)
                }
            }

            return builder.build()
        }

        /** Creates and returns a new [Builder] to create a [SymbolTable]. */
        @JvmStatic
        fun builder() = Builder()
    }

    class IllegalResourceVisibilityException(description: String) : Exception(description)
}

/** Returns [tablePackage] if valid or throws [IllegalArgumentException]. See [SymbolTable]. */
private fun validate(tablePackage: String): String {
    if (tablePackage.isEmpty() || SourceVersion.isName(tablePackage)) return tablePackage
    // The table package is invalid. This will always be the beginning of the message.
    val msg = "Package '$tablePackage' from AndroidManifest.xml is not a valid Java package name"
    for (segment in Splitter.on('.').split(tablePackage)) {
        require(SourceVersion.isIdentifier(segment)) {
            "$msg as '$segment' is not a valid Java identifier."
        }
        require(!SourceVersion.isKeyword(segment)) { "$msg as '$segment' is a Java keyword." }
    }
    // Shouldn't happen.
    throw IllegalArgumentException("$msg.")
}
