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

package com.android.build.gradle.external.cmake.server;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * Interface that defines a Cmake server. Server interface is based off of
 * https://cmake.org/cmake/help/v3.7/manual/cmake-server.7.html
 */
public interface Server {
    /**
     * Starts and connects to a Cmake server and sends a HelloRequest.
     *
     * @return true if connected successfully to Cmake server
     * @throws IOException I/O failure
     */
    boolean connect() throws IOException;

    /**
     * Disconnected from the Cmake server.
     *
     * @throws IOException I/O failure
     */
    void disconnect() throws IOException;

    /**
     * Returns the connection status to the Cmake server
     *
     * @return true if connected Cmake server
     */
    boolean isConnected();

    /**
     * Cmake server returns a list of supported versions when a connection is established (via the
     * HelloResult). This function returns the supported version to be sent for handshake.
     *
     * @return list of protocol versions supported by the server. The list is empty if the server
     *     does not support the Cmake server.
     */
    @Nullable
    List<ProtocolVersion> getSupportedVersion();

    /**
     * One of the first request a client may send to the Cmake server.
     *
     * @param handshakeRequest - a valid handshake request
     * @return HandshakeResult
     * @throws IOException I/O failure
     */
    @NonNull
    HandshakeResult handshake(@NonNull HandshakeRequest handshakeRequest) throws IOException;

    /**
     * Configures our project for build.
     *
     * @param cacheArguments list of strings to configure via the cache argument keys. These string
     *     are interpreted similar to cmake command line client.
     * @return ConfigureCommandResult - this object holds the ConfigureResult and interactive
     *     messages returned by Cmake when configuring the project.
     * @throws IOException I/O failure
     */
    @NonNull
    ConfigureCommandResult configure(@NonNull String... cacheArguments) throws IOException;

    /**
     * Computes, i.e., generates the build system files in the build directory. Ideally, this
     * function should be called only if configure succeeds.
     *
     * @return ComputeResult
     * @throws IOException I/O failure
     */
    @NonNull
    ComputeResult compute() throws IOException;

    /**
     * Requests the project's code model once its configured successfully.
     *
     * @return CodeModel
     * @throws IOException I/O failure
     */
    @NonNull
    CodeModel codemodel() throws IOException;

    /**
     * Lists the cached configuration values after the project is configured.
     *
     * @return CacheResult
     * @throws IOException I/O failure
     */
    @NonNull
    CacheResult cache() throws IOException;

    /**
     * Requests files used by CMake as part of the build system itself.
     *
     * @return CmakeInputsResult
     * @throws IOException I/O failure
     */
    @NonNull
    CmakeInputsResult cmakeInputs() throws IOException;

    /**
     * Request to get the state of Cmake (after a successful handshake).
     *
     * @return global settings of Cmake's state
     * @throws IOException I/O failure
     */
    @NonNull
    GlobalSettings globalSettings() throws IOException;

    /**
     * Returns the compiler executable used C files.
     *
     * @return full path to the compiler and compiler executable. If compiler executable is not
     *     found this function returns null.
     */
    @NonNull
    String getCCompilerExecutable();

    /**
     * Returns the compiler executable used Cpp files.
     *
     * @return full path to the compiler and compiler executable. If compiler executable is not
     *     found this function returns null.
     */
    @NonNull
    String getCppCompilerExecutable();

    /** Returns to the path of the Cmake executable. */
    @NonNull
    String getCmakePath();
}
