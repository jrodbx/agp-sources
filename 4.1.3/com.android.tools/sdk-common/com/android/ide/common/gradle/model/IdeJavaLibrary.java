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
import com.android.builder.model.JavaLibrary;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Creates a deep copy of a {@link JavaLibrary}. */
public final class IdeJavaLibrary extends IdeLibrary implements JavaLibrary {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final File myJarFile;
    @NonNull private final List<? extends JavaLibrary> myDependencies;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeJavaLibrary() {
        //noinspection ConstantConditions
        myJarFile = null;
        myDependencies = Collections.emptyList();

        myHashCode = 0;
    }

    public IdeJavaLibrary(@NonNull JavaLibrary library, @NonNull ModelCache modelCache) {
        super(library, modelCache);
        myJarFile = library.getJarFile();
        myDependencies =
                IdeModel.copy(
                        library.getDependencies(),
                        modelCache,
                        dependency -> new IdeJavaLibrary(dependency, modelCache));

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public File getJarFile() {
        return myJarFile;
    }

    @Override
    @NonNull
    public List<? extends JavaLibrary> getDependencies() {
        return myDependencies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeJavaLibrary)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        IdeJavaLibrary library = (IdeJavaLibrary) o;
        return library.canEqual(this)
                && Objects.equals(myJarFile, library.myJarFile)
                && Objects.equals(myDependencies, library.myDependencies);
    }

    @Override
    public boolean canEqual(Object other) {
        return other instanceof IdeJavaLibrary;
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    @Override
    protected int calculateHashCode() {
        return Objects.hash(super.calculateHashCode(), myJarFile, myDependencies);
    }

    @Override
    public String toString() {
        return "IdeJavaLibrary{"
                + super.toString()
                + ", myJarFile="
                + myJarFile
                + ", myDependencies="
                + myDependencies
                + "}";
    }
}
