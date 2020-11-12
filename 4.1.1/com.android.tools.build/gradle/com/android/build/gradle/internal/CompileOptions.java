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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either excodess or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import java.util.Locale;
import javax.inject.Inject;
import org.gradle.api.JavaVersion;

/** Java compilation options. */
public class CompileOptions implements com.android.build.api.dsl.CompileOptions {
    private static final String VERSION_PREFIX = "VERSION_";

    @Nullable
    private JavaVersion sourceCompatibility;

    @Nullable
    private JavaVersion targetCompatibility;

    @NonNull
    private String encoding = Charsets.UTF_8.name();

    @Nullable private Boolean incremental = null;

    @Nullable private Boolean coreLibraryDesugaringEnabled = null;

    /** @see #setDefaultJavaVersion(JavaVersion) */
    @NonNull
    @VisibleForTesting
    JavaVersion defaultJavaVersion = JavaVersion.VERSION_1_6;

    @Inject
    public CompileOptions() {}

    public void setSourceCompatibility(@NonNull Object sourceCompatibility) {
        this.sourceCompatibility = convert(sourceCompatibility);
    }

    @Override
    public void sourceCompatibility(@NonNull Object sourceCompatibility) {
        this.sourceCompatibility = convert(sourceCompatibility);
    }

    @Override
    public void setSourceCompatibility(@NonNull JavaVersion sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    @Override
    @NonNull
    public JavaVersion getSourceCompatibility() {
        return sourceCompatibility != null ? sourceCompatibility : defaultJavaVersion;
    }

    public void setTargetCompatibility(@NonNull Object targetCompatibility) {
        this.targetCompatibility = convert(targetCompatibility);
    }

    @Override
    public void targetCompatibility(@NonNull Object targetCompatibility) {
        this.targetCompatibility = convert(targetCompatibility);
    }

    @Override
    public void setTargetCompatibility(@NonNull JavaVersion targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    @Override
    @NonNull
    public JavaVersion getTargetCompatibility() {
        return targetCompatibility != null ? targetCompatibility : defaultJavaVersion;
    }

    @Override
    public void setEncoding(@NonNull String encoding) {
        this.encoding = checkNotNull(encoding);
    }

    @Override
    @NonNull
    public String getEncoding() {
        return encoding;
    }

    /**
     * Default java version, based on the target SDK. Set by the plugin, not meant to be used in
     * build files by users.
     */
    public void setDefaultJavaVersion(@NonNull JavaVersion defaultJavaVersion) {
        this.defaultJavaVersion = checkNotNull(defaultJavaVersion);
    }

    /**
     * Whether Java compilation should be incremental or not.
     *
     * <p>The default value is {@code true}.
     *
     * <p>Note that even if this option is set to {@code true}, Java compilation may still be
     * non-incremental (e.g., if incremental annotation processing is not yet possible in the
     * project).
     */
    @Nullable
    public Boolean getIncremental() {
        return incremental;
    }

    /** @see #getIncremental() */
    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }

    @Nullable
    public Boolean getCoreLibraryDesugaringEnabled() {
        return coreLibraryDesugaringEnabled;
    }

    @Override
    public boolean isCoreLibraryDesugaringEnabled() {
        return coreLibraryDesugaringEnabled != null ? coreLibraryDesugaringEnabled : false;
    }

    @Override
    public void setCoreLibraryDesugaringEnabled(boolean coreLibraryDesugaringEnabled) {
        this.coreLibraryDesugaringEnabled = coreLibraryDesugaringEnabled;
    }

    /**
     * Converts all possible supported way of specifying a Java version to a {@link JavaVersion}.
     * @param version the user provided java version.
     */
    @NonNull
    private static JavaVersion convert(@NonNull Object version) {
        // for backward version reasons, we support setting strings like 'Version_1_6'
        if (version instanceof String) {
            final String versionString = (String) version;
            if (versionString.toUpperCase(Locale.ENGLISH).startsWith(VERSION_PREFIX)) {
                version = versionString.substring(VERSION_PREFIX.length()).replace('_', '.');
            }
        }
        return JavaVersion.toVersion(version);
    }
}
