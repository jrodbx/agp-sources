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

package com.android.builder.dependency.level2;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.FD_AAR_LIBS;
import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.SdkConstants.FN_PROGUARD_TXT;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.builder.dependency.HashCodeUtils;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.model.MavenCoordinates;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Represents an Android Library and its content.
 */
@Immutable
public final class AndroidDependency extends ExtractedDependency {

    @NonNull
    private final File jarsRootFolder;

    /**
     * Whether the library is an android Lib sub-module. This is different from testing {@link
     * #getProjectPath()} as a module could wrap a local aar, which is not the same as a lib
     * sub-module.
     */
    private final boolean isSubModule;

    private final int hashCode;

    public static AndroidDependency createExplodedAarLibrary(
            @NonNull File artifactFile,
            @NonNull MavenCoordinates coordinates,
            @NonNull String name,
            @Nullable String projectPath,
            @NonNull File extractedFolder) {
        return new AndroidDependency(
                artifactFile,
                coordinates,
                name,
                projectPath,
                extractedFolder,
                new File(extractedFolder, FD_JARS),
                null, /*variant*/
                false /*IsSubModule*/);
    }

    public AndroidDependency(
            @Nullable File artifactFile,
            @NonNull MavenCoordinates coordinates,
            @NonNull String name,
            @Nullable String projectPath,
            @NonNull File extractedFolder,
            @NonNull File jarsRootFolder,
            @Nullable String variant,
            boolean isSubModule) {
        super(artifactFile, coordinates, name, projectPath, extractedFolder, variant);
        this.jarsRootFolder = jarsRootFolder;
        this.isSubModule = isSubModule;
        hashCode = computeHashCode();

        // can only have non null variant if it's a sub-module.
        Preconditions.checkArgument(variant == null || projectPath != null);
    }

    @NonNull
    @Override
    public File getArtifactFile() {
        throw new UnsupportedOperationException(
                "getArtifactFile() is no longer supported by AndroidDependency.");
    }

    /**
     * Returns whether the library is an android Lib sub-module.
     *
     * This is different from testing {@link #getProjectPath()} as a module could wrap a local aar,
     * which is not the same as a lib sub-module.
     */
    public boolean isSubModule() {
        return isSubModule;
    }

    /**
     * returns the list of local jar for this android AAR.
     *
     * This look on the file system for any jars under $AAR/lib
     */
    @NonNull
    public List<File> getLocalJars() {
        List<File> localJars = Lists.newArrayList();
        File[] jarList = new File(getJarsRootFolder(), FD_AAR_LIBS).listFiles();
        if (jarList != null) {
            for (File jars : jarList) {
                if (jars.isFile() && jars.getName().endsWith(DOT_JAR)) {
                    localJars.add(jars);
                }
            }
        }

        return localJars;
    }

    @Override
    @NonNull
    public File getJarFile() {
        return new File(getJarsRootFolder(), FN_CLASSES_JAR);
    }

    @Nullable
    @Override
    public List<File> getAdditionalClasspath() {
        return getLocalJars();
    }

    @NonNull
    public File getJniFolder() {
        return new File(getExtractedFolder(), FD_JNI);
    }

    @NonNull
    public File getAidlFolder() {
        return new File(getExtractedFolder(), FD_AIDL);
    }

    @NonNull
    public File getRenderscriptFolder() {
        return new File(getExtractedFolder(), FD_RENDERSCRIPT);
    }

    @NonNull
    public File getProguardRules() {
        return new File(getExtractedFolder(), FN_PROGUARD_TXT);
    }

    @NonNull
    public File getLintJar() {
        return new File(getJarsRootFolder(), FN_LINT_JAR);
    }

    @NonNull
    public File getExternalAnnotations() {
        return new File(getExtractedFolder(), FN_ANNOTATIONS_ZIP);
    }

    @NonNull
    public File getPublicResources() {
        return new File(getExtractedFolder(), FN_PUBLIC_TXT);
    }

    @NonNull
    public File getSymbolFile() {
        return new File(getExtractedFolder(), FN_RESOURCE_TEXT);
    }

    @NonNull
    protected File getJarsRootFolder() {
        return jarsRootFolder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AndroidDependency that = (AndroidDependency) o;
        return isSubModule == that.isSubModule
                && Objects.equals(jarsRootFolder, that.jarsRootFolder);
    }

    private int computeHashCode() {
        return HashCodeUtils.hashCode(
                super.hashCode(),
                isSubModule,
                jarsRootFolder);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("artifactFile", getArtifactFile())
                .add("coordinates", getCoordinates())
                .add("projectPath", getProjectPath())
                .add("extractedFolder", getExtractedFolder())
                .add("variant", getVariant())
                .add("isSubModule", isSubModule)
                .add("jarsRootFolder", jarsRootFolder)
                .toString();
    }
}