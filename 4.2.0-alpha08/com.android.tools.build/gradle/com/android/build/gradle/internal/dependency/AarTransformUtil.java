/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency;

import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY;
import static com.android.SdkConstants.LIBS_FOLDER;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

class AarTransformUtil {

    static List<File> getJars(@NonNull File explodedAar) {
        List<File> files = Lists.newArrayList();
        File jarFolder = new File(explodedAar, FD_JARS);

        File file = FileUtils.join(jarFolder, FN_CLASSES_JAR);
        if (file.isFile()) {
            files.add(file);
        }

        // local jars
        final File localJarFolder = new File(jarFolder, LIBS_FOLDER);
        File[] jars = localJarFolder.listFiles((dir, name) -> name.endsWith(SdkConstants.DOT_JAR));

        if (jars != null) {
            Arrays.sort(jars, Comparator.naturalOrder());
            files.addAll((Arrays.asList(jars)));
        }

        return files;
    }

}
