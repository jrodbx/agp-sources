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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.utils.setDisallowChanges
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.ide.common.symbols.Symbol
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Stopwatch
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheBuilderSpec
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.collect.ImmutableList
import com.google.common.collect.Interner
import com.google.common.collect.Interners
import java.io.File
import java.lang.ref.SoftReference
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.Objects
import javax.annotation.concurrent.ThreadSafe
import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Build service for tasks that android resource sybmols from the classpath in to memory.
 *
 * This maintains two separate caches, one of loaders, and one of actual file content.
 *
 * @param cacheBuilderSpec The configuration of the cache to use. Gradle calls the `@`[Inject]
 * annotated constructor which uses [SOFT_VALUES].
 */
@ThreadSafe
abstract class SymbolTableBuildService @VisibleForTesting internal constructor(cacheBuilderSpec: CacheBuilderSpec) :
    BuildService<BuildServiceParameters.None>, AutoCloseable {

    @Suppress("unused") // Called by Gradle
    @Inject
    constructor() : this(cacheBuilderSpec = SOFT_VALUES)

    fun loadClasspath(files: Iterable<File>): List<SymbolTable> {
        val stopwatch = Stopwatch.createStarted()
        logger.log(logLevel, "SymbolTableBuildService: loadClasspath started")
        getSymbolTablesCached(files).also { result ->
            logger.log(
                logLevel,
                "SymbolTableBuildService: loadClasspath took {} to return {} tables",
                stopwatch.elapsed(),
                result.size
            )
            return result
        }
    }

    protected val logger: Logger = Logging.getLogger(SymbolTableBuildService::class.java)

    private val logLevel: LogLevel =  LogLevel.DEBUG

    /**
     * We keep a soft reference to the strong interner used by symbol IO.
     *
     * This is to avoid having a very large number of soft references,
     * but still being able to drop symbols from the intern table under memory pressure.
     */
    private var symbolInternerReference: SoftReference<Interner<Symbol>>? = null

    private val symbolInterner: Interner<Symbol>
        @Synchronized
        get() {
            return symbolInternerReference?.get() ?: Interners.newStrongInterner<Symbol>().also {
                symbolInternerReference = SoftReference(it)
                // This will have happened if the original interner has been garbage collected.
                // When recreating it, intern the existing symbols to avoid additional memory use.
                for (symbolTable in symbolTableCache.asMap().values) {
                    for (symbol in symbolTable.symbols.values()) {
                        it.intern(symbol)
                    }
                }
            }
        }

    /** A key for symbol table lookup that uses the [BasicFileAttributes.fileKey] */
    private data class FileCacheKey(
        val file: Path,
        private val key: Any =
            Files.readAttributes(file, BasicFileAttributes::class.java).fileKey()
                ?: file.toUri().toString() // Fall back to using the file path as key.
    ) {
        // This uses a custom equals and hashcode implementation to avoid using the
        // Path object as part of the identity, but passing it in to allow symbols to be loaded.
        override fun equals(other: Any?): Boolean =
            other is FileCacheKey && this.key == other.key

        override fun hashCode(): Int = Objects.hash(key)
    }

    /** Cache of loaded files */
    private val symbolTableCache: LoadingCache<FileCacheKey, SymbolTable> =
        CacheBuilder.from(cacheBuilderSpec)
            .build(
                object : CacheLoader<FileCacheKey, SymbolTable>() {
                    override fun load(key: FileCacheKey): SymbolTable {
                        val result =
                            SymbolIo(symbolInterner).readSymbolListWithPackageName(key.file)
                        logger.log(
                            logLevel,
                            "SymbolTableBuildService: cache miss - loaded table '{}' from disk",
                            result.tablePackage
                        )
                        return result
                    }
                }
            )

    /**
     * Loads the given symbol tables using the cache.
     *
     * [SymbolTable]s will be returned from the in-memory cache if present, and only loaded from
     * disk if they are not already cached.
     * [Symbol] instances will be interned and shared across invocations.
     */
    private fun getSymbolTablesCached(files: Iterable<File>): List<SymbolTable> {
        return files.map { symbolTableCache.get(FileCacheKey(it.toPath())) }
    }

    @VisibleForTesting
    internal fun dropSymbolInterner() {
        symbolInternerReference?.clear()
    }

    @VisibleForTesting
    internal fun dropSymbolTables() {
        symbolTableCache.invalidateAll()
    }

    final override fun close() {
        dropSymbolInterner()
        dropSymbolTables()
    }

    class RegistrationAction(project: Project) :
        ServiceRegistrationAction<SymbolTableBuildService, BuildServiceParameters.None>(
            project,
            SymbolTableBuildService::class.java
        ) {
        override fun configure(parameters: BuildServiceParameters.None) {}
    }

    companion object {
        private val SOFT_VALUES = CacheBuilderSpec.parse("softValues")
    }
}
