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

import static com.android.utils.FileUtils.toSystemIndependentPath;

import com.android.SdkConstants;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * This represents input containing .class files. It is used as an input for the dexing phase.All
 * files are specified relative to some base directory, or .jar containing them. This is necessary
 * in order to process the packages properly.
 *
 * <p>When using instances of {@link ClassFileInput} make sure that you invoke {@link #close()}
 * after you are done using it.
 */
public interface ClassFileInput extends Closeable {

    /** Accepts Unix-style or Windows-style absolute or relative path. */
    public Predicate<String> CLASS_MATCHER =
            s -> {
                String lowerCase = toSystemIndependentPath(s.toLowerCase(Locale.US));
                if (!lowerCase.endsWith(SdkConstants.DOT_CLASS)) {
                    return false;
                }

                if (lowerCase.equals("module-info.class")
                        || lowerCase.endsWith("/module-info.class")) {
                    return false;
                }

                if (lowerCase.startsWith("/meta-inf/") || lowerCase.startsWith("meta-inf/")) {
                    return false;
                }
                return true;
            };

    /**
     * @param filter filter specify which files should be part of the class input
     * @return a {@link Stream} for all the entries that satisfies the passed filter.
     * @throws IOException if the jar/directory cannot be read correctly.
     */
    Stream<ClassFileEntry> entries(BiPredicate<Path, String> filter) throws IOException;

    /**
     * @return the root {@link Path} of this {@link ClassFileInput}.
     */
    Path getPath();
}
