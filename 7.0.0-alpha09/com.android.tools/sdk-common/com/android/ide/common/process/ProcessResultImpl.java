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

package com.android.ide.common.process;

import com.android.annotations.NonNull;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Internal implementation of ProcessResult used by DefaultProcessExecutor.
 */
class ProcessResultImpl implements ProcessResult {

    @NonNull
    private final ImmutableList<String> mCommand;
    private final int mExitValue;
    private final Throwable mFailure;

    ProcessResultImpl(List<String> command, int exitValue) {
        this(command, exitValue, null);
    }

    ProcessResultImpl(List<String> command, Throwable failure) {
        this(command, -1, failure);
    }

    ProcessResultImpl(List<String> command, int exitValue, Throwable failure) {
        mCommand = ImmutableList.copyOf(command);
        mExitValue = exitValue;
        mFailure = failure;
    }

    @NonNull
    @Override
    public ProcessResult assertNormalExitValue() throws ProcessException {
        if (mExitValue != 0) {
            throw new ProcessException(
                    String.format(
                            "Return code %d for process '%s'",
                            mExitValue,
                            Joiner.on(" ").join(mCommand)));
        }

        return this;
    }

    @Override
    public int getExitValue() {
        return mExitValue;
    }

    @NonNull
    @Override
    public ProcessResult rethrowFailure() throws ProcessException {
        if (mFailure != null) {
            throw new ProcessException("", mFailure);
        }

        return this;
    }
}
