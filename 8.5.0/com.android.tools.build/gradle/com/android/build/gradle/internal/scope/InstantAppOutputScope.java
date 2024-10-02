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

package com.android.build.gradle.internal.scope;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.cxx.json.PlainFileGsonTypeAdaptor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import org.apache.commons.io.FileUtils;

/**
 * Information about expected Outputs from an InstantApp build.
 *
 * <p>This will contain:
 *
 * <ul>
 *   <li>The instantApp applicationId
 *   <li>A single instantApp zip bundle
 *   <li>One or more APK directory references
 * </ul>
 */
public class InstantAppOutputScope {

    private static final String PERSISTED_FILE_NAME = "instant-app.json";

    @NonNull private final String applicationId;
    @NonNull private final File instantAppBundle;
    @NonNull private final List<File> apkDirectories;

    public InstantAppOutputScope(
            @NonNull String applicationId,
            @NonNull File instantAppBundle,
            @NonNull List<File> apkDirectories) {
        this.applicationId = applicationId;
        this.instantAppBundle = instantAppBundle;
        this.apkDirectories = apkDirectories;
    }

    @NonNull
    public String getApplicationId() {
        return applicationId;
    }

    @NonNull
    public File getInstantAppBundle() {
        return instantAppBundle;
    }

    @NonNull
    public List<File> getApkDirectories() {
        return apkDirectories;
    }

    public void save(@NonNull File outputDirectory) throws IOException {
        File outputFile = new File(outputDirectory, PERSISTED_FILE_NAME);
        Gson gson =
                new GsonBuilder()
                        .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                        .create();
        FileUtils.write(outputFile, gson.toJson(this));
    }

    @Nullable
    public static InstantAppOutputScope load(@NonNull File directory) throws IOException {
        File input = new File(directory, PERSISTED_FILE_NAME);
        if (!input.exists()) {
            return null;
        }

        Gson gson =
                new GsonBuilder()
                        .registerTypeAdapter(File.class, new PlainFileGsonTypeAdaptor())
                        .create();
        try (FileReader fr = new FileReader(input)) {
            return gson.fromJson(fr, InstantAppOutputScope.class);
        }
    }
}
