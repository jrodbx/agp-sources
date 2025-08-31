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
package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * A {@link ResourcePreprocessor} used when no other preprocessor is enabled.
 */
public final class NoOpResourcePreprocessor implements ResourcePreprocessor {
    public static final NoOpResourcePreprocessor INSTANCE = new NoOpResourcePreprocessor();

    // private constructor to avoid new instances.
    private NoOpResourcePreprocessor() { }

    @NonNull
    @Override
    public Collection<File> getFilesToBeGenerated(@NonNull File original) {
        return Collections.emptySet();
    }

    @Override
    public void generateFile(@NonNull File toBeGenerated, @NonNull File original) {
        throw new IllegalStateException("Should not be called");
    }
}
