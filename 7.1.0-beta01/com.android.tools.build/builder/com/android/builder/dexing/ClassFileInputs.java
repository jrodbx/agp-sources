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

package com.android.builder.dexing;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;

/** Helper methods for creating {@link ClassFileInput} instances. */
public class ClassFileInputs {

    static final PathMatcher jarMatcher =
            FileSystems.getDefault().getPathMatcher("glob:**" + SdkConstants.DOT_JAR);

    private ClassFileInputs() {
        // empty
    }

    /**
     * Creates a {@link com.android.builder.dexing.ClassFileInput} by analyzing the specified root
     * path. It supports discovery of .class files in directories and jar files, while omitting the
     * ones that do not satisfy the specified predicate.
     *
     * <p>In case the path ends with .jar, all .class files in in will be kept and added to the
     * {@link ClassFileInput} object that is created.
     *
     * <p>Otherwise, the root path will be processed as a directory, and all .class files in it will
     * be processed.
     *
     * @param rootPath root path to analyze, jar or a directory
     * @return input {@link ClassFileInput} that provides a list of .class files to process
     */
    @NonNull
    public static ClassFileInput fromPath(@NonNull Path rootPath) {
        if (jarMatcher.matches(rootPath)) {
            return new JarClassFileInput(rootPath);
        } else {
            return new DirectoryBasedClassFileInput(rootPath);
        }
    }
}
