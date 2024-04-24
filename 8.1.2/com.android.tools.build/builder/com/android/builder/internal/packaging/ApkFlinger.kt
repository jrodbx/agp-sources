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

package com.android.builder.internal.packaging

import com.android.SdkConstants
import com.android.signflinger.SignedApk
import com.android.signflinger.SignedApkOptions
import com.android.tools.build.apkzlib.sign.SigningOptions
import com.android.tools.build.apkzlib.zfile.ApkCreator
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode
import com.android.zipflinger.Entry
import com.android.zipflinger.Source.NO_ALIGNMENT
import com.android.zipflinger.Sources
import com.android.zipflinger.StableArchive
import com.android.zipflinger.SynchronizedArchive
import com.android.zipflinger.Zip64
import com.android.zipflinger.ZipArchive
import com.android.zipflinger.ZipSource
import com.android.zipflinger.ZipSource.COMPRESSION_NO_CHANGE
import com.google.common.base.Function
import com.google.common.base.Preconditions
import com.google.common.base.Predicate
import java.io.File
import java.io.IOException
import java.nio.file.InvalidPathException
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.zip.Deflater.NO_COMPRESSION

// TODO ensure that all input zip entries have desired compression -
//  https://issuetracker.google.com/135275558

/** An implementation of [ApkCreator] using the zipflinger library
 *
 * @param deterministicEntryOrder whether or not the order of the entries in the created APK will be
 * deterministic, regardless of any multi-threading in the implementation and/or regardless of the
 * order of calls to [writeZip], [writeFile], and [deleteFile]. Importantly, deterministicEntryOrder
 * does not guarantee that the resulting APK will be deterministic for a given set of source files;
 * that is true for clean builds, but not incremental builds. Instead, if deterministicEntryOrder is
 * true, the resulting APK will be deterministic given the same initial APK and the same calls to
 * [writeZip], [writeFile], and [deleteFile].
 */
class ApkFlinger(
    creationData: ApkCreatorFactory.CreationData,
    private val compressionLevel: Int,
    deterministicEntryOrder: Boolean = true,
    enableV3Signing: Boolean = false,
    enableV4Signing: Boolean = false
) : ApkCreator {

    /**
     * The archive file, which must be synchronized because we make async calls to it in the
     * writeFile method below, in order to do compression in parallel.
     * */
    private val archive: SynchronizedArchive

    /** Predicate defining which files should not be compressed.  */
    private val noCompressPredicate: Predicate<String>

    /** Predicate defining which files should be page aligned.  */
    private val pageAlignPredicate: Predicate<String>

    /** forkJoinPool used so that compression can occur in parallel */
    private val forkJoinPool = ForkJoinPool.commonPool()
    private val subTasks = mutableListOf<ForkJoinTask<Unit>>()

    init {
        when (creationData.nativeLibrariesPackagingMode) {
            NativeLibrariesPackagingMode.COMPRESSED -> {
                noCompressPredicate = creationData.noCompressPredicate
                pageAlignPredicate = Predicate { false }
            }
            NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED -> {
                val baseNoCompressPredicate = creationData.noCompressPredicate
                noCompressPredicate = Predicate { name ->
                    baseNoCompressPredicate.apply(name)
                            || name?.endsWith(SdkConstants.DOT_NATIVE_LIBS) ?: false
                }
                pageAlignPredicate =
                    Predicate { it?.endsWith(SdkConstants.DOT_NATIVE_LIBS) ?: false }
            }
            else -> throw AssertionError()
        }
        val signingOptions: SigningOptions? = creationData.signingOptions.orNull()
        val innerArchive =
            if (signingOptions == null) {
                ZipArchive(creationData.apkPath.toPath(), Zip64.Policy.FORBID)
            } else {
                SignedApk(
                    creationData.apkPath,
                    SignedApkOptions.Builder()
                        .setCertificates(signingOptions.certificates)
                        .setMinSdkVersion(signingOptions.minSdkVersion)
                        .setPrivateKey(signingOptions.key)
                        .setSdkDependencies(signingOptions.sdkDependencyData)
                        .setV1Enabled(signingOptions.isV1SigningEnabled)
                        .setV2Enabled(signingOptions.isV2SigningEnabled)
                        .setV3Enabled(enableV3Signing)
                        .setV4Enabled(enableV4Signing)
                        .setV1CreatedBy(creationData.createdBy ?: DEFAULT_CREATED_BY)
                        .setV1TrustManifest(creationData.isIncremental)
                        .also { builder ->
                            signingOptions.executor?.let { builder.setExecutor(it) }
                            if (enableV4Signing) {
                                builder.setV4Output(
                                    File("${creationData.apkPath.absolutePath}.idsig")
                                )
                            }
                        }
                        .build()
                )
            }
        archive =
            SynchronizedArchive(
                if (deterministicEntryOrder) StableArchive(innerArchive) else innerArchive
            )
    }

    /**
     * Copies the content of a Jar/Zip archive into the receiver archive.
     *
     * <p>An optional predicate allows to selectively choose which files to copy over and an
     * optional function allows renaming the files as they are copied.
     *
     * <p>If any entries in zip already exist in this archive, they must be deleted with
     * [deleteFile] before calling this method.
     *
     * <p>After calling this method, any calls at all to [deleteFile] will result in an error.
     *
     * @param zip the zip to copy data from
     * @param transform an optional transform to apply to file names before copying them
     * @param isIgnored an optional filter or {@code null} to mark which out files should not be
     *     added, even through they are on the zip; if {@code transform} is specified, then this
     *     predicate applies after transformation
     * @throws IOException I/O error
     */
    @Throws(IOException::class)
    override fun writeZip(
        zip: File, transform: Function<String, String>?, isIgnored: Predicate<String>?
    ) {
        Preconditions.checkArgument(zip.isFile, "!zip.isFile()")

        val ignorePredicate : Predicate<String> = isIgnored ?: Predicate { false }

        val zipSource = ZipSource(zip.toPath())
        val entries = zipSource.entries().values
        for (entry in entries) {
            if (entry.isDirectory || ignorePredicate.apply(entry.name)) {
                continue
            }
            // newName is the entry's name in the destination archive.
            val newName = transform?.apply(entry.name) ?: entry.name
            if (newName.contains("../")) {
                throw InvalidPathException(newName, "Entry name contains invalid characters")
            }
            // newCompressionLevel is the entry's compression level in the destination archive, or
            //  COMPRESSION_NO_CHANGE if it's the same compression level as the source entry.
            val newCompressionLevel =
                if (entry.isCompressed && noCompressPredicate.apply(newName)) {
                    NO_COMPRESSION
                } else {
                    COMPRESSION_NO_CHANGE
                }
            // newAlignment is the entry's alignment in the destination archive.
            val newAlignment =
                getNewAlignment(entry, newName, newCompressionLevel, pageAlignPredicate)
            zipSource.select(entry.name, newName, newCompressionLevel, newAlignment)
        }
        archive.add(zipSource)
    }

    /**
     * Writes a new [File] into the archive.
     *
     * <p>If this file entry already exists in this archive, it must be deleted with [deleteFile]
     * before calling this method.
     *
     * <p>After calling this method, any calls at all to [deleteFile] will result in an error.
     *
     * @param inputFile the {@link File} to write.
     * @param apkPath the filepath inside the archive.
     * @throws IOException I/O error
     */
    @Throws(IOException::class)
    override fun writeFile(inputFile: File, apkPath: String) {
        subTasks.add(
            forkJoinPool.submit(
                Callable<Unit> {
                    val mayCompress = !noCompressPredicate.apply(apkPath)
                    val source = Sources.from(inputFile, apkPath, if (mayCompress) compressionLevel else NO_COMPRESSION)
                    if (!mayCompress) {
                        if (pageAlignPredicate.apply(apkPath)) {
                            source.align(PAGE_ALIGNMENT)
                        } else {
                            // by default all uncompressed entries are aligned at 4 byte boundaries.
                            source.align(DEFAULT_ALIGNMENT)
                        }
                    }
                    archive.add(source)
                }
            )
        )
    }

    /**
     * Deletes the entry with the given apkPath from the [ZipArchive].
     *
     * <p>If this method is called after any writeZip() or writeFile() calls, an error will be
     * thrown.
     *
     * @param apkPath the path to remove
     * @throws IOException failed to remove the entry
     */
    @Throws(IOException::class)
    override fun deleteFile(apkPath: String) {
        archive.delete(apkPath)
    }

    // This is never called. We can delete this method once we migrate away from implementing
    // apkzlib's ApkCreator
    @Throws(IOException::class)
    override fun hasPendingChangesWithWait(): Boolean {
        throw RuntimeException("not implemented")
    }

    @Throws(IOException::class)
    override fun close() {
        subTasks.forEach { it.join() }
        archive.close()
    }

    companion object {

        /**
         * Calculate an [entry]'s alignment in the destination archive.
         *
         * @param entry the entry from the source archive
         * @param newName the new name of the entry in the destination archive
         * @param newCompressionLevel the compression level of the entry in the destination archive
         * @param pageAlignPredicate the predicate defining which files should be page aligned
         */
        private fun getNewAlignment(
            entry: Entry,
            newName: String,
            newCompressionLevel: Int,
            pageAlignPredicate: Predicate<String>
        ): Long {
            val isNewEntryCompressed =
                when (newCompressionLevel) {
                    COMPRESSION_NO_CHANGE -> entry.isCompressed
                    NO_COMPRESSION -> false
                    else -> true
                }
            // No alignment needed for compressed entries
            if (isNewEntryCompressed) {
                return NO_ALIGNMENT
            }
            // For uncompressed entries, use PAGE_ALIGNMENT when required, otherwise use
            // DEFAULT_ALIGNMENT
            return if (pageAlignPredicate.apply(newName)) {
                PAGE_ALIGNMENT
            } else {
                DEFAULT_ALIGNMENT
            }
        }
    }
}

private const val DEFAULT_ALIGNMENT = 4L
private const val PAGE_ALIGNMENT = 4096L

private const val DEFAULT_CREATED_BY = "Generated-by-ADT"
