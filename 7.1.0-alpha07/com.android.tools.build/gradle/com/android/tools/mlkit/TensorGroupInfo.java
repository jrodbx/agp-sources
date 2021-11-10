/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.mlkit;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Strings;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.tensorflow.lite.support.metadata.schema.TensorGroup;

/**
 * Information of a list of tensors that need to be grouped together. In codegen it means to create
 * an individual data class for them.
 */
public class TensorGroupInfo {
    @NonNull private final String name;
    @NonNull private final String identifierName;
    @NonNull private final List<String> tensorNames;

    public TensorGroupInfo(TensorGroup tensorGroup) {
        name = Strings.nullToEmpty(tensorGroup.name());
        identifierName = MlNames.computeIdentifierName(name, "");
        int len = tensorGroup.tensorNamesLength();
        tensorNames = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            tensorNames.add(tensorGroup.tensorNames(i));
        }
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public List<String> getTensorNames() {
        return tensorNames;
    }

    @NonNull
    public String getIdentifierName() {
        return identifierName;
    }

    public TensorGroupInfo(@NonNull DataInput in) throws IOException {
        name = in.readUTF();
        identifierName = in.readUTF();
        int length = in.readInt();
        tensorNames = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            tensorNames.add(in.readUTF());
        }
    }

    public void save(@NonNull DataOutput out) throws IOException {
        out.writeUTF(name);
        out.writeUTF(identifierName);
        out.writeInt(tensorNames.size());
        for (String tensorName : tensorNames) {
            out.writeUTF(tensorName);
        }
    }

    @SuppressWarnings("EqualsHashCode")  // b/180537631
    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TensorGroupInfo that = (TensorGroupInfo) o;
        return name.equals(that.name)
                && identifierName.equals(that.identifierName)
                && tensorNames.equals(that.tensorNames);
    }
}
