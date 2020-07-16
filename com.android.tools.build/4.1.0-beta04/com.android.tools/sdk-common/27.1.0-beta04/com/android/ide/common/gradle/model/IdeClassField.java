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
import com.android.builder.model.ClassField;
import com.google.common.annotations.VisibleForTesting;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/** Creates a deep copy of a {@link ClassField}. */
public final class IdeClassField implements ClassField, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 2L;

    @NonNull private final String myName;
    @NonNull private final String myType;
    @NonNull private final String myValue;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @VisibleForTesting
    @SuppressWarnings("unused")
    public IdeClassField() {
        myName = "";
        myType = "";
        myValue = "";

        myHashCode = 0;
    }

    public IdeClassField(@NonNull ClassField classField) {
        myName = classField.getName();
        myType = classField.getType();
        myValue = classField.getValue();

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getType() {
        return myType;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getValue() {
        return myValue;
    }

    @Override
    @NonNull
    public String getDocumentation() {
        throw new UnusedModelMethodException("getDocumentation");
    }

    @Override
    @NonNull
    public Set<String> getAnnotations() {
        throw new UnusedModelMethodException("getAnnotations");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeClassField)) {
            return false;
        }
        IdeClassField field = (IdeClassField) o;
        return Objects.equals(myName, field.myName)
                && Objects.equals(myType, field.myType)
                && Objects.equals(myValue, field.myValue);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myName, myType, myValue);
    }

    @Override
    public String toString() {
        return "IdeClassField{"
                + "myName='"
                + myName
                + '\''
                + ", myType='"
                + myType
                + '\''
                + ", myValue='"
                + myValue
                + '\''
                + '}';
    }
}
