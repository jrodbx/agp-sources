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

import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_API_JAR;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.SdkConstants.FN_PROGUARD_TXT;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.utils.FileUtils;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/** Serializable implementation of AndroidLibrary for use in the model. */
@Immutable
public final class AndroidLibraryImpl extends LibraryImpl implements AndroidLibrary, Serializable {
    private static final long serialVersionUID = 1L;

    @Nullable
    private final String variant;
    @NonNull private final File bundle;
    @NonNull private final File folder;
    @Nullable private final File resStaticLibrary;
    @NonNull
    private final List<AndroidLibrary> androidLibraries;
    @NonNull
    private final Collection<JavaLibrary> javaLibraries;
    @NonNull
    private final Collection<File> localJars;

    private final int hashcode;

    public AndroidLibraryImpl(
            @NonNull MavenCoordinates coordinates,
            @Nullable String buildId,
            @Nullable String projectPath,
            @NonNull File bundle,
            @NonNull File extractedFolder,
            @Nullable File resStaticLibrary,
            @Nullable String variant,
            boolean isProvided,
            boolean isSkipped,
            @NonNull List<AndroidLibrary> androidLibraries,
            @NonNull Collection<JavaLibrary> javaLibraries,
            @NonNull Collection<File> localJavaLibraries) {
        super(buildId, projectPath, null, coordinates, isSkipped, isProvided);
        this.resStaticLibrary = resStaticLibrary;
        this.androidLibraries = ImmutableList.copyOf(androidLibraries);
        this.javaLibraries = ImmutableList.copyOf(javaLibraries);
        this.localJars = ImmutableList.copyOf(localJavaLibraries);
        this.variant = variant;
        this.bundle = bundle;
        this.folder = extractedFolder;
        hashcode = computeHashCode();
    }

    @Nullable
    @Override
    public String getProjectVariant() {
        return variant;
    }

    @NonNull
    @Override
    public File getBundle() {
        return bundle;
    }

    @NonNull
    @Override
    public File getFolder() {
        return folder;
    }

    @NonNull
    @Override
    public List<? extends AndroidLibrary> getLibraryDependencies() {
        return androidLibraries;
    }

    @NonNull
    @Override
    public Collection<? extends JavaLibrary> getJavaDependencies() {
        return javaLibraries;
    }

    @NonNull
    @Override
    public Collection<File> getLocalJars() {
        return localJars;
    }

    @NonNull
    @Override
    public File getManifest() {
        return new File(folder, FN_ANDROID_MANIFEST_XML);
    }

    @NonNull
    @Override
    public File getJarFile() {
        return FileUtils.join(folder, FD_JARS, FN_CLASSES_JAR);
    }

    @NonNull
    @Override
    public File getCompileJarFile() {
        // We use the api.jar file for compiling if that file exists (api.jar is optional in an
        // AAR); otherwise, we use the regular jar file for compiling.
        File apiJarFile = FileUtils.join(folder, FN_API_JAR);
        return apiJarFile.exists() ? apiJarFile : getJarFile();
    }

    @NonNull
    @Override
    public File getResFolder() {
        return new File(folder, FD_RES);
    }

    @Nullable
    @Override
    public File getResStaticLibrary() {
        return resStaticLibrary;
    }

    @NonNull
    @Override
    public File getAssetsFolder() {
        return new File(folder, FD_ASSETS);
    }

    @NonNull
    @Override
    public File getJniFolder() {
        return new File(folder, FD_JNI);
    }

    @NonNull
    @Override
    public File getAidlFolder() {
        return new File(folder, FD_AIDL);
    }


    @NonNull
    @Override
    public File getRenderscriptFolder() {
        return new File(folder, FD_RENDERSCRIPT);
    }

    @NonNull
    @Override
    public File getProguardRules() {
        return new File(folder, FN_PROGUARD_TXT);
    }

    @NonNull
    @Override
    public File getLintJar() {
        return new File(getJarFile().getParentFile(), FN_LINT_JAR);
    }


    @NonNull
    @Override
    public File getExternalAnnotations() {
        return new File(folder, FN_ANNOTATIONS_ZIP);
    }


    @Override
    @NonNull
    public File getPublicResources() {
        return new File(folder, FN_PUBLIC_TXT);
    }


    @Override
    @Deprecated
    public boolean isOptional() {
        return isProvided();
    }

    @NonNull
    @Override
    public File getSymbolFile() {
        return new File(folder, FN_RESOURCE_TEXT);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AndroidLibraryImpl that = (AndroidLibraryImpl) o;

        // quick fail on hashcode to avoid comparing the whole tree
        if (hashcode != that.hashcode || !super.equals(o)) {
            return false;
        }

        return Objects.equal(variant, that.variant)
                && Objects.equal(bundle, that.bundle)
                && Objects.equal(folder, that.folder)
                && Objects.equal(resStaticLibrary, that.resStaticLibrary)
                && Objects.equal(androidLibraries, that.androidLibraries)
                && Objects.equal(javaLibraries, that.javaLibraries)
                && Objects.equal(localJars, that.localJars);
    }

    @Override
    public int hashCode() {
        return hashcode;
    }

    private int computeHashCode() {
        return Objects.hashCode(
                super.hashCode(),
                variant,
                bundle,
                folder,
                resStaticLibrary,
                androidLibraries,
                javaLibraries,
                localJars);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", getName())
                .add("project", getProject())
                .add("variant", variant)
                .add("bundle", bundle)
                .add("folder", folder)
                .add("resStaticLibrary", resStaticLibrary)
                .add("androidLibraries", androidLibraries)
                .add("javaLibraries", javaLibraries)
                .add("localJars", localJars)
                .add("super", super.toString())
                .toString();
    }
}
