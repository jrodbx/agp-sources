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

import com.android.annotations.NonNull;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Implementation of {@link TransformOutputProvider} passed to the transforms.
 */
class TransformOutputProviderImpl implements TransformOutputProvider {

    @NonNull private final IntermediateFolderUtils folderUtils;

    TransformOutputProviderImpl(@NonNull IntermediateFolderUtils folderUtils) {
        this.folderUtils = folderUtils;
    }

    @Override
    public void deleteAll() throws IOException {
        FileUtils.cleanOutputDir(folderUtils.getRootFolder());
    }

    @NonNull
    @Override
    public File getContentLocation(
            @NonNull String name,
            @NonNull Set<QualifiedContent.ContentType> types,
            @NonNull Set<? super QualifiedContent.Scope> scopes,
            @NonNull Format format) {
        return folderUtils.getContentLocation(name, types, scopes, format);
    }
}
