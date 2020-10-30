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
import com.android.build.gradle.external.cmake.CmakeUtils;
import com.android.build.gradle.external.cmake.server.receiver.ServerReceiver;
import com.android.repository.Revision;
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;

/** Server factory thats used to create Cmake server objects based on the Cmake version. */
public class ServerFactory {
    /**
     * Creates a Cmake server object for the given Cmake in the install path.
     *
     * @param cmakeInstallPath - path to cmake
     * @param serverReceiver - message receiver from Cmake server
     * @return Cmake Server object
     * @throws IOException I/O failure
     */
    @Nullable
    public static Server create(
            @NonNull File cmakeInstallPath, @NonNull ServerReceiver serverReceiver)
            throws IOException {
        return (create(CmakeUtils.getVersion(cmakeInstallPath), cmakeInstallPath, serverReceiver));
    }

    /**
     * Creates a Cmake server object for the given Cmake version.
     *
     * @param version - Cmake version for which Cmake server object needs to be created
     * @param cmakeInstallPath - path to cmake
     * @param serverReceiver - message receiver from Cmake server
     * @return Cmake Server object
     */
    @Nullable
    @VisibleForTesting
    public static Server create(
            Revision version,
            @NonNull File cmakeInstallPath,
            @NonNull ServerReceiver serverReceiver) {
        if (version.getMajor() >= 3 && version.getMinor() >= 7) {
            return new ServerProtocolV1(cmakeInstallPath, serverReceiver);
        }
        return null;
    }
}
