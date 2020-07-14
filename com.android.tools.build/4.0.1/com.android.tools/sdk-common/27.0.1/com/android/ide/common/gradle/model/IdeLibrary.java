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

import static com.android.ide.common.gradle.model.IdeLibraries.computeResolvedCoordinate;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.Library;
import java.io.Serializable;
import java.util.Objects;

/** Creates a deep copy of a {@link Library}. */
public abstract class IdeLibrary implements Library, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 3L;

    @NonNull private final IdeMavenCoordinates myResolvedCoordinates;
    @Nullable private final String myBuildId;
    @Nullable private final String myProject;
    @Nullable private final String myName;
    @Nullable private final Boolean myIsSkipped;
    @Nullable private final Boolean myProvided;
    private final int hashCode;

    // Used for serialization by the IDE.
    IdeLibrary() {
        myResolvedCoordinates = new IdeMavenCoordinates();
        myBuildId = null;
        myProject = null;
        myName = null;
        myIsSkipped = null;
        myProvided = null;

        hashCode = 0;
    }

    protected IdeLibrary(@NonNull Library library, @NonNull ModelCache modelCache) {
        myResolvedCoordinates = computeResolvedCoordinate(library, modelCache);
        myBuildId = IdeModel.copyNewProperty(library::getBuildId, null);
        myProject = IdeModel.copyNewProperty(library::getProject, null);
        myName =
                IdeModel.copyNewProperty(
                        library::getName, null); // Library.getName() was added in 2.2
        myProvided = IdeModel.copyNewProperty(library::isProvided, null);
        myIsSkipped = IdeModel.copyNewProperty(library::isSkipped, null);
        hashCode = calculateHashCode();
    }

    @Override
    @Nullable
    public IdeMavenCoordinates getRequestedCoordinates() {
        throw new UnusedModelMethodException("getRequestedCoordinates");
    }

    @Override
    @NonNull
    public IdeMavenCoordinates getResolvedCoordinates() {
        return myResolvedCoordinates;
    }

    @Nullable
    @Override
    public String getBuildId() {
        return myBuildId;
    }

    @Override
    @Nullable
    public String getProject() {
        return myProject;
    }

    @Override
    @Nullable
    public String getName() {
        return myName;
    }

    @Override
    public boolean isSkipped() {
        if (myIsSkipped != null) {
            return myIsSkipped;
        }
        throw new UnsupportedOperationException("Unsupported method: IdeLibrary.isSkipped()");
    }

    @Override
    public boolean isProvided() {
        if (myProvided != null) {
            return myProvided;
        }
        throw new UnsupportedOperationException("Unsupported method: IdeLibrary.isProvided()");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeLibrary)) {
            return false;
        }
        IdeLibrary library = (IdeLibrary) o;
        return library.canEqual(this)
                && Objects.equals(myIsSkipped, library.myIsSkipped)
                && Objects.equals(myProvided, library.myProvided)
                && Objects.equals(myResolvedCoordinates, library.myResolvedCoordinates)
                && Objects.equals(myBuildId, library.myBuildId)
                && Objects.equals(myProject, library.myProject)
                && Objects.equals(myName, library.myName);
    }

    public boolean canEqual(Object other) {
        // See: http://www.artima.com/lejava/articles/equality.html
        return other instanceof IdeLibrary;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    protected int calculateHashCode() {
        return Objects.hash(
                myResolvedCoordinates, myBuildId, myProject, myName, myProvided, myIsSkipped);
    }

    @Override
    public String toString() {
        return "myResolvedCoordinates="
                + myResolvedCoordinates
                + ", myBuildId='"
                + myBuildId
                + '\''
                + ", myProject='"
                + myProject
                + '\''
                + ", myName='"
                + myName
                + '\''
                + ", myProvided="
                + myProvided;
    }
}
