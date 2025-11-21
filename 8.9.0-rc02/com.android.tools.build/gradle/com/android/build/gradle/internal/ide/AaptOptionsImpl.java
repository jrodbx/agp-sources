/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.builder.model.AaptOptions;
import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.Objects;

/**
 * Implementation of {@link AaptOptions} that is Serializable.
 *
 * <p>Should only be used for the model.
 */
@Immutable
public final class AaptOptionsImpl implements AaptOptions, Serializable {
    private static final long serialVersionUID = 2L;

    @NonNull private final AaptOptions.Namespacing namespacing;

    public static AaptOptions create(
            @NonNull com.android.build.gradle.internal.dsl.AaptOptions aaptOptions) {
        return new AaptOptionsImpl(
                aaptOptions.getNamespaced() ? Namespacing.REQUIRED : Namespacing.DISABLED);
    }

    public AaptOptionsImpl(@NonNull Namespacing namespacing) {
        this.namespacing = namespacing;
    }

    @NonNull
    @Override
    public Namespacing getNamespacing() {
        return namespacing;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("namespacing", namespacing)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AaptOptionsImpl)) {
            return false;
        }
        AaptOptionsImpl that = (AaptOptionsImpl) o;
        return namespacing == that.namespacing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespacing);
    }
}
