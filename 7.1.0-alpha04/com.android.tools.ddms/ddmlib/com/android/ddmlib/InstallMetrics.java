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
package com.android.ddmlib;

public class InstallMetrics {
    private final long uploadStartNs;
    private final long uploadFinishNs;
    private final long installStartNs;
    private final long installFinishNs;

    public InstallMetrics(
            long uploadStartNs, long uploadFinishNs, long installStartNs, long installFinishNs) {
        this.uploadStartNs = uploadStartNs;
        this.uploadFinishNs = uploadFinishNs;
        this.installStartNs = installStartNs;
        this.installFinishNs = installFinishNs;
    }

    /**
     * Returns the VM clock time at which the APK files in the installation began to be uploaded to
     * the device.
     */
    public long getUploadStartNs() {
        return uploadStartNs;
    }

    /**
     * Return the VM clock time at which the APK files in the installation were finished uploading
     * to the device.
     */
    public long getUploadFinishNs() {
        return uploadFinishNs;
    }

    /** Return the VM clock time at which the installation began. */
    public long getInstallStartNs() {
        return installStartNs;
    }

    /** Returns the VM clock time at which the installation completed. */
    public long getInstallFinishNs() {
        return installFinishNs;
    }
}
