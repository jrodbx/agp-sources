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
import com.android.annotations.concurrency.Immutable;
import com.android.build.api.transform.QualifiedContent;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.Serializable;
import java.util.Set;

/** Basic implementation of {@link QualifiedContent}. */
@Immutable
class QualifiedContentImpl implements QualifiedContent, Serializable {

    @NonNull
    private final String name;
    @NonNull
    private final File file;
    @NonNull
    private final Set<ContentType> contentTypes;
    @NonNull
    private final Set<? super Scope> scopes;

    protected QualifiedContentImpl(
            @NonNull String name,
            @NonNull File file,
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<? super Scope> scopes) {
        this.name = name;
        this.file = file;
        this.contentTypes = ImmutableSet.copyOf(contentTypes);
        this.scopes = ImmutableSet.copyOf(scopes);
    }

    protected QualifiedContentImpl(@NonNull QualifiedContent qualifiedContent) {
        this.name = qualifiedContent.getName();
        this.file = qualifiedContent.getFile();
        this.contentTypes = qualifiedContent.getContentTypes();
        this.scopes = qualifiedContent.getScopes();
    }

    @Override
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @NonNull
    public File getFile() {
        return file;
    }

    @NonNull
    @Override
    public Set<ContentType> getContentTypes() {
        return contentTypes;
    }

    @NonNull
    @Override
    public Set<? super Scope> getScopes() {
        return scopes;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", name)
                .add("file", file)
                .add("contentTypes", contentTypes)
                .add("scopes", scopes)
                .toString();
    }
}
