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

package com.android.build.gradle.internal.res.namespaced

import com.android.SdkConstants
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.res.Aapt2CompileRunnable
import com.android.build.gradle.internal.services.Aapt2DaemonServiceKey
import com.android.builder.core.VariantTypeImpl
import com.android.builder.internal.aapt.AaptOptions
import com.android.builder.internal.aapt.AaptPackageConfig
import com.android.builder.internal.aapt.v2.Aapt2RenamingConventions
import com.android.ide.common.resources.CompileResourceRequest
import com.android.ide.common.symbols.SymbolIo
import com.android.ide.common.symbols.SymbolTable
import com.android.io.nonClosing
import com.android.utils.PathUtils
import com.google.common.collect.ImmutableList
import com.google.common.io.ByteStreams
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.InputArtifactDependencies
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Artifact transform with dependencies to auto-namespace AARs.
 *
 * At a high level this means resolving all resource references that are not namespaced, and
 * rewriting them to be namespaced. See [NamespaceRewriter].
 */
@CacheableTransform
abstract class AutoNamespaceTransform : TransformAction<AutoNamespaceParameters> {

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val preProcessedAarDir: Provider<FileSystemLocation>

    @get:InputArtifactDependencies
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val preprocessedDependencies: FileCollection

    override fun transform(outputs: TransformOutputs) {
        try {
            val output = outputs.file(getOutputFileName())
            val preprocessedAar: File = preProcessedAarDir.get().asFile
                .resolve(AutoNamespacePreProcessTransform.PREPROCESSED_AAR_FILE_NAME)
            ZipFile(preprocessedAar).use { inputAar ->
                ZipOutputStream(output.outputStream().buffered()).use { outputAar ->
                    val resApk = inputAar.getEntry(SdkConstants.FN_RESOURCE_STATIC_LIBRARY)
                    if (resApk != null) {
                        // Already namespaced!
                        copyNamespacedAar(inputAar, outputAar)
                    } else {
                        val tempDir = Files.createTempDirectory("auto_namespace")
                        try {
                            autoNamespaceAar(
                                inputAar,
                                outputAar,
                                preprocessedDependencies.files,
                                tempDir
                            )
                        } finally {
                            PathUtils.deleteRecursivelyIfExists(tempDir)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            throw IOException("Failed to auto-namespace ${preProcessedAarDir.get().asFile.name}", e)
        }
    }

    private fun copyNamespacedAar(
        inputAar: ZipFile,
        outputAar: ZipOutputStream
    ) {
        for (entry in inputAar.entries()) {
            when {
                entry.name == SdkConstants.FN_R_DEF_TXT -> {
                    // Only used for auto-namespacing other libraries on top of this one,
                    // not part of the actual AAR format.
                }
                else -> {
                    outputAar.putNextEntry(ZipEntry(entry.name))
                    ByteStreams.copy(inputAar.getInputStream(entry), outputAar)
                }
            }
        }
    }

    private fun getOutputFileName() =
        preProcessedAarDir.get().asFile.name + ".aar"

    private fun autoNamespaceAar(
        inputAar: ZipFile,
        outputAar: ZipOutputStream,
        dependencies: Set<File>,
        tempDir: Path
    ) {
        val resToCompileDir = tempDir.resolve("res_to_compile")
            .also { Files.createDirectory(it) }
        val manifestFile = tempDir.resolve("AndroidManifest.xml")
        val compiledResDir = tempDir.resolve("compiled_res")
            .also { Files.createDirectory(it) }
        val aaptIntermediateDir = tempDir.resolve("aapt_intermediate")
            .also { Files.createDirectory(it) }
        val partialRDir = tempDir.resolve("partialR")
            .also { Files.createDirectory(it) }
        val staticLibApk = tempDir.resolve("staticLib.apk")
        val generatedPublicXmlDir = tempDir.resolve("generated_public_xml")
            .also { Files.createDirectory(it) }
        val compiledPublicXmlDir = tempDir.resolve("compiled_public_xml")
            .also { Files.createDirectory(it) }
        val requestList = ArrayList<CompileResourceRequest>()
        val aapt2ServiceKey: Aapt2DaemonServiceKey =
            parameters.aapt2DaemonBuildService.get().registerAaptService(
                parameters.aapt2FromMaven.singleFile,
                LoggerWrapper.getLogger(this::class.java)
            )

        // Read the symbol tables from this AAR and the dependencies to enable the namespaced
        // rewriter to resolve symbols.
        val localTableEntry = inputAar.getEntry(SdkConstants.FN_R_DEF_TXT)
        val localTable = SymbolIo.readRDefFromInputStream(
            preProcessedAarDir.get().asFile.toString(),
            inputAar.getInputStream(localTableEntry)
        )

        val symbolTables = ImmutableList.builder<SymbolTable>().apply {
            add(localTable)
            for (dependency in dependencies) {
                add(
                    SymbolIo.readRDefFromZip(
                        dependency.toPath().resolve(
                            AutoNamespacePreProcessTransform.PREPROCESSED_AAR_FILE_NAME
                        )
                    )
                )
            }
        }.build()

        val rewriter =
            NamespaceRewriter(symbolTables, Logging.getLogger(AutoNamespaceTransform::class.java))

        for (entry in inputAar.entries()) {
            when {
                entry.name == SdkConstants.FN_R_DEF_TXT -> {
                    // Only used for auto-namespacing other libraries on top of this one,
                    // not part of the actual AAR format.
                }
                entry.name == SdkConstants.FN_RESOURCE_TEXT -> {
                    // Regenerated below to be namespaced, as the compilation R class is
                    // generated from this
                }
                isJar(entry.name) -> {
                    outputAar.putNextEntry(ZipEntry(entry.name))
                    rewriter.rewriteJar(inputAar.getInputStream(entry), outputAar)
                }
                entry.name == SdkConstants.FN_ANDROID_MANIFEST_XML -> {
                    // TODO: separate manifest XML.
                    outputAar.putNextEntry(ZipEntry(entry.name))
                    // Write to aar and to file for AAPT2.
                    Files.newOutputStream(manifestFile).buffered().use { file ->
                        rewriter.rewriteManifest(
                            inputAar.getInputStream(entry),
                            TeeOutputStream(file, outputAar),
                            preProcessedAarDir
                        )
                    }
                }
                entry.name.startsWith("res/") -> {
                    outputAar.putNextEntry(ZipEntry(entry.name))
                    val outFile = resToCompileDir.resolve(entry.name)
                    Files.createDirectories(outFile.parent)
                    Files.newOutputStream(outFile).buffered().use { outputStream ->
                        rewriter.rewriteAarResource(
                            entry.name,
                            inputAar.getInputStream(entry),
                            outputStream
                        )
                    }
                    val partialRFile = partialRDir.resolve(
                        "${Aapt2RenamingConventions.compilationRename(outFile.toFile())}-R.txt"
                    ).toFile()
                    // TODO: Compilation using new java implementation once done?
                    // TODO(146340124): Pseudolocalization?
                    requestList.add(
                        CompileResourceRequest(
                            inputFile = outFile.toFile(),
                            outputDirectory = compiledResDir.toFile(),
                            partialRFile = partialRFile
                        )
                    )
                }
                else -> {
                    outputAar.putNextEntry(ZipEntry(entry.name))
                    ByteStreams.copy(inputAar.getInputStream(entry), outputAar)
                }
            }
        }

        val publicTxtInputStream: InputStream? =
            inputAar.getEntry(SdkConstants.FN_PUBLIC_TXT)?.let { inputAar.getInputStream(it) }
        val publicXml = rewriter.generatePublicFile(publicTxtInputStream, generatedPublicXmlDir)
        requestList.add(
            CompileResourceRequest(
                inputFile = publicXml.toFile(),
                outputDirectory = compiledPublicXmlDir.toFile()
            )
        )

        // TODO: Performance: This is single threaded (but multiple AARs could be being
        //       auto-namespaced in parallel), investigate whether it can be improved.
        Aapt2CompileRunnable(
            Aapt2CompileRunnable.Params(
                aapt2ServiceKey,
                requestList,
                parameters.errorFormatMode.get()
            )
        ).run()

        linkAndroidResources(
            manifestFile,
            compiledResDir,
            compiledPublicXmlDir,
            staticLibApk,
            aaptIntermediateDir,
            aapt2ServiceKey,
            outputAar
        )

        generateRTxt(requestList, localTable, outputAar)
    }

    private fun linkAndroidResources(
        manifestFile: Path,
        compiledResDir: Path,
        compiledPublicXmlDir: Path,
        staticLibApk: Path,
        aaptIntermediateDir: Path,
        aapt2ServiceKey: Aapt2DaemonServiceKey,
        outputAar: ZipOutputStream
    ) {
        if (!Files.isRegularFile(manifestFile)) {
            throw IOException("manifest file $manifestFile does not exist")
        }

        val request = AaptPackageConfig(
            androidJarPath = null,
            manifestFile = manifestFile.toFile(),
            options = AaptOptions(),
            resourceDirs = ImmutableList.of(
                compiledResDir.toFile(),
                preProcessedAarDir.get().asFile.resolve(AutoNamespacePreProcessTransform.PRECOMPILED_RES_DIR_NAME),
                compiledPublicXmlDir.toFile()
            ),
            staticLibrary = true,
            resourceOutputApk = staticLibApk.toFile(),
            variantType = VariantTypeImpl.LIBRARY,
            mergeOnly = true,
            intermediateDir = aaptIntermediateDir.toFile()
        )

        Aapt2LinkRunnable(
            Aapt2LinkRunnable.Params(
                aapt2ServiceKey,
                request,
                parameters.errorFormatMode.get()
            )
        ).run()

        outputAar.putNextEntry(ZipEntry(SdkConstants.FN_RESOURCE_STATIC_LIBRARY))
        Files.copy(staticLibApk, outputAar)
    }

    private fun generateRTxt(
        requestList: ArrayList<CompileResourceRequest>,
        localTable: SymbolTable,
        outputAar: ZipOutputStream
    ) {
        val partialRFiles =
            preProcessedAarDir.get().asFile.resolve(AutoNamespacePreProcessTransform.PRECOMPILED_RES_PARTIAL_R_DIR_NAME)
                .listFiles()?.toMutableList() ?: mutableListOf<File>()
        requestList.mapNotNullTo(partialRFiles) { it.partialRFile }

        val resources = SymbolTable.mergePartialTables(partialRFiles, localTable.tablePackage)

        // This is deliberately a non transitive R.txt file.
        // AARs generated from namespaced projects will have non-transitive R.txt, so the
        // auto-namespacing pipeline simulates that.
        // This has the nice property of enabling us to generate the compilation R class in the
        // same way in the namespaced and the non-namespaced pipeline.
        outputAar.putNextEntry(ZipEntry(SdkConstants.FN_RESOURCE_TEXT))
        outputAar.nonClosing().bufferedWriter().use { writer ->
            SymbolIo.writeForAar(resources, writer)
        }
    }

    companion object {
        fun isJar(entryName: String): Boolean =
            entryName == SdkConstants.FN_CLASSES_JAR ||
                    entryName == SdkConstants.FN_API_JAR ||
                    (entryName.startsWith("libs/") && entryName.endsWith(SdkConstants.DOT_JAR))

    }
}