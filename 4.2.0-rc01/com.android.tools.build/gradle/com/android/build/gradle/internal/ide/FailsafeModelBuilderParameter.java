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

package com.android.build.gradle.internal.ide;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.ModelBuilderParameter;

/**
 * An implementation of the used parameter which filters unsupported operations before delegating
 * them and replaces exceptions by default values when needed.
 *
 * <p>Parameters are dynamically built as proxies and will throw exception instead of retuning
 * default values if the required field has not been set.
 */
class FailsafeModelBuilderParameter implements ModelBuilderParameter {
    @NonNull private final ModelBuilderParameter delegate;

    FailsafeModelBuilderParameter(@NonNull ModelBuilderParameter delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean getShouldBuildVariant() {
        try {
            return delegate.getShouldBuildVariant();
        } catch (Throwable t) {
            // Default value is to build the entire Android Project
            return true;
        }
    }

    @Override
    public void setShouldBuildVariant(boolean shouldBuildVariant) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean getShouldGenerateSources() {
        try {
            return delegate.getShouldGenerateSources();
        } catch (Throwable t) {
            // Default value is not to generate sources
            return false;
        }
    }

    @Override
    public void setShouldGenerateSources(boolean shouldGenerateSources) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public String getVariantName() {
        try {
            return delegate.getVariantName();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void setVariantName(@NonNull String variantName) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public String getAbiName() {
        try {
            return delegate.getAbiName();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void setAbiName(@NonNull String abi) {
        throw new UnsupportedOperationException();
    }
}
