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
package com.android.sdklib.tool.sdkmanager;

import com.android.annotations.NonNull;
import com.android.repository.api.Downloader;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import java.io.BufferedReader;
import java.io.PrintStream;

abstract class SdkAction {
    protected final SdkManagerCliSettings mSettings;

    SdkAction(SdkManagerCliSettings settings) {
        mSettings = settings;
    }

    public abstract void execute(@NonNull ProgressIndicator progress)
            throws SdkManagerCli.CommandFailedException;

    public boolean validate(@NonNull ProgressIndicator progress) {
        return true;
    }

    boolean consumeArgument(@NonNull String arg, @NonNull ProgressIndicator progress) {
        return false;
    }

    protected RepoManager getRepoManager() {
        return mSettings.getRepoManager();
    }

    protected Downloader getDownloader() {
        return mSettings.getDownloader();
    }

    protected BufferedReader getInputReader() {
        return mSettings.getInputReader();
    }

    protected PrintStream getOutputStream() {
        return mSettings.getOutputStream();
    }

    protected AndroidSdkHandler getSdkHandler() {
        return mSettings.getSdkHandler();
    }
}
