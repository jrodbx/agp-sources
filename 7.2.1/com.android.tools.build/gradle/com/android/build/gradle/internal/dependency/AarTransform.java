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

package com.android.build.gradle.internal.dependency;

import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_ASSETS;
import static com.android.SdkConstants.FD_JARS;
import static com.android.SdkConstants.FD_JNI;
import static com.android.SdkConstants.FD_PREFAB_PACKAGE;
import static com.android.SdkConstants.FD_RENDERSCRIPT;
import static com.android.SdkConstants.FD_RES;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.FN_ANNOTATIONS_ZIP;
import static com.android.SdkConstants.FN_CLASSES_JAR;
import static com.android.SdkConstants.FN_LINT_JAR;
import static com.android.SdkConstants.FN_PROGUARD_TXT;
import static com.android.SdkConstants.FN_PUBLIC_TXT;
import static com.android.SdkConstants.FN_RESOURCE_STATIC_LIBRARY;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.SdkConstants.FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML;

import android.databinding.tool.DataBindingBuilder;
import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType;
import com.android.build.gradle.internal.tasks.AarMetadataTask;
import com.android.utils.FileUtils;
import java.io.File;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

/** Transform that returns the content of an extracted AAR folder. */
@DisableCachingByDefault
public abstract class AarTransform implements TransformAction<AarTransform.Parameters> {

    public interface Parameters extends GenericTransformParameters {
        @Input
        Property<ArtifactType> getTargetType();

        @Input
        Property<Boolean> getSharedLibSupport();
    }

    @PathSensitive(PathSensitivity.ABSOLUTE)
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @NonNull
    public static ArtifactType[] getTransformTargets() {
        return new ArtifactType[] {
            // For CLASSES, this transform is ues for runtime, and AarCompileClassesTransform is
            // used for compile
            ArtifactType.SHARED_CLASSES,
            ArtifactType.JAVA_RES,
            ArtifactType.SHARED_JAVA_RES,
            ArtifactType.PROCESSED_JAR,
            ArtifactType.MANIFEST,
            ArtifactType.ANDROID_RES,
            ArtifactType.ASSETS,
            ArtifactType.SHARED_ASSETS,
            ArtifactType.JNI,
            ArtifactType.SHARED_JNI,
            ArtifactType.AIDL,
            ArtifactType.RENDERSCRIPT,
            ArtifactType.UNFILTERED_PROGUARD_RULES,
            ArtifactType.LINT,
            ArtifactType.ANNOTATIONS,
            ArtifactType.PUBLIC_RES,
            ArtifactType.COMPILE_SYMBOL_LIST,
            ArtifactType.DATA_BINDING_ARTIFACT,
            ArtifactType.DATA_BINDING_BASE_CLASS_LOG_ARTIFACT,
            ArtifactType.RES_STATIC_LIBRARY,
            ArtifactType.RES_SHARED_STATIC_LIBRARY,
            ArtifactType.PREFAB_PACKAGE,
            ArtifactType.AAR_METADATA,
            ArtifactType.ART_PROFILE,
        };
    }

    @Override
    public void transform(@NonNull TransformOutputs transformOutputs) {
        File input = getInputArtifact().get().getAsFile();
        ArtifactType targetType = getParameters().getTargetType().get();
        switch (targetType) {
            case CLASSES_JAR:
            case JAVA_RES:
            case PROCESSED_JAR:
                // even though resources are supposed to only be in the main jar of the AAR, this
                // is not necessarily enforced by all build systems generating AAR so it's safer to
                // read all jars from the manifest.
                // For shared libraries, these are provided via SHARED_CLASSES and SHARED_JAVA_RES.
                if (!isShared(input)) {
                    AarTransformUtil.getJars(input).forEach(transformOutputs::file);
                }
                break;
            case SHARED_CLASSES:
            case SHARED_JAVA_RES:
                if (isShared(input)) {
                    AarTransformUtil.getJars(input).forEach(transformOutputs::file);
                }
                break;
            case LINT:
                outputIfExists(FileUtils.join(input, FD_JARS, FN_LINT_JAR), transformOutputs);
                break;
            case MANIFEST:
                // Return both the manifest and the extra snippet for the shared library.
                outputIfExists(new File(input, FN_ANDROID_MANIFEST_XML), transformOutputs);
                if (isShared(input)) {
                    outputIfExists(
                            new File(input, FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML),
                            transformOutputs);
                }
                break;
            case ANDROID_RES:
                outputIfExists(new File(input, FD_RES), transformOutputs);
                break;
            case ASSETS:
                outputIfExists(new File(input, FD_ASSETS), transformOutputs);
                break;
            case JNI:
                outputIfExists(new File(input, FD_JNI), transformOutputs);
                break;
            case AIDL:
                outputIfExists(new File(input, FD_AIDL), transformOutputs);
                break;
            case RENDERSCRIPT:
                outputIfExists(new File(input, FD_RENDERSCRIPT), transformOutputs);
                break;
            case UNFILTERED_PROGUARD_RULES:
                if (!ExtractProGuardRulesTransform.performTransform(
                        FileUtils.join(input, FD_JARS, FN_CLASSES_JAR), transformOutputs, false)) {
                    outputIfExists(new File(input, FN_PROGUARD_TXT), transformOutputs);
                }
                break;
            case ANNOTATIONS:
                outputIfExists(new File(input, FN_ANNOTATIONS_ZIP), transformOutputs);
                break;
            case PUBLIC_RES:
                outputIfExists(new File(input, FN_PUBLIC_TXT), transformOutputs);
                break;
            case COMPILE_SYMBOL_LIST:
                outputIfExists(new File(input, FN_RESOURCE_TEXT), transformOutputs);
                break;
            case RES_STATIC_LIBRARY:
                if (!isShared(input)) {
                    outputIfExists(new File(input, FN_RESOURCE_STATIC_LIBRARY), transformOutputs);
                }
                break;
            case RES_SHARED_STATIC_LIBRARY:
                if (isShared(input)) {
                    outputIfExists(
                            new File(input, SdkConstants.FN_RESOURCE_SHARED_STATIC_LIBRARY),
                            transformOutputs);
                }
                break;
            case DATA_BINDING_ARTIFACT:
                outputIfExists(
                        new File(input, DataBindingBuilder.DATA_BINDING_ROOT_FOLDER_IN_AAR),
                        transformOutputs);
                break;
            case DATA_BINDING_BASE_CLASS_LOG_ARTIFACT:
                outputIfExists(
                        new File(
                                input,
                                DataBindingBuilder.DATA_BINDING_CLASS_LOG_ROOT_FOLDER_IN_AAR),
                        transformOutputs);
                break;
            case PREFAB_PACKAGE:
                outputIfExists(new File(input, FD_PREFAB_PACKAGE), transformOutputs);
                break;
            case AAR_METADATA:
                outputIfExists(
                        FileUtils.join(input, AarMetadataTask.AAR_METADATA_ENTRY_PATH.split("/")),
                        transformOutputs);
                break;
            case ART_PROFILE:
                outputIfExists(
                        FileUtils.join(
                                input,
                                SdkConstants.FN_ART_PROFILE),
                        transformOutputs);
                break;
            default:
                throw new RuntimeException("Unsupported type in AarTransform: " + targetType);
        }
    }

    private boolean isShared(@NonNull File explodedAar) {
        return getParameters().getSharedLibSupport().get()
                && new File(explodedAar, FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML).exists();
    }

    private static void outputIfExists(@NonNull File file, @NonNull TransformOutputs outputs) {
        if (file.isDirectory()) {
            outputs.dir(file);
        } else if (file.isFile()) {
            outputs.file(file);
        }
    }
}
