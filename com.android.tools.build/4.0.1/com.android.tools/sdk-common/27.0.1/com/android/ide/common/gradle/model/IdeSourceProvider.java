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
import com.android.builder.model.SourceProvider;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/** Creates a deep copy of a {@link SourceProvider}. */
public final class IdeSourceProvider implements SourceProvider, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 3L;

    @NonNull private final String myName;
    @NonNull private final File myManifestFile;
    @NonNull private final Collection<File> myJavaDirectories;
    @NonNull private final Collection<File> myResourcesDirectories;
    @NonNull private final Collection<File> myAidlDirectories;
    @NonNull private final Collection<File> myRenderscriptDirectories;
    @NonNull private final Collection<File> myCDirectories;
    @NonNull private final Collection<File> myCppDirectories;
    @NonNull private final Collection<File> myResDirectories;
    @NonNull private final Collection<File> myAssetsDirectories;
    @NonNull private final Collection<File> myJniLibsDirectories;
    @NonNull private final Collection<File> myShadersDirectories;
    private final int myHashCode;

    // Used for serialization by the IDE.
    IdeSourceProvider() {
        myName = "";
        //noinspection ConstantConditions
        myManifestFile = null;
        myJavaDirectories = Collections.emptyList();
        myResourcesDirectories = Collections.emptyList();
        myAidlDirectories = Collections.emptyList();
        myRenderscriptDirectories = Collections.emptyList();
        myCDirectories = Collections.emptyList();
        myCppDirectories = Collections.emptyList();
        myResDirectories = Collections.emptyList();
        myAssetsDirectories = Collections.emptyList();
        myJniLibsDirectories = Collections.emptyList();
        myShadersDirectories = Collections.emptyList();

        myHashCode = 0;
    }

    public IdeSourceProvider(@NonNull SourceProvider provider) {
        myName = provider.getName();
        myManifestFile = provider.getManifestFile();
        myJavaDirectories = ImmutableList.copyOf(provider.getJavaDirectories());
        myResourcesDirectories = ImmutableList.copyOf(provider.getResourcesDirectories());
        myAidlDirectories = ImmutableList.copyOf(provider.getAidlDirectories());
        myRenderscriptDirectories = ImmutableList.copyOf(provider.getRenderscriptDirectories());
        myCDirectories = ImmutableList.copyOf(provider.getCDirectories());
        myCppDirectories = ImmutableList.copyOf(provider.getCppDirectories());
        myResDirectories = ImmutableList.copyOf(provider.getResDirectories());
        myAssetsDirectories = ImmutableList.copyOf(provider.getAssetsDirectories());
        myJniLibsDirectories = ImmutableList.copyOf(provider.getJniLibsDirectories());
        myShadersDirectories =
                IdeModel.copyNewProperty(
                        () -> ImmutableList.copyOf(provider.getShadersDirectories()),
                        Collections.emptyList());
        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public File getManifestFile() {
        return myManifestFile;
    }

    @Override
    @NonNull
    public Collection<File> getJavaDirectories() {
        return myJavaDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getResourcesDirectories() {
        return myResourcesDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getAidlDirectories() {
        return myAidlDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getRenderscriptDirectories() {
        return myRenderscriptDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getCDirectories() {
        return myCDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getCppDirectories() {
        return myCppDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getResDirectories() {
        return myResDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getAssetsDirectories() {
        return myAssetsDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getJniLibsDirectories() {
        return myJniLibsDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getShadersDirectories() {
        return myShadersDirectories;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeSourceProvider)) {
            return false;
        }
        IdeSourceProvider provider = (IdeSourceProvider) o;
        return Objects.equals(myName, provider.myName)
                && Objects.equals(myManifestFile, provider.myManifestFile)
                && Objects.equals(myJavaDirectories, provider.myJavaDirectories)
                && Objects.equals(myResourcesDirectories, provider.myResourcesDirectories)
                && Objects.equals(myAidlDirectories, provider.myAidlDirectories)
                && Objects.equals(myRenderscriptDirectories, provider.myRenderscriptDirectories)
                && Objects.equals(myCDirectories, provider.myCDirectories)
                && Objects.equals(myCppDirectories, provider.myCppDirectories)
                && Objects.equals(myResDirectories, provider.myResDirectories)
                && Objects.equals(myAssetsDirectories, provider.myAssetsDirectories)
                && Objects.equals(myJniLibsDirectories, provider.myJniLibsDirectories)
                && Objects.equals(myShadersDirectories, provider.myShadersDirectories);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(
                myName,
                myManifestFile,
                myJavaDirectories,
                myResourcesDirectories,
                myAidlDirectories,
                myRenderscriptDirectories,
                myCDirectories,
                myCppDirectories,
                myResDirectories,
                myAssetsDirectories,
                myJniLibsDirectories,
                myShadersDirectories);
    }

    @Override
    public String toString() {
        return "IdeSourceProvider{"
                + "myName='"
                + myName
                + '\''
                + ", myManifestFile="
                + myManifestFile
                + ", myJavaDirectories="
                + myJavaDirectories
                + ", myResourcesDirectories="
                + myResourcesDirectories
                + ", myAidlDirectories="
                + myAidlDirectories
                + ", myRenderscriptDirectories="
                + myRenderscriptDirectories
                + ", myCDirectories="
                + myCDirectories
                + ", myCppDirectories="
                + myCppDirectories
                + ", myResDirectories="
                + myResDirectories
                + ", myAssetsDirectories="
                + myAssetsDirectories
                + ", myJniLibsDirectories="
                + myJniLibsDirectories
                + ", myShadersDirectories="
                + myShadersDirectories
                + '}';
    }
}
