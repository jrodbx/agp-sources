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
import com.android.builder.model.AaptOptions;
import com.google.common.annotations.VisibleForTesting;
import java.io.Serializable;
import java.util.Objects;

public class IdeAaptOptionsImpl implements IdeAaptOptions, Serializable {

    @NonNull private final Namespacing namespacing;

    // Used for serialization by the IDE.
    IdeAaptOptionsImpl() {
        namespacing = DISABLED;
    }

    // copyNewProperty won't return null for a non-null getter with a non-null default value.
    @SuppressWarnings("ConstantConditions")
    @VisibleForTesting
    public IdeAaptOptionsImpl(@NonNull AaptOptions original) {
        namespacing = IdeModel.copyNewProperty(original::getNamespacing, DISABLED);
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
        IdeAaptOptionsImpl that = (IdeAaptOptionsImpl) o;
        return namespacing == that.namespacing;
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespacing);
    }
}
