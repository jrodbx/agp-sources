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

import static com.android.utils.ImmutableCollectors.toImmutableList;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

/** Creates a deep copy of an {@link OutputFile}. */
public final class IdeOutputFileImpl implements IdeOutputFile, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myOutputType;
    @NonNull private final Collection<String> myFilterTypes;
    @NonNull private final Collection<FilterData> myFilters;
    @NonNull private final File myOutputFile;
    @NonNull private final Collection<? extends OutputFile> myOutputs;
    @Nullable private final OutputFile myMainOutputFile;
    @Nullable private final Integer myVersionCode;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeOutputFileImpl() {
        myOutputType = "";
        myFilterTypes = Collections.emptyList();
        myFilters = Collections.emptyList();
        //noinspection ConstantConditions
        myOutputFile = null;
        myOutputs = Collections.emptyList();
        myMainOutputFile = null;
        myVersionCode = null;

        myHashCode = 0;
    }

    public IdeOutputFileImpl(
            @NonNull String outputType,
            @NonNull Collection<String> filterTypes,
            @NonNull Collection<FilterData> filters,
            @NonNull File outputFile,
            @NonNull Collection<? extends OutputFile> outputs,
            @Nullable OutputFile mainOutputFile,
            @Nullable Integer versionCode) {
        myOutputType = outputType;
        myFilterTypes = filterTypes;
        myFilters = filters;
        myOutputFile = outputFile;
        myOutputs = outputs;
        myMainOutputFile = mainOutputFile;
        myVersionCode = versionCode;
        myHashCode = calculateHashCode();
    }

    public IdeOutputFileImpl(@NonNull OutputFile file, @NonNull ModelCache modelCache) {
        myOutputType = file.getOutputType();
        myFilterTypes = ImmutableList.copyOf(file.getFilterTypes());
        myFilters =
                IdeModel.copy(file.getFilters(), modelCache, data -> new IdeFilterDataImpl(data));
        myOutputFile = file.getOutputFile();
        myMainOutputFile = copyMainOutputFile(file, modelCache);
        //noinspection deprecation
        myOutputs = copyOutputs(file, modelCache);
        myVersionCode = IdeModel.copyNewProperty(file::getVersionCode, null);

        myHashCode = calculateHashCode();
    }

    @Nullable
    private IdeOutputFileImpl copyMainOutputFile(
            @NonNull OutputFile file, @NonNull ModelCache modelCache) {
        try {
            if (file == file.getMainOutputFile()) {
                return this;
            }
        } catch (UnsupportedOperationException ignored) {
            // getMainOutputFile is supported in AGP 3.0+.
        }
        return IdeModel.copyNewProperty(
                modelCache,
                file::getMainOutputFile,
                outputFile -> new IdeOutputFileImpl(outputFile, modelCache),
                null);
    }

    @NonNull
    private Collection<? extends OutputFile> copyOutputs(
            @NonNull OutputFile file, @NonNull ModelCache modelCache) {
        try {
            //noinspection deprecation
            return file.getOutputs().stream()
                    .map(
                            outputFile ->
                                    outputFile == file
                                            ? this
                                            : new IdeOutputFileImpl(outputFile, modelCache))
                    .collect(toImmutableList());
        } catch (UnsupportedOperationException ignored) {
            return Collections.emptyList();
        }
    }

    @Override
    @NonNull
    public String getOutputType() {
        return myOutputType;
    }

    @Override
    @NonNull
    public Collection<String> getFilterTypes() {
        return myFilterTypes;
    }

    @Override
    @NonNull
    public Collection<FilterData> getFilters() {
        return myFilters;
    }

    @Override
    @NonNull
    public File getOutputFile() {
        return myOutputFile;
    }

    @Override
    @NonNull
    public OutputFile getMainOutputFile() {
        if (myMainOutputFile != null) {
            return myMainOutputFile;
        }
        throw new UnsupportedOperationException("getMainOutputFile()");
    }

    @Override
    @NonNull
    public Collection<? extends OutputFile> getOutputs() {
        return myOutputs;
    }

    @Override
    public int getVersionCode() {
        if (myVersionCode != null) {
            return myVersionCode;
        }
        throw new UnsupportedOperationException("getVersionCode");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeOutputFileImpl)) {
            return false;
        }

        IdeOutputFileImpl file = (IdeOutputFileImpl) o;
        return Objects.equals(myVersionCode, file.myVersionCode)
                && Objects.equals(myOutputType, file.myOutputType)
                && Objects.equals(myFilterTypes, file.myFilterTypes)
                && Objects.equals(myFilters, file.myFilters)
                && Objects.equals(myOutputFile, file.myOutputFile)
                && areOutputsEqual(file)
                && mainOutputFileEquals(file);
    }

    private boolean areOutputsEqual(@NonNull IdeOutputFileImpl other) {
        if (myOutputs == other.myOutputs) {
            return true;
        }
        Iterator<? extends OutputFile> iterator1 = myOutputs.iterator();
        Iterator<? extends OutputFile> iterator2 = other.myOutputs.iterator();
        while (iterator1.hasNext()) {
            if (!iterator2.hasNext()) {
                return false;
            }
            Object o1 = iterator1.next();
            Object o2 = iterator2.next();
            if (o1 == this) {
                if (o2 != other) {
                    return false;
                }
                continue;
            }
            if (!Objects.equals(o1, o2)) {
                return false;
            }
        }
        return !iterator2.hasNext();
    }

    private boolean mainOutputFileEquals(@NonNull IdeOutputFileImpl file) {
        // Avoid stack overflow.
        return myMainOutputFile == this
                ? file.myMainOutputFile == file
                : Objects.equals(myMainOutputFile, file.myMainOutputFile);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        int result = myOutputType.hashCode();
        result = 31 * result + myFilterTypes.hashCode();
        result = 31 * result + myFilters.hashCode();
        result = 31 * result + hashCode(myOutputFile);
        result = 31 * result + hashCode(myMainOutputFile);
        result = 31 * result + hashCode(myOutputs);
        result = 31 * result + Objects.hashCode(myVersionCode);
        return result;
    }

    private int hashCode(@NonNull Collection<? extends OutputFile> outputFiles) {
        int hashCode = 1;
        for (OutputFile outputFile : outputFiles) {
            hashCode = 31 * hashCode + hashCode(outputFile);
        }
        return hashCode;
    }

    private <T> int hashCode(@Nullable T obj) {
        return obj != this ? Objects.hashCode(obj) : 1;
    }

    @Override
    public String toString() {
        return "IdeOutputFile{"
                + "myOutputType='"
                + myOutputType
                + '\''
                + ", myFilterTypes="
                + myFilterTypes
                + ", myFilters="
                + myFilters
                + ", myOutputFile="
                + myOutputFile
                + ", myMainOutputFile="
                + toString(myMainOutputFile)
                + // Avoid stack overflow.
                ", myOutputs="
                + toString(myOutputs)
                + ", myVersionCode="
                + myVersionCode
                + "}";
    }

    @NonNull
    private String toString(@NonNull Collection<? extends OutputFile> outputFiles) {
        int max = outputFiles.size() - 1;
        if (max == -1) {
            return "[]";
        }

        StringBuilder b = new StringBuilder();
        b.append('[');
        int i = 0;
        for (OutputFile file : outputFiles) {
            b.append(toString(file));
            if (i++ == max) {
                b.append(']');
                break;
            }
            b.append(", ");
        }
        return b.toString();
    }

    @NonNull
    private String toString(@Nullable OutputFile outputFile) {
        if (outputFile == this) {
            return "this";
        }
        return Objects.toString(outputFile);
    }
}
