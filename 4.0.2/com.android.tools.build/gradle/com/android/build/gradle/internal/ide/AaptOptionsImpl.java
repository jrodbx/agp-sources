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
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.AaptOptions;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of AaptOptions that is Serializable.
 *
 * <p>Should only be used for the model.
 */
@Immutable
public final class AaptOptionsImpl implements AaptOptions, Serializable {
    private static final long serialVersionUID = 1L;

    @Nullable
    private final String ignoreAssets;

    @Nullable
    private final Collection<String> noCompress;

    private final boolean failOnMissingConfigEntry;

    @NonNull
    private final List<String> additionalParameters;

    @NonNull private final AaptOptions.Namespacing namespacing;

    public static AaptOptions create(
            @NonNull com.android.build.gradle.internal.dsl.AaptOptions aaptOptions) {
        return new AaptOptionsImpl(
                aaptOptions.getIgnoreAssets(),
                aaptOptions.getNoCompress(),
                aaptOptions.getFailOnMissingConfigEntry(),
                aaptOptions.getAdditionalParameters(),
                aaptOptions.getNamespaced() ? Namespacing.REQUIRED : Namespacing.DISABLED);
    }

    public static AaptOptions createDummy() {
        return new AaptOptionsImpl(
                "", Collections.emptyList(), false, Collections.emptyList(), Namespacing.DISABLED);
    }

    public AaptOptionsImpl(
            @Nullable String ignoreAssets,
            @Nullable Collection<String> noCompress,
            boolean failOnMissingConfigEntry,
            @Nullable List<String> additionalParameters,
            @NonNull Namespacing namespacing) {
        this.ignoreAssets = ignoreAssets;
        this.failOnMissingConfigEntry = failOnMissingConfigEntry;
        this.noCompress =
                noCompress == null ? null : ImmutableList.copyOf(noCompress);
        this.additionalParameters =
                additionalParameters == null ? ImmutableList.of() : additionalParameters;
        this.namespacing = namespacing;
    }

    @Nullable
    @Override
    public String getIgnoreAssets() {
        return ignoreAssets;
    }

    @Nullable
    @Override
    public Collection<String> getNoCompress() {
        return noCompress;
    }

    @Override
    public boolean getFailOnMissingConfigEntry() {
        return failOnMissingConfigEntry;
    }

    @NonNull
    @Override
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    @NonNull
    @Override
    public Namespacing getNamespacing() {
        return namespacing;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("ignoreAssets", ignoreAssets)
                .add("noCompress", noCompress)
                .add("failOnMissingConfigEntry", failOnMissingConfigEntry)
                .add("additionalParameters", additionalParameters)
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
        return failOnMissingConfigEntry == that.failOnMissingConfigEntry
                && Objects.equals(ignoreAssets, that.ignoreAssets)
                && Objects.equals(noCompress, that.noCompress)
                && Objects.equals(additionalParameters, that.additionalParameters)
                && namespacing == that.namespacing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                ignoreAssets,
                noCompress,
                failOnMissingConfigEntry,
                additionalParameters,
                namespacing);
    }
}
