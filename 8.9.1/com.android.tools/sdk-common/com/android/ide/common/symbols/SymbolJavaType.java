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

package com.android.ide.common.symbols;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableMap;

public enum SymbolJavaType {
    INT("int", "I"),
    INT_LIST("int[]", "[I"),
    ;

    private static final ImmutableMap<String, SymbolJavaType> types;

    static {
        ImmutableMap.Builder<String, SymbolJavaType> typesBuilder = ImmutableMap.builder();
        for (SymbolJavaType symbolJavaType : SymbolJavaType.values()) {
            typesBuilder.put(symbolJavaType.getTypeName(), symbolJavaType);
        }
        types = typesBuilder.build();
    }

    @NonNull private final String typeName;
    @NonNull private final String desc;

    SymbolJavaType(@NonNull String typeName, @NonNull String desc) {
        this.typeName = typeName;
        this.desc = desc;
    }

    @NonNull
    public final String getTypeName() {
        return typeName;
    }

    @NonNull
    public String getDesc() {
        return desc;
    }

    @Nullable
    public static SymbolJavaType getEnum(@NonNull String name) {
        return types.get(name);
    }
}
