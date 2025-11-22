/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.merge;

/**
 * Exception thrown when more than one file with the same relative path is found in an incremental
 * input for a merge. For example, if an input has two directories and both have file {@code x},
 * then this exception is thrown.
 *
 * <p>This is different from the case where a file with the same relative path exists in different
 * inputs. That is not an error and is handled by the merger, although some implementations of
 * {@link IncrementalFileMergerOutput} may reject this (for example,
 * {@link StreamMergeAlgorithms#acceptOnlyOne()}.
 */
public class DuplicatePathInIncrementalInputException extends RuntimeException {

    /**
     * Creates a new exception.
     *
     * @param description a description of the exception
     */
    public DuplicatePathInIncrementalInputException(String description) {
        super(description);
    }
}
