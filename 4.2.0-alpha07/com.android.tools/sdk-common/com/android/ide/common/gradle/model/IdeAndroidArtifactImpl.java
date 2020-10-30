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
import com.android.builder.model.NativeLibrary;
import com.android.builder.model.TestOptions;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Creates a deep copy of {@link AndroidArtifact}. */
public final class IdeAndroidArtifactImpl extends IdeBaseArtifactImpl
        implements IdeAndroidArtifact {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 5L;

    @NonNull private final List<IdeAndroidArtifactOutput> myOutputs;
    @NonNull private final String myApplicationId;
    @NonNull private final String mySourceGenTaskName;
    @NonNull private final List<File> myGeneratedResourceFolders;
    @NonNull private final List<File> myAdditionalRuntimeApks;
    @Nullable private final IdeInstantRun myInstantRun;
    @Nullable private final String mySigningConfigName;
    @NonNull private final Set<String> myAbiFilters;
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
        myInstantRun = null;
        mySigningConfigName = null;
        myAbiFilters = Collections.emptySet();
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
            @Nullable GradleVersion agpVersion) {
        super(artifact, modelCache, dependenciesFactory, agpVersion);
        myOutputs = copyOutputs(artifact, modelCache, agpVersion);
        myApplicationId = artifact.getApplicationId();
        mySourceGenTaskName = artifact.getSourceGenTaskName();
        myGeneratedResourceFolders = ImmutableList.copyOf(artifact.getGeneratedResourceFolders());
        myInstantRun =
                IdeModel.copyNewProperty(
                        modelCache, artifact::getInstantRun, IdeInstantRunImpl::new, null);
        mySigningConfigName = artifact.getSigningConfigName();
        // In AGP 4.0 and below abiFilters was nullable, normalize null to empty set.
        myAbiFilters =
                ImmutableSet.copyOf(
                        MoreObjects.firstNonNull(artifact.getAbiFilters(), ImmutableSet.of()));
        mySigned = artifact.isSigned();
        myAdditionalRuntimeApks =
                IdeModel.copyNewPropertyNonNull(
                        () -> new ArrayList<>(artifact.getAdditionalRuntimeApks()),
                        Collections.emptyList());
        myTestOptions =
                IdeModel.copyNewProperty(
                        modelCache, artifact::getTestOptions, IdeTestOptionsImpl::new, null);
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
    private static List<IdeAndroidArtifactOutput> copyOutputs(
            @NonNull AndroidArtifact artifact,
            @NonNull ModelCache modelCache,
            @Nullable GradleVersion agpVersion) {
        // getOutputs is deprecated in AGP 4.0.0.
        if (agpVersion != null && agpVersion.compareIgnoringQualifiers("4.0.0") >= 0) {
            return Collections.emptyList();
        }
        List<AndroidArtifactOutput> outputs;
        try {
            outputs = new ArrayList<>(artifact.getOutputs());
            return IdeModel.copy(
                    outputs,
                    modelCache,
                    output -> new IdeAndroidArtifactOutputImpl(output, modelCache));
        } catch (RuntimeException e) {
            System.err.println("Caught exception: " + e);
            // See http://b/64305584
            return Collections.emptyList();
        }
    }

    @Override
    @NonNull
    public List<IdeAndroidArtifactOutput> getOutputs() {
        return myOutputs;
    }

    @Override
    @NonNull
    public String getApplicationId() {
        return myApplicationId;
    }

    @Override
    @NonNull
    public List<File> getGeneratedResourceFolders() {
        return myGeneratedResourceFolders;
    }

    @Override
    @NonNull
    public Map<String, ClassField> getResValues() {
        return Collections.emptyMap();
    }

    @Override
    @NonNull
    public IdeInstantRun getInstantRun() {
        if (myInstantRun != null) {
            return myInstantRun;
        }
        throw new UnsupportedOperationException(
                "Unsupported method: AndroidArtifact.getInstantRun()");
    }

    @Override
    @NonNull
    public List<File> getAdditionalRuntimeApks() {
        return myAdditionalRuntimeApks;
    }

    @Override
    @Nullable
    public TestOptions getTestOptions() {
        return myTestOptions;
    }

    @Override
    @Nullable
    public String getBundleTaskName() {
        return myBundleTaskName;
    }

    @Override
    @Nullable
    public String getBundleTaskOutputListingFile() {
        return myPostBundleTaskModelFile;
    }

    @Override
    @Nullable
    public String getApkFromBundleTaskName() {
        return myApkFromBundleTaskName;
    }

    @Override
    @Nullable
    public String getApkFromBundleTaskOutputListingFile() {
        return myPostApkFromBundleTaskModelFile;
    }

    @Override
    @Nullable
    public CodeShrinker getCodeShrinker() {
        return myCodeShrinker;
    }

    @Override
    @Nullable
    public String getSigningConfigName() {
        return mySigningConfigName;
    }

    @Override
    @NonNull
    public Set<String> getAbiFilters() {
        return myAbiFilters;
    }

    @Nullable
    @Deprecated // This is for ndk-compile, which has long been deprecated
    public Collection<NativeLibrary> getNativeLibraries() {
        return null;
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
                && Objects.equals(myInstantRun, artifact.myInstantRun)
                && Objects.equals(mySigningConfigName, artifact.mySigningConfigName)
                && Objects.equals(myAbiFilters, artifact.myAbiFilters)
                && Objects.equals(myAdditionalRuntimeApks, artifact.myAdditionalRuntimeApks)
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
                myInstantRun,
                mySigningConfigName,
                myAbiFilters,
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
                + ", myInstantRun="
                + myInstantRun
                + ", mySigningConfigName='"
                + mySigningConfigName
                + '\''
                + ", myAbiFilters="
                + myAbiFilters
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
