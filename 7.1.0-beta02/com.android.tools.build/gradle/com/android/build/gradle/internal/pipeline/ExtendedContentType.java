/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.pipeline;

import com.android.build.api.transform.QualifiedContent.ContentType;
import com.android.build.api.transform.QualifiedContent.DefaultContentType;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * Content types private to the Android Plugin.
 */
public enum ExtendedContentType implements ContentType {

    /**
     * The content is dex files.
     */
    DEX(0x1000),

    /**
     * Content is a native library.
     */
    NATIVE_LIBS(0x2000),

    /**
     * Instant Run '$override' classes, which contain code of new method bodies.
     *
     * <p>This stream also contains the AbstractPatchesLoaderImpl class for applying HotSwap
     * changes.
     */
    CLASSES_ENHANCED(0x4000),

    /**
     * The content is an artifact exported by the data binding compiler.
     */
    DATA_BINDING(0x10000),

    /** The content is Java source file. @Deprecated don't use! */
    @Deprecated
    JAVA_SOURCES(0x20000),

    /** The content is a dex archive. It contains a single DEX file per class. */
    DEX_ARCHIVE(0x40000),
    ;

    private final int value;

    ExtendedContentType(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }


    /**
     * Returns all {@link DefaultContentType} and {@link ExtendedContentType} content types.
     * @return a set of all known {@link ContentType}
     */
    public static Set<ContentType> getAllContentTypes() {
        return allContentTypes;
    }

    private static final Set<ContentType> allContentTypes;

    static {
        ImmutableSet.Builder<ContentType> builder = ImmutableSet.builder();
        for (DefaultContentType contentType : DefaultContentType.values()) {
            builder.add(contentType);
        }
        for (ExtendedContentType extendedContentType : ExtendedContentType.values()) {
            builder.add(extendedContentType);
        }
        allContentTypes = builder.build();
    }
}
