/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.builder.model.TestedTargetVariant;
import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.Objects;

/**
 * Implementation of {@link com.android.builder.model.TestedTargetVariant} that is
 * serializable.
 */
@Immutable
public final class TestedTargetVariantImpl implements TestedTargetVariant, Serializable{
    private static final long serialVersionUID = 1L;

    @NonNull
    private final String mTargetProjectPath;
    @NonNull
    private final String mTargetVariant;

    public TestedTargetVariantImpl(
            @NonNull String targetProjectPath, @NonNull String targetVariant) {
        mTargetProjectPath = targetProjectPath;
        mTargetVariant = targetVariant;
    }

    @NonNull
    @Override
    public String getTargetProjectPath() {
        return mTargetProjectPath;
    }

    @NonNull
    @Override
    public String getTargetVariant() {
        return mTargetVariant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TestedTargetVariantImpl that = (TestedTargetVariantImpl) o;
        return Objects.equals(mTargetProjectPath, that.mTargetProjectPath) &&
                Objects.equals(mTargetVariant, that.mTargetVariant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mTargetProjectPath, mTargetVariant);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("mTargetProjectPath", mTargetProjectPath)
                .add("mTargetVariant", mTargetVariant)
                .toString();
    }
}
