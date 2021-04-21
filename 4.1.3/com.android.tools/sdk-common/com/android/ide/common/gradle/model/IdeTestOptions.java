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
import com.android.builder.model.TestOptions;
import java.io.Serializable;
import java.util.Objects;

/** Creates a deep copy of a {@link TestOptions}. */
public class IdeTestOptions implements TestOptions, Serializable {
    private static final long serialVersionUID = 2L;

    private final boolean myAnimationsDisabled;
    @Nullable private final Execution myExecutionEnum;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    IdeTestOptions() {
        myAnimationsDisabled = false;
        myExecutionEnum = null;

        myHashCode = 0;
    }

    public IdeTestOptions(@NonNull TestOptions testOptions) {
        myAnimationsDisabled = testOptions.getAnimationsDisabled();
        myExecutionEnum = testOptions.getExecution();
        myHashCode = calculateHashCode();
    }

    @Override
    public boolean getAnimationsDisabled() {
        return myAnimationsDisabled;
    }

    @Override
    @Nullable
    public Execution getExecution() {
        return myExecutionEnum;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeTestOptions)) {
            return false;
        }
        IdeTestOptions options = (IdeTestOptions) o;
        return myAnimationsDisabled == options.myAnimationsDisabled
                && myExecutionEnum == options.myExecutionEnum;
    }

    @Override
    public final int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myAnimationsDisabled, myExecutionEnum);
    }

    @Override
    public String toString() {
        return "IdeTestOptions{"
                + "myAnimationsDisabled='"
                + myAnimationsDisabled
                + ", myExecutionEnum='"
                + myExecutionEnum
                + "}";
    }
}
