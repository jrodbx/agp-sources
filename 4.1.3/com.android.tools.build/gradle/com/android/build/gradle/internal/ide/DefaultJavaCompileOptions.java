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
import com.android.build.gradle.internal.CompileOptions;
import com.android.builder.model.JavaCompileOptions;
import java.io.Serializable;
import java.util.Objects;

/**
 * Implementation of {@link JavaCompileOptions}.
 */
@Immutable
final class DefaultJavaCompileOptions implements JavaCompileOptions, Serializable {
    private static final long serialVersionUID = 2L;

    @NonNull
    private final String sourceCompatibility;
    @NonNull
    private final String targetCompatibility;
    @NonNull
    private final String encoding;
    private final boolean coreLibraryDesugaringEnabled;

    DefaultJavaCompileOptions(@NonNull CompileOptions options) {
        sourceCompatibility = options.getSourceCompatibility().toString();
        targetCompatibility = options.getTargetCompatibility().toString();
        encoding = options.getEncoding();
        coreLibraryDesugaringEnabled = options.getCoreLibraryDesugaringEnabled() == Boolean.TRUE;
    }

    @NonNull
    @Override
    public String getSourceCompatibility() {
        return sourceCompatibility;
    }

    @NonNull
    @Override
    public String getTargetCompatibility() {
        return targetCompatibility;
    }

    @Override
    public boolean isCoreLibraryDesugaringEnabled() {
        return coreLibraryDesugaringEnabled;
    }

    @NonNull
    @Override
    public String getEncoding() {
        return encoding;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultJavaCompileOptions that = (DefaultJavaCompileOptions) o;
        return Objects.equals(sourceCompatibility, that.sourceCompatibility)
                && Objects.equals(targetCompatibility, that.targetCompatibility)
                && Objects.equals(encoding, that.encoding)
                && Objects.equals(coreLibraryDesugaringEnabled, that.coreLibraryDesugaringEnabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sourceCompatibility, targetCompatibility, encoding, coreLibraryDesugaringEnabled);
    }
}
