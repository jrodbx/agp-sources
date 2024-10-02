/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.utils;

import com.android.annotations.NonNull;
import java.io.File;

/**
 * Utility methods for native build
 */
public class NdkUtils {

    private static String removeFileExtension(String output) {
        int dotIndex = output.lastIndexOf('.');
        if (dotIndex == -1) {
            return output;
        }
        return output.substring(0, dotIndex);
    }

    @NonNull
    public static String getTargetNameFromBuildOutputFile(@NonNull File output) {
        return getTargetNameFromBuildOutputFileName(output.getName());
    }

    @NonNull
    public static String getTargetNameFromBuildOutputFileName(@NonNull String basename) {
        String artifactName = removeFileExtension(basename);
        if (artifactName.startsWith("lib")) {
            artifactName = artifactName.substring(3);
        }
        return artifactName;
    }
}
