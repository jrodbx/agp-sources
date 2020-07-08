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
import com.android.builder.model.AndroidArtifactOutput;
import java.io.File;
import java.util.Objects;

/** Creates a deep copy of an {@link AndroidArtifactOutput}. */
public final class IdeAndroidArtifactOutput extends IdeVariantOutput
        implements AndroidArtifactOutput {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @Nullable private final File myOutputFile;
    @Nullable private final String myAssembleTaskName;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeAndroidArtifactOutput() {
        super();

        myOutputFile = null;
        myAssembleTaskName = null;

        myHashCode = 0;
    }

    public IdeAndroidArtifactOutput(
            @NonNull AndroidArtifactOutput output, @NonNull ModelCache modelCache) {
        super(output, modelCache);
        String assembleTaskName;
        try {
            //noinspection deprecation
            assembleTaskName = output.getAssembleTaskName();
        } catch (RuntimeException e) {
            assembleTaskName = null;
        }
        myAssembleTaskName = assembleTaskName;
        // Even though getOutputFile is not new, the class hierarchies in builder-model have changed a lot (e.g. new interfaces have been
        // created, and existing methods have been moved around to new interfaces) making Gradle think that this is a new method.
        // When using the plugin v2.4 or older, we fall back to calling getMainOutputFile().getOutputFile(), which is the older plugins
        // do.
        myOutputFile =
                IdeModel.copyNewProperty(
                        output::getOutputFile, output.getMainOutputFile().getOutputFile());

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getAssembleTaskName() {
        if (myAssembleTaskName != null) {
            return myAssembleTaskName;
        }
        throw new RuntimeException("Method 'getAssembleTaskName' is no longer supported");
    }

    @Override
    @NonNull
    public File getGeneratedManifest() {
        throw new UnusedModelMethodException("getGeneratedManifest");
    }

    @Override
    @NonNull
    public File getOutputFile() {
        if (myOutputFile != null) {
            return myOutputFile;
        }
        throw new UnsupportedOperationException("getOutputFile");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeAndroidArtifactOutput)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        IdeAndroidArtifactOutput output = (IdeAndroidArtifactOutput) o;
        return output.canEquals(this)
                && Objects.equals(myAssembleTaskName, output.myAssembleTaskName)
                && Objects.equals(myOutputFile, output.myOutputFile);
    }

    @Override
    protected boolean canEquals(Object other) {
        return other instanceof IdeAndroidArtifactOutput;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    @Override
    protected int calculateHashCode() {
        return Objects.hash(super.calculateHashCode(), myAssembleTaskName, myOutputFile);
    }

    @Override
    public String toString() {
        return "IdeAndroidArtifactOutput{"
                + super.toString()
                + ", myAssembleTaskName='"
                + myAssembleTaskName
                + '\''
                + ", myOutputFile="
                + myOutputFile
                + "}";
    }
}
