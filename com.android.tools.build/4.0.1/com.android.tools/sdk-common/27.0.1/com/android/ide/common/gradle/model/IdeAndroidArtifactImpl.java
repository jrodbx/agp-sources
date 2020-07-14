/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidArtifactOutput;
import com.android.builder.model.ClassField;
import com.android.builder.model.CodeShrinker;
import com.android.builder.model.InstantRun;
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.TestOptions;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Creates a deep copy of {@link AndroidArtifact}. */
public final class IdeAndroidArtifactImpl extends IdeBaseArtifactImpl
        implements IdeAndroidArtifact {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 5L;

    @NonNull private final Collection<AndroidArtifactOutput> myOutputs;
    @NonNull private final String myApplicationId;
    @NonNull private final String mySourceGenTaskName;
    @NonNull private final Collection<File> myGeneratedResourceFolders;
    @NonNull private final Collection<File> myAdditionalRuntimeApks;
    @NonNull private final Map<String, ClassField> myBuildConfigFields;
    @NonNull private final Map<String, ClassField> myResValues;
    @Nullable private final IdeInstantRun myInstantRun;
    @Nullable private final String mySigningConfigName;
    @Nullable private final Set<String> myAbiFilters;
    @Nullable private final Collection<NativeLibrary> myNativeLibraries;
    @Nullable private final IdeTestOptions myTestOptions;
    @Nullable private final String myInstrumentedTestTaskName;
    @Nullable private final String myBundleTaskName;
    @Nullable private final String myPostBundleTaskModelFile;
    @Nullable private final String myApkFromBundleTaskName;
    @Nullable private final String myPostApkFromBundleTaskModelFile;
    @Nullable private final CodeShrinker myCodeShrinker;

    private final boolean mySigned;
    private final int myHashCode;

    // Used for serialization by the IDE.
    IdeAndroidArtifactImpl() {
        super();
        myOutputs = Collections.emptyList();
        myApplicationId = "";
        mySourceGenTaskName = "";
        myGeneratedResourceFolders = Collections.emptyList();
        myAdditionalRuntimeApks = Collections.emptyList();
        myBuildConfigFields = Collections.emptyMap();
        myResValues = Collections.emptyMap();
        myInstantRun = null;
        mySigningConfigName = null;
        myAbiFilters = null;
        myNativeLibraries = null;
        myTestOptions = null;
        myInstrumentedTestTaskName = null;
        myBundleTaskName = null;
        myPostBundleTaskModelFile = null;
        myApkFromBundleTaskName = null;
        myPostApkFromBundleTaskModelFile = null;
        mySigned = false;
        myCodeShrinker = null;

        myHashCode = 0;
    }

    public IdeAndroidArtifactImpl(
            @NonNull AndroidArtifact artifact,
            @NonNull ModelCache modelCache,
            @NonNull IdeDependenciesFactory dependenciesFactory,
            @Nullable GradleVersion gradleVersion) {
        super(artifact, modelCache, dependenciesFactory, gradleVersion);
        myOutputs = copyOutputs(artifact, modelCache);
        myApplicationId = artifact.getApplicationId();
        mySourceGenTaskName = artifact.getSourceGenTaskName();
        myGeneratedResourceFolders = ImmutableList.copyOf(artifact.getGeneratedResourceFolders());
        myBuildConfigFields =
                IdeModel.copy(artifact.getBuildConfigFields(), modelCache, IdeClassField::new);
        myResValues = IdeModel.copy(artifact.getResValues(), modelCache, IdeClassField::new);
        myInstantRun =
                IdeModel.copyNewProperty(
                        modelCache, artifact::getInstantRun, IdeInstantRun::new, null);
        mySigningConfigName = artifact.getSigningConfigName();
        myAbiFilters = IdeModel.copy(artifact.getAbiFilters());
        myNativeLibraries = copy(modelCache, artifact.getNativeLibraries());
        mySigned = artifact.isSigned();
        myAdditionalRuntimeApks =
                IdeModel.copyNewPropertyNonNull(
                        artifact::getAdditionalRuntimeApks, Collections.emptySet());
        myTestOptions =
                IdeModel.copyNewProperty(
                        modelCache, artifact::getTestOptions, IdeTestOptions::new, null);
        myInstrumentedTestTaskName =
                IdeModel.copyNewProperty(
                        modelCache,
                        artifact::getInstrumentedTestTaskName,
                        Function.identity(),
                        null);
        myBundleTaskName =
                IdeModel.copyNewProperty(
                        modelCache, artifact::getBundleTaskName, Function.identity(), null);
        myPostBundleTaskModelFile =
                IdeModel.copyNewProperty(
                        modelCache,
                        artifact::getBundleTaskOutputListingFile,
                        Function.identity(),
                        null);
        myApkFromBundleTaskName =
                IdeModel.copyNewProperty(
                        modelCache, artifact::getApkFromBundleTaskName, Function.identity(), null);
        myPostApkFromBundleTaskModelFile =
                IdeModel.copyNewProperty(
                        modelCache,
                        artifact::getApkFromBundleTaskOutputListingFile,
                        Function.identity(),
                        null);
        myCodeShrinker =
                IdeModel.copyNewProperty(
                        modelCache, artifact::getCodeShrinker, Function.identity(), null);
        myHashCode = calculateHashCode();
    }

    @NonNull
    private static Collection<AndroidArtifactOutput> copyOutputs(
            @NonNull AndroidArtifact artifact, @NonNull ModelCache modelCache) {
        Collection<AndroidArtifactOutput> outputs;
        try {
            outputs = artifact.getOutputs();
            return IdeModel.copy(
                    outputs,
                    modelCache,
                    output -> new IdeAndroidArtifactOutput(output, modelCache));
        } catch (RuntimeException e) {
            System.err.println("Caught exception: " + e);
            // See http://b/64305584
            return Collections.emptyList();
        }
    }

    @Nullable
    private static Collection<NativeLibrary> copy(
            @NonNull ModelCache modelCache, @Nullable Collection<NativeLibrary> original) {
        return original != null ? IdeModel.copy(original, modelCache, IdeNativeLibrary::new) : null;
    }

    @Override
    @NonNull
    public Collection<AndroidArtifactOutput> getOutputs() {
        return myOutputs;
    }

    @Override
    @NonNull
    public String getApplicationId() {
        return myApplicationId;
    }

    @Override
    @NonNull
    public String getSourceGenTaskName() {
        return mySourceGenTaskName;
    }

    @Override
    @NonNull
    public Collection<File> getGeneratedResourceFolders() {
        return myGeneratedResourceFolders;
    }

    @Override
    @NonNull
    public Map<String, ClassField> getBuildConfigFields() {
        return myBuildConfigFields;
    }

    @Override
    @NonNull
    public Map<String, ClassField> getResValues() {
        return myResValues;
    }

    @Override
    @NonNull
    public InstantRun getInstantRun() {
        if (myInstantRun != null) {
            return myInstantRun;
        }
        throw new UnsupportedOperationException(
                "Unsupported method: AndroidArtifact.getInstantRun()");
    }

    @NonNull
    @Override
    public Collection<File> getAdditionalRuntimeApks() {
        return myAdditionalRuntimeApks;
    }

    @Override
    @Nullable
    public TestOptions getTestOptions() {
        return myTestOptions;
    }

    @Nullable
    @Override
    public String getInstrumentedTestTaskName() {
        return myInstrumentedTestTaskName;
    }

    @Nullable
    @Override
    public String getBundleTaskName() {
        return myBundleTaskName;
    }

    @Nullable
    @Override
    public String getBundleTaskOutputListingFile() {
        return myPostBundleTaskModelFile;
    }

    @Nullable
    @Override
    public String getApkFromBundleTaskName() {
        return myApkFromBundleTaskName;
    }

    @Nullable
    @Override
    public String getApkFromBundleTaskOutputListingFile() {
        return myPostApkFromBundleTaskModelFile;
    }

    @Nullable
    @Override
    public CodeShrinker getCodeShrinker() {
        return myCodeShrinker;
    }

    @Override
    @Nullable
    public String getSigningConfigName() {
        return mySigningConfigName;
    }

    @Override
    @Nullable
    public Set<String> getAbiFilters() {
        return myAbiFilters;
    }

    @Override
    @Nullable
    public Collection<NativeLibrary> getNativeLibraries() {
        return myNativeLibraries;
    }

    @Override
    public boolean isSigned() {
        return mySigned;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeAndroidArtifactImpl)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        IdeAndroidArtifactImpl artifact = (IdeAndroidArtifactImpl) o;
        return artifact.canEquals(this)
                && mySigned == artifact.mySigned
                && Objects.equals(myOutputs, artifact.myOutputs)
                && Objects.equals(myApplicationId, artifact.myApplicationId)
                && Objects.equals(mySourceGenTaskName, artifact.mySourceGenTaskName)
                && Objects.equals(myGeneratedResourceFolders, artifact.myGeneratedResourceFolders)
                && Objects.equals(myBuildConfigFields, artifact.myBuildConfigFields)
                && Objects.equals(myResValues, artifact.myResValues)
                && Objects.equals(myInstantRun, artifact.myInstantRun)
                && Objects.equals(mySigningConfigName, artifact.mySigningConfigName)
                && Objects.equals(myAbiFilters, artifact.myAbiFilters)
                && Objects.equals(myAdditionalRuntimeApks, artifact.myAdditionalRuntimeApks)
                && Objects.equals(myNativeLibraries, artifact.myNativeLibraries)
                && Objects.equals(myTestOptions, artifact.myTestOptions)
                && Objects.equals(myInstrumentedTestTaskName, artifact.myInstrumentedTestTaskName)
                && Objects.equals(myBundleTaskName, artifact.myBundleTaskName)
                && Objects.equals(myPostBundleTaskModelFile, artifact.myPostBundleTaskModelFile)
                && Objects.equals(myCodeShrinker, artifact.myCodeShrinker)
                && Objects.equals(myApkFromBundleTaskName, artifact.myApkFromBundleTaskName)
                && Objects.equals(
                        myPostApkFromBundleTaskModelFile,
                        artifact.myPostApkFromBundleTaskModelFile);
    }

    @Override
    protected boolean canEquals(Object other) {
        return other instanceof IdeAndroidArtifactImpl;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    @Override
    protected int calculateHashCode() {
        return Objects.hash(
                super.calculateHashCode(),
                myOutputs,
                myApplicationId,
                mySourceGenTaskName,
                myGeneratedResourceFolders,
                myBuildConfigFields,
                myResValues,
                myInstantRun,
                mySigningConfigName,
                myAbiFilters,
                myNativeLibraries,
                mySigned,
                myAdditionalRuntimeApks,
                myTestOptions,
                myInstrumentedTestTaskName,
                myBundleTaskName,
                myPostBundleTaskModelFile,
                myCodeShrinker,
                myApkFromBundleTaskName,
                myPostApkFromBundleTaskModelFile);
    }

    @Override
    public String toString() {
        return "IdeAndroidArtifact{"
                + super.toString()
                + ", myOutputs="
                + myOutputs
                + ", myApplicationId='"
                + myApplicationId
                + '\''
                + ", mySourceGenTaskName='"
                + mySourceGenTaskName
                + '\''
                + ", myGeneratedResourceFolders="
                + myGeneratedResourceFolders
                + ", myBuildConfigFields="
                + myBuildConfigFields
                + ", myResValues="
                + myResValues
                + ", myInstantRun="
                + myInstantRun
                + ", mySigningConfigName='"
                + mySigningConfigName
                + '\''
                + ", myAbiFilters="
                + myAbiFilters
                + ", myNativeLibraries="
                + myNativeLibraries
                + ", mySigned="
                + mySigned
                + ", myTestOptions="
                + myTestOptions
                + ", myInstrumentedTestTaskName="
                + myInstrumentedTestTaskName
                + ", myBundleTaskName="
                + myBundleTaskName
                + ", myPostBundleTaskModelFile="
                + myPostBundleTaskModelFile
                + ", myCodeShrinker="
                + myCodeShrinker
                + ", myApkFromBundleTaskName="
                + myApkFromBundleTaskName
                + ", myPostApkFromBundleTaskModelFile="
                + myPostApkFromBundleTaskModelFile
                + "}";
    }
}
