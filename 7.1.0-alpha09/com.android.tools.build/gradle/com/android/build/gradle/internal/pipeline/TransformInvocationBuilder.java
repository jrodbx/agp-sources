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

package com.android.build.gradle.internal.pipeline;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Context;
import com.android.build.api.transform.SecondaryInput;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.google.common.collect.ImmutableList;
import java.util.Collection;

/**
 * A simple holder for all {@link TransformInvocation} relevant information, provider a Builder
 * style interface to build up.
 */
public class TransformInvocationBuilder {

    Context context;
    Collection<TransformInput> inputs = ImmutableList.of();
    Collection<TransformInput> referencedInputs = ImmutableList.of();
    Collection<SecondaryInput> secondaryInputs = ImmutableList.of();
    TransformOutputProvider transformOutputProvider;
    boolean isIncremental = false;

    public TransformInvocationBuilder(@NonNull Context context) {
        this.context = context;
    }

    public TransformInvocationBuilder addInputs(Collection<TransformInput> inputs) {
        this.inputs = ImmutableList.copyOf(inputs);
        return this;
    }

    public TransformInvocationBuilder addReferencedInputs(Collection<TransformInput> referencedInputs) {
        this.referencedInputs = ImmutableList.copyOf(referencedInputs);
        return this;
    }

    public TransformInvocationBuilder addSecondaryInputs(Collection<SecondaryInput> secondaryInputs) {
        this.secondaryInputs = ImmutableList.copyOf(secondaryInputs);
        return this;
    }

    public TransformInvocationBuilder addOutputProvider(
            @Nullable TransformOutputProvider transformOutputProvider) {
        this.transformOutputProvider = transformOutputProvider;
        return this;
    }

    public TransformInvocationBuilder setIncrementalMode(boolean isIncremental) {
        this.isIncremental = isIncremental;
        return this;
    }

    public TransformInvocation build() {
        return new TransformInvocation() {
            @NonNull
            @Override
            public Context getContext() {
                return context;
            }

            @NonNull
            @Override
            public Collection<TransformInput> getInputs() {
                return inputs;
            }

            @NonNull
            @Override
            public Collection<TransformInput> getReferencedInputs() {
                return referencedInputs;
            }

            @NonNull
            @Override
            public Collection<SecondaryInput> getSecondaryInputs() {
                return secondaryInputs;
            }

            @Nullable
            @Override
            public TransformOutputProvider getOutputProvider() {
                return transformOutputProvider;
            }

            @Override
            public boolean isIncremental() {
                return isIncremental;
            }

        };
    }
}
