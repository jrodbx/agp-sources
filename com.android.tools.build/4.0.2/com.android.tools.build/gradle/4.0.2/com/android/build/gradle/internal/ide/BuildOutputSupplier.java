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

package com.android.build.gradle.internal.ide;

import java.io.File;
import java.io.Serializable;
import java.util.function.Supplier;

/** Specialized version of {@link Supplier} that's serializable */
public interface BuildOutputSupplier<T> extends Supplier<T>, Serializable {

    default File guessOutputFile(String relativeFileName) {
        return new File(relativeFileName);
    }

    static <U> BuildOutputSupplier<U> of(U value) {
        return (BuildOutputSupplier<U>) () -> value;
    }
}
