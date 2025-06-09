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
import com.android.build.gradle.internal.profile.ProfileAwareWorkAction
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
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import java.io.File
import java.io.IOException

abstract class Aapt2ProcessResourcesRunnable : ProfileAwareWorkAction<Aapt2ProcessResourcesRunnable.Params>() {

    override fun run() {
        val logger = Logging.getLogger(this::class.java)
        useAaptDaemon(parameters.aapt2ServiceKey.get()) { daemon ->
            processResources(
                aapt = daemon,
                aaptConfig = parameters.request.get(),
                rJar = null,
                logger = logger,
                errorFormatMode = parameters.errorFormatMode.get()
            )
        }
    }

    abstract class Params: ProfileAwareWorkAction.Parameters() {
        abstract val aapt2ServiceKey: Property<Aapt2DaemonServiceKey>
        abstract val request: Property<AaptPackageConfig>
        abstract val errorFormatMode: Property<SyncOptions.ErrorFormatMode>
    }
}

@Throws(IOException::class, ProcessException::class)
fun processResources(
    aapt: Aapt2,
    aaptConfig: AaptPackageConfig,
    rJar: File?,
    logger: Logger,
    errorFormatMode: SyncOptions.ErrorFormatMode,
    symbolTableLoader: (Iterable<File>) -> List<SymbolTable> = { SymbolIo().loadDependenciesSymbolTables(it) }
) {

    try {
        aapt.link(aaptConfig, LoggerWrapper(logger))
    } catch (e: Aapt2Exception) {
        throw rewriteLinkException(
                e,
                errorFormatMode,
                aaptConfig.mergeBlameDirectory,
                aaptConfig.manifestMergeBlameFile,
                aaptConfig.identifiedSourceSetMap,
                logger
        )
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
            SymbolTable.builder().tablePackage(mainPackageName).build()

        // For each dependency, load its symbol file.
        var depSymbolTables: List<SymbolTable> = symbolTableLoader.invoke(
            aaptConfig.librarySymbolTableFiles
        )

        val finalIds = aaptConfig.useFinalIds
        if (rJar != null) { // non-namespaced case
            val localSymbolsFile = aaptConfig.localSymbolTableFile
            val nonTransitiveRClass = localSymbolsFile != null

            // If we're generating a non-transitive R class for the current module, we need to read
            // the local symbols file and add it to the dependencies symbol tables. It doesn't have
            // the correct IDs, so we skip the values - they will be resolved in the next line from
            // the R.txt.
            if (nonTransitiveRClass) {
                val localSymbols =
                    SymbolIo.readRDef(localSymbolsFile!!.toPath()).rename(mainSymbols.tablePackage)
                depSymbolTables = depSymbolTables.plus(localSymbols)
            }

            // Replace the default values from the dependency table with the allocated values from
            // the main table.
            depSymbolTables = depSymbolTables.map { t -> t.withValuesFrom(mainSymbols) }

            // If our local R class is transitive, add the table of *all* symbols to generate.
            if (!nonTransitiveRClass)
                depSymbolTables = depSymbolTables.plus(mainSymbols)

            exportToCompiledJava(
                depSymbolTables,
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
