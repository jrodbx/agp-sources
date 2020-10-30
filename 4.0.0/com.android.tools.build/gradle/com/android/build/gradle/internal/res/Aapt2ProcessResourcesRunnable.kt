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

package com.android.build.gradle.internal.res

import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.build.gradle.internal.services.useAaptDaemon
import com.android.build.gradle.options.SyncOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2
import com.android.builder.internal.aapt.v2.Aapt2Exception
import com.android.builder.internal.aapt.v2.Aapt2InternalException
import com.android.builder.symbols.exportToCompiledJava
import com.android.ide.common.process.ProcessException
import com.android.ide.common.symbols.RGeneration
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.ide.common.symbols.getPackageNameFromManifest
import com.android.ide.common.symbols.loadDependenciesSymbolTables
import com.android.utils.ILogger
import com.google.common.collect.Iterables
import org.gradle.api.logging.Logging
import java.io.File
import java.io.IOException
import java.io.Serializable
import javax.inject.Inject

class Aapt2ProcessResourcesRunnable @Inject constructor(
        private val params: Params) : Runnable {

    override fun run() {
        val logger = Logging.getLogger(this::class.java)
        useAaptDaemon(params.aapt2ServiceKey) { daemon ->
            try {
                processResources(daemon, params.request, null, LoggerWrapper(logger))
            } catch (exception: Aapt2Exception) {
                throw rewriteLinkException(
                    exception,
                    params.errorFormatMode,
                    params.mergeBlameFolder,
                    params.manifestMergeBlameFile,
                    logger
                )
            }
        }
    }

    class Params(
        val aapt2ServiceKey: Aapt2DaemonServiceKey,
        val request: AaptPackageConfig,
        val errorFormatMode: SyncOptions.ErrorFormatMode,
        val mergeBlameFolder: File?,
        val manifestMergeBlameFile: File?
    ) : Serializable
}

@Throws(IOException::class, ProcessException::class)
fun processResources(
    aapt: Aapt2,
    aaptConfig: AaptPackageConfig,
    rJar: File?,
    logger: ILogger
) {

    try {
        aapt.link(aaptConfig, logger)
    } catch (e: Aapt2Exception) {
        throw e
    } catch (e: Aapt2InternalException) {
        throw e
    } catch (e: Exception) {
        throw ProcessException("Failed to execute aapt", e)
    }

    val sourceOut = aaptConfig.sourceOutputDir
    if (sourceOut != null || rJar != null) {
        // Figure out what the main symbol file's package is.
        var mainPackageName = aaptConfig.customPackageForR
        if (mainPackageName == null) {
            mainPackageName = getPackageNameFromManifest(aaptConfig.manifestFile)
        }

        // Load the main symbol file.
        val mainRTxt = File(aaptConfig.symbolOutputDir, "R.txt")
        val mainSymbols = if (mainRTxt.isFile)
            SymbolIo.readFromAapt(mainRTxt, mainPackageName)
        else
            SymbolTable.builder().tablePackage(mainPackageName!!).build()

        // For each dependency, load its symbol file.
        var depSymbolTables: Set<SymbolTable> = loadDependenciesSymbolTables(
            aaptConfig.librarySymbolTableFiles
        )

        val finalIds = aaptConfig.useFinalIds
        if (rJar != null) { // non-namespaced case
            // replace the default values from the dependency table with the allocated values
            // from the main table
            depSymbolTables = depSymbolTables.asSequence().map { t -> t.withValuesFrom(mainSymbols) }.toSet()
            exportToCompiledJava(
                Iterables.concat(setOf(mainSymbols), depSymbolTables),
                rJar.toPath(),
                finalIds
            )
        } else { // namespaced case, TODO: use exportToCompiledJava instead b/130110629
            RGeneration.generateRForLibraries(
                mainSymbols, depSymbolTables, sourceOut!!, finalIds
            )
        }
    }
}
