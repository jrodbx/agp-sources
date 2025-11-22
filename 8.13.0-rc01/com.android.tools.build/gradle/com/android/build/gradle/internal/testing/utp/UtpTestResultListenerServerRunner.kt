/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.testing.utp

import com.android.utils.FileUtils
import java.io.Closeable
import java.io.File
import java.io.IOException
import org.gradle.api.logging.Logging

/**
 * Runner of the [UtpTestResultListenerServer].
 *
 * This class generates a pair of private and public key for both gRPC client and server.
 * Those generated TLS certifications are used to be establish gRPC connection locally to
 * implement inter-process communication between UTP and AGP. Generated certs are deleted
 * after use by [close] method.
 *
 * @param listener a listener to receive test result events
 */
class UtpTestResultListenerServerRunner(
        listener: UtpTestResultListener?,
        startServerFunc: (File, File, File) -> UtpTestResultListenerServer? =
                { certChainFile, privateKeyFile, trustCertCollectionFile ->
                    UtpTestResultListenerServer.startServer(
                            certChainFile,
                            privateKeyFile,
                            trustCertCollectionFile,
                            listener)
                }
): Closeable {

    companion object {
        private val logger = Logging.getLogger(UtpTestResultListenerServerRunner::class.java)
    }

    private val serverCert: File
    private val serverPrivateKey: File

    private val clientCert: File
    private val clientPrivateKey: File

    private val server: UtpTestResultListenerServer

    val metadata: UtpTestResultListenerServerMetadata

    init {
        serverCert = createUtpTempFile("resultListenerServerCert", ".pem")
        serverPrivateKey = createUtpTempFile("resultListenerServer", ".key")
        clientCert = createUtpTempFile("resultListenerClientCert", ".pem")
        clientPrivateKey = createUtpTempFile("resultListenerClient", ".key")

        generateRsaKeyPair(serverCert, serverPrivateKey)
        generateRsaKeyPair(clientCert, clientPrivateKey)

        server = requireNotNull(startServerFunc(serverCert, serverPrivateKey, clientCert)) {
            "Unable to start the UTP test results listener gRPC server."
        }

        metadata = UtpTestResultListenerServerMetadata(
                serverCert, server.port, clientCert, clientPrivateKey)
    }

    override fun close() {
        server.close()
        try {
            FileUtils.deleteIfExists(serverCert)
            FileUtils.deleteIfExists(serverPrivateKey)
            FileUtils.deleteIfExists(clientCert)
            FileUtils.deleteIfExists(clientPrivateKey)
        } catch (e: IOException) {
            logger.warn("Failed to cleanup temporary directories: $e")
        }
    }
}

/**
 * Metadata about UTP test result listener server.
 */
data class UtpTestResultListenerServerMetadata(
        /**
         * A path to a TLS certificate file of the gRPC server in PEM format.
         */
        val serverCert: File,

        /**
         * A server port number.
         */
        val serverPort: Int,

        /**
         * A path to a TLS certificate file of the gRPC client in PEM format.
         */
        val clientCert: File,

        /**
         * A path to a private key of the gRPC client in PEM format.
         */
        val clientPrivateKey: File
)
