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

package com.android.builder.internal.packaging

import com.android.signflinger.SignedApk
import com.android.signflinger.SignedApkOptions
import com.android.zipflinger.Sources
import com.android.zipflinger.StableArchive
import com.android.zipflinger.SynchronizedArchive
import com.android.zipflinger.Zip64
import com.google.common.base.Preconditions
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.InvalidPathException
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.zip.ZipFile

/**
 * Signs and compresses the bundle using the zipflinger library.
 */
class AabFlinger(
        outputFile: File,
        signerName: String,
        privateKey: PrivateKey,
        certificates: List<X509Certificate>,
        minSdkVersion: Int
): Closeable {

    /**
     * The archive file, which must be synchronized because we make async calls to it in the
     * writeFile method below, in order to do compression in parallel.
     */
    private val archive =
            SynchronizedArchive(
                    StableArchive(
                            SignedApk(
                                    outputFile,
                                    SignedApkOptions.Builder()
                                            .setName(signerName)
                                            .setPrivateKey(privateKey)
                                            .setCertificates(certificates)
                                            .setMinSdkVersion(minSdkVersion)
                                            .setV1Enabled(true)
                                            .setV2Enabled(false)
                                            .setV3Enabled(false)
                                            .setV4Enabled(false)
                                            .build(),
                                    Zip64.Policy.ALLOW
                            )
                    )
            )

    /** forkJoinPool used so that compression can occur in parallel */
    private val forkJoinPool = ForkJoinPool.commonPool()
    private val subTasks = mutableListOf<ForkJoinTask<Unit>>()
    private val openZipFiles = mutableListOf<ZipFile>()

    /**
     * Writes the content of a Jar/Zip archive to the receiver archive.
     *
     * @param zip the zip to copy data from
     * @throws IOException I/O error
     */
    @Throws(IOException::class)
    fun writeZip(zip: File, compressionLevel: Int) {
        Preconditions.checkArgument(zip.isFile, "!zip.isFile()")

        val zipFile = ZipFile(zip)
        openZipFiles.add(zipFile)
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) {
                continue
            }
            if (entry.name.contains("../")) {
                throw InvalidPathException(entry.name, "Entry name contains invalid characters")
            }
            subTasks.add(
                forkJoinPool.submit(Callable {
                    archive.add(
                        Sources.from(
                            // the input stream will be closed in StreamSource
                            zipFile.getInputStream(entry),
                            entry.name,
                            compressionLevel
                        )
                    )
                })
            )
        }
    }

    override fun close() {
        subTasks.forEach { it.join() }
        archive.close()
        openZipFiles.forEach(Closeable::close)
    }
}
