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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.InstantRun;
import java.io.File;
import java.io.Serializable;
import java.util.Objects;

/**
 * Implementation of the {@link InstantRun} model
 */
@Immutable
final class InstantRunImpl implements InstantRun, Serializable {
    private static final long serialVersionUID = 1L;

    @NonNull
    private final File infoFile;
    private final int supportStatus;

    public InstantRunImpl(@NonNull File infoFile, int supportStatus) {
        this.infoFile = infoFile;
        this.supportStatus = supportStatus;
    }

    @NonNull
    @Override
    public File getInfoFile() {
        return infoFile;
    }

    @Override
    public boolean isSupportedByArtifact() {
        return supportStatus == InstantRun.STATUS_SUPPORTED;
    }

    @Override
    public int getSupportStatus() {
        return supportStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InstantRunImpl that = (InstantRunImpl) o;
        return supportStatus == that.supportStatus &&
                Objects.equals(infoFile, that.infoFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(infoFile, supportStatus);
    }
}
