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

import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.RecordTestResultEventResponse
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerProto.TestResultEvent
import com.android.tools.utp.plugins.result.listener.gradle.proto.GradleAndroidTestResultListenerServiceGrpc.GradleAndroidTestResultListenerServiceImplBase
import com.google.common.annotations.VisibleForTesting
import com.google.protobuf.Any
import com.google.testing.platform.proto.api.core.TestStatusProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.google.testing.platform.proto.api.core.TestSuiteResultProto.TestSuiteResult
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContextBuilder
import org.gradle.api.logging.Logging
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * A GRPC server to receive test progress and results in realtime from UTP.
 *
 * @param port a port number to start and bind the gRPC server
 * @param listener a listener to receive test result events
 */
class UtpTestResultListenerServer private constructor(
        val port: Int,
        listener: UtpTestResultListener?,
        serverFactory: (Int) -> ServerBuilder<*>) : Closeable {
    companion object {
        private val logger = Logging.getLogger(UtpTestResultListenerServer::class.java)

        private const val DEFAULT_GRPC_SERVER_PORT: Int = 9624
        private const val DEFAULT_MAX_RETRY_ATTEMPT: Int = 10

        /**
         * Starts the gRPC server. A given [defaultPort] number is used for binding the
         * server. If the [defaultPort] is unavailable, it retries by incrementing the
         * port number by 1 until it reaches to the [maxRetryAttempt].
         */
        fun startServer(
                certChainFile: File,
                privateKeyFile: File,
                trustCertCollectionFile: File,
                listener: UtpTestResultListener?,
                defaultPort: Int = DEFAULT_GRPC_SERVER_PORT,
                maxRetryAttempt: Int = DEFAULT_MAX_RETRY_ATTEMPT,
                serverFactory: (Int) -> ServerBuilder<*> = { port ->
                    createServerBuilder(certChainFile, privateKeyFile, trustCertCollectionFile, port)
                }
        ): UtpTestResultListenerServer? {
            for (attempt in 0 until maxRetryAttempt) {
                val port = defaultPort + attempt
                try {
                    return UtpTestResultListenerServer(port, listener, serverFactory)
                } catch (exception: IOException) {
                    logger.info("Failed to bind and start the gRPC server." +
                            " Retrying with a different port number.")
                }
            }
            logger.error("Unable to start the gRPC server.")
            return null
        }

        private fun createServerBuilder(
                certChainFile: File,
                privateKeyFile: File,
                trustCertCollectionFile: File,
                port: Int): ServerBuilder<*> {
            val sslContext = SslContextBuilder.forServer(certChainFile, privateKeyFile).apply {
                trustManager(trustCertCollectionFile)
                clientAuth(ClientAuth.REQUIRE)
                GrpcSslContexts.configure(this)
            }.build()
            return NettyServerBuilder.forPort(port).sslContext(sslContext)
        }
    }

    @VisibleForTesting
    val server: Server = serverFactory(port)
            .addService(GradleAndroidTestResultListenerService(listener))
            .build()
            .start()

    override fun close() {
        server.shutdownNow().awaitTermination()
    }
}

private class GradleAndroidTestResultListenerService(val listener: UtpTestResultListener?)
    : GradleAndroidTestResultListenerServiceImplBase() {
    companion object {
        private val logger = Logging.getLogger(GradleAndroidTestResultListenerService::class.java)
    }
    override fun recordTestResultEvent(
            responseObserver: StreamObserver<RecordTestResultEventResponse>): StreamObserver<TestResultEvent> {
        return object: StreamObserver<TestResultEvent> {
            /**
             * A device ID that this stream observer receives test results from.
             * This ID is initialized when it receives a test result event.
             */
            var targetDeviceId: String = ""

            override fun onNext(testResultEvent: TestResultEvent) {
                targetDeviceId = testResultEvent.deviceId
                listener?.onTestResultEvent(testResultEvent)
            }

            override fun onError(error: Throwable) {
                logger.error("Could not receive test results from the test executor.", error)

                val grpcErrorEvent = TestResultEvent.newBuilder().apply {
                    deviceId = targetDeviceId
                    testSuiteFinishedBuilder.apply {
                        testSuiteResult = Any.pack(TestSuiteResult.newBuilder().apply {
                            testStatus = TestStatusProto.TestStatus.ERROR
                        }.build())
                    }
                }.build()

                listener?.onTestResultEvent(grpcErrorEvent)
            }

            override fun onCompleted() {
                responseObserver.onNext(RecordTestResultEventResponse.getDefaultInstance())
                responseObserver.onCompleted()
            }
        }
    }
}
