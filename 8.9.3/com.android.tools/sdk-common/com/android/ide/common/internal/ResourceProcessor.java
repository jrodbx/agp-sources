/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.ide.common.internal;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.process.ProcessOutputHandler;
import com.android.ide.common.resources.CompileResourceRequest;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;

/** An object able to send requests to an AAPT or AAPT2 daemon. */
public interface ResourceProcessor {

    /**
     * Initiates a series of compile requests. The call to this method must be followed by a call to
     * {@link #end(int)} that will allow to wait for all requests made with the {@link #compile(int,
     * CompileResourceRequest, ProcessOutputHandler)} method.
     *
     * @return the key for this set of requests.
     */
    int start();

    /**
     * Crunch a given file into another given file. This may be implemented synchronously or
     * asynchronously. Therefore the output file may not be present until {@link #end(int)} is
     * called and returned. When implemented asynchronously, this act like queueing a crunching
     * request. So this can be called multiple times and when {@link #end(int)} is called and
     * returned, all output files will be present.
     *
     * @param key obtained from the {@link #start()}
     * @param request the compilation request containing the input, output and compilation flags
     * @param processOutputHandler the handler for the output of the compilation
     * @return a {@link ListenableFuture} instance calling code can listen to for completion.
     */
    ListenableFuture<File> compile(
            int key,
            @NonNull CompileResourceRequest request,
            @Nullable ProcessOutputHandler processOutputHandler)
            throws ResourceCompilationException;

    /**
     * Wait until all compile requests have been executed. If there are no other users of this
     * service, it can shutdown all associated thread pools or native resources.
     *
     * @param key obtained in the {@link #start()}
     */
    void end(int key) throws InterruptedException;
}
