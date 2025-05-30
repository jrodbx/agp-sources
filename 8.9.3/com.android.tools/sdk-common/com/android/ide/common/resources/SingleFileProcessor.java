/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.io.File;
import javax.xml.bind.JAXBException;

/** An interface for a single file processor. */
public interface SingleFileProcessor {

    /**
     * Processes a single layout file.
     *
     * @param inputFile the input layout file
     * @param outputFile the output layout file after processing
     * @param inputFileIsFromDependency whether the resource comes from a dependency or from the
     *     current subproject, or `null` if this information is not available
     * @return `true` if the input file was processed
     */
    boolean processSingleFile(
            @NonNull File inputFile,
            @NonNull File outputFile,
            @Nullable Boolean inputFileIsFromDependency)
            throws Exception;

    void processRemovedFile(File file);

    /** Processes a layout file which does not contain data binding constructs. */
    void processFileWithNoDataBinding(@NonNull File file);

    void end() throws JAXBException;
}
