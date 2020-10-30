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
package com.android.ide.common.gradle.model;

import static com.android.builder.model.AaptOptions.Namespacing.DISABLED;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AaptOptions;
import com.google.common.annotations.VisibleForTesting;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class IdeAaptOptions implements AaptOptions, Serializable {

    @Nullable private final String ignoreAssets;
    @Nullable private final Collection<String> noCompress;
    private final boolean failOnMissingConfigEntry;
    @NonNull private final List<String> additionalParameters;
    @NonNull private final Namespacing namespacing;

    // Used for serialization by the IDE.
    IdeAaptOptions() {
        ignoreAssets = null;
        noCompress = null;
        failOnMissingConfigEntry = false;
        additionalParameters = Collections.emptyList();
        namespacing = DISABLED;
    }

    // copyNewProperty won't return null for a non-null getter with a non-null default value.
    @SuppressWarnings("ConstantConditions")
    @VisibleForTesting
    public IdeAaptOptions(@NonNull AaptOptions original) {

        ignoreAssets = IdeModel.copyNewProperty(original::getIgnoreAssets, null);
        noCompress = IdeModel.copyNewProperty(original::getNoCompress, null);
        namespacing = IdeModel.copyNewProperty(original::getNamespacing, DISABLED);
        additionalParameters =
                IdeModel.copyNewProperty(
                        original::getAdditionalParameters, Collections.emptyList());
        failOnMissingConfigEntry =
                IdeModel.copyNewProperty(original::getFailOnMissingConfigEntry, false);
    }

    @Override
    @Nullable
    public String getIgnoreAssets() {
        return ignoreAssets;
    }

    @Override
    @Nullable
    public Collection<String> getNoCompress() {
        return noCompress;
    }

    @Override
    public boolean getFailOnMissingConfigEntry() {
        return failOnMissingConfigEntry;
    }

    @Override
    @NonNull
    public List<String> getAdditionalParameters() {
        return additionalParameters;
    }

    @Override
    @NonNull
    public Namespacing getNamespacing() {
        return namespacing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IdeAaptOptions that = (IdeAaptOptions) o;
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
