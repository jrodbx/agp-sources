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
package com.android.ide.common.gradle.model.level2;

import com.android.annotations.NonNull;
import com.android.builder.model.Dependencies;
import com.android.builder.model.level2.DependencyGraphs;
import com.android.builder.model.level2.Library;
import java.io.File;
import java.util.Collection;

/**
 * Unified API for L1 ( {@link Dependencies} in pre-3.0 models) and L4 ({@link DependencyGraphs} in
 * 3.0+ models) dependencies.
 */
public interface IdeDependencies {
    /**
     * Returns the Android library dependencies, both direct and transitive.
     *
     * @return the list of libraries of type LIBRARY_ANDROID.
     */
    @NonNull
    Collection<Library> getAndroidLibraries();

    /**
     * Returns the Java library dependencies, both direct and transitive dependencies.
     *
     * @return the list of libraries of type LIBRARY_JAVA.
     */
    @NonNull
    Collection<Library> getJavaLibraries();

    /**
     * Returns the Module dependencies.
     *
     * @return the list of libraries of type LIBRARY_MODULE.
     */
    @NonNull
    Collection<Library> getModuleDependencies();

    /**
     * Returns the list of runtime only classes.
     *
     * @return the list of runtime only classes.
     */
    @NonNull
    Collection<File> getRuntimeOnlyClasses();
}
