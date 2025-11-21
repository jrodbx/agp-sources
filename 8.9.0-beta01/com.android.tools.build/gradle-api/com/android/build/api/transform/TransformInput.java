/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.api.transform;

import com.android.annotations.NonNull;
import java.util.Collection;

/**
 * The input to a Transform.
 * <p>
 * It is mostly composed of a list of {@link JarInput} and a list of {@link DirectoryInput}.
 * @deprecated
 */
@Deprecated
public interface TransformInput {

    /**
     * Returns a collection of {@link JarInput}.
     */
    @NonNull
    Collection<JarInput> getJarInputs();

    /**
     * Returns a collection of {@link DirectoryInput}.
     */
    @NonNull
    Collection<DirectoryInput> getDirectoryInputs();
}
