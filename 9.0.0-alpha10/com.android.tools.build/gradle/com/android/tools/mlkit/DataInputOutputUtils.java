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
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class DataInputOutputUtils {

    @NonNull
    public static int[] readIntArray(@NonNull DataInput in) throws IOException {
        int len = in.readInt();
        int[] values = new int[len];
        for (int i = 0; i < len; i++) {
            values[i] = in.readInt();
        }
        return values;
    }

    public static void writeIntArray(@NonNull DataOutput out, @NonNull int[] values)
            throws IOException {
        out.writeInt(values.length);
        for (int value : values) {
            out.writeInt(value);
        }
    }

    @NonNull
    public static float[] readFloatArray(@NonNull DataInput in) throws IOException {
        int len = in.readInt();
        float[] values = new float[len];
        for (int i = 0; i < len; i++) {
            values[i] = in.readFloat();
        }
        return values;
    }

    public static void writeFloatArray(@NonNull DataOutput out, @NonNull float[] values)
            throws IOException {
        out.writeInt(values.length);
        for (float value : values) {
            out.writeFloat(value);
        }
    }

    @NonNull
    public static List<TensorInfo> readTensorInfoList(@NonNull DataInput in) throws IOException {
        List<TensorInfo> tensorInfoList = new ArrayList<>();
        int len = in.readInt();
        for (int i = 0; i < len; i++) {
            tensorInfoList.add(new TensorInfo(in));
        }
        return tensorInfoList;
    }

    public static void writeTensorInfoList(
            @NonNull DataOutput out, @NonNull List<TensorInfo> tensorInfoList) throws IOException {
        out.writeInt(tensorInfoList.size());
        for (TensorInfo tensorInfo : tensorInfoList) {
            tensorInfo.save(out);
        }
    }

    @NonNull
    public static List<TensorGroupInfo> readTensorGroupInfoList(@NonNull DataInput in)
            throws IOException {
        List<TensorGroupInfo> tensorGroupInfoList = new ArrayList<>();
        int len = in.readInt();
        for (int i = 0; i < len; i++) {
            tensorGroupInfoList.add(new TensorGroupInfo(in));
        }
        return tensorGroupInfoList;
    }

    public static void writeTensorGroupInfoList(
            @NonNull DataOutput out, @NonNull List<TensorGroupInfo> tensorInfoList)
            throws IOException {
        out.writeInt(tensorInfoList.size());
        for (TensorGroupInfo tensorGroupInfo : tensorInfoList) {
            tensorGroupInfo.save(out);
        }
    }
}
