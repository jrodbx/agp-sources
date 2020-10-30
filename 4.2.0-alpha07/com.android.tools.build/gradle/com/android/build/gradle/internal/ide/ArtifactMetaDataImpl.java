/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.ArtifactMetaData;
import java.io.Serializable;
import java.util.Objects;

/**
 * Implementation of ArtifactMetaData that is serializable
 */
@Immutable
public final class ArtifactMetaDataImpl implements ArtifactMetaData, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String name;
    private final boolean isTest;
    private final int type;

    public ArtifactMetaDataImpl(@NonNull String name, boolean isTest, int type) {
        this.name = name;
        this.isTest = isTest;
        this.type = type;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isTest() {
        return isTest;
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ArtifactMetaDataImpl that = (ArtifactMetaDataImpl) o;
        return isTest == that.isTest &&
                type == that.type &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, isTest, type);
    }
}
