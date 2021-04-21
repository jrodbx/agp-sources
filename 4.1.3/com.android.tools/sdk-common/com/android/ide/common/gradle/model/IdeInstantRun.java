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
import com.android.builder.model.InstantRun;
import java.io.File;
import java.io.Serializable;
import java.util.Objects;

/** Creates a deep copy of an {@link InstantRun}. */
public final class IdeInstantRun implements InstantRun, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final File myInfoFile;
    private final boolean mySupportedByArtifact;
    private final int mySupportStatus;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeInstantRun() {
        //noinspection ConstantConditions
        myInfoFile = null;
        mySupportedByArtifact = false;
        mySupportStatus = 0;

        myHashCode = 0;
    }

    public IdeInstantRun(@NonNull InstantRun run) {
        myInfoFile = run.getInfoFile();
        mySupportedByArtifact = run.isSupportedByArtifact();
        mySupportStatus = run.getSupportStatus();

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public File getInfoFile() {
        return myInfoFile;
    }

    @Override
    public boolean isSupportedByArtifact() {
        return mySupportedByArtifact;
    }

    @Override
    public int getSupportStatus() {
        return mySupportStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeInstantRun)) {
            return false;
        }
        IdeInstantRun run = (IdeInstantRun) o;
        return mySupportedByArtifact == run.mySupportedByArtifact
                && mySupportStatus == run.mySupportStatus
                && Objects.equals(myInfoFile, run.myInfoFile);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myInfoFile, mySupportedByArtifact, mySupportStatus);
    }

    @Override
    public String toString() {
        return "IdeInstantRun{"
                + "myInfoFile="
                + myInfoFile
                + ", mySupportedByArtifact="
                + mySupportedByArtifact
                + ", mySupportStatus="
                + mySupportStatus
                + "}";
    }
}
