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

package com.android.build.gradle.external.cmake;

import com.android.repository.Revision;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/** Cmake functionality bound to a particular Cmake install path. */
public class Cmake {
    private final File cmakeInstallPath;
    private final Map<String, String> cmakeProcessEnvironment;

    public Cmake(File cmakeInstallPath) {
        this.cmakeInstallPath = cmakeInstallPath;
        this.cmakeProcessEnvironment = new ProcessBuilder().environment();
    }

    /**
     * Get the environment that Cmake will be (or already was) started with. If the process hasn't
     * been started yet the changes here will end up in the environment of the spawned process.
     */
    public Map<String, String> environment() {
        return this.cmakeProcessEnvironment;
    }

    /**
     * Returns the Cmake version string as a structure.
     *
     * @return Revision for the current Cmake.
     * @throws IOException I/O failure
     */
    public Revision getVersion() throws IOException {
        return CmakeUtils.getVersion(cmakeInstallPath);
    }
}
