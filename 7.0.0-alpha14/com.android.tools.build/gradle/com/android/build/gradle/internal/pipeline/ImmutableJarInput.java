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
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import java.io.File;
import java.util.Set;

/**
 * Immutable version of {@link JarInput}.
 */
@Immutable
class ImmutableJarInput extends QualifiedContentImpl implements JarInput {

    @NonNull
    private final Status status;

    ImmutableJarInput(
            @NonNull String name,
            @NonNull File file,
            @NonNull Status status,
            @NonNull Set<ContentType> contentTypes,
            @NonNull Set<? super Scope> scopes) {
        super(name, file, contentTypes, scopes);
        this.status = status;
    }

    ImmutableJarInput(
            @NonNull QualifiedContent qualifiedContent,
            @NonNull Status status) {
        super(qualifiedContent);
        this.status = status;
    }

    @NonNull
    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", getName())
                .add("file", getFile())
                .add("contentTypes", Joiner.on(',').join(getContentTypes()))
                .add("scopes", Joiner.on(',').join(getScopes()))
                .add("status", status)
                .toString();
    }
}
