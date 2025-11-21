/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.core;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * Enum of some well-known ABI values. This list may not be complete because the primary source that
 * defines ABIs in the meta/abis.json file. When this was written, support was being added for
 * 'riscv64' ABI which is not included in this list.
 *
 * <p>The only ABIs that must be in this list are ABIs that existed in NDKs that predate the
 * addition of meta/abis.json. The 'riscv64' ABI is being introduced around NDK r26 which does have
 * meta/abis.json in it.
 */
public enum Abi {
    ARMEABI(SdkConstants.ABI_ARMEABI),
    ARMEABI_V7A(SdkConstants.ABI_ARMEABI_V7A),
    ARM64_V8A(SdkConstants.ABI_ARM64_V8A),
    X86(SdkConstants.ABI_INTEL_ATOM),
    X86_64(SdkConstants.ABI_INTEL_ATOM64),
    MIPS(SdkConstants.ABI_MIPS),
    MIPS64(SdkConstants.ABI_MIPS64),
    RISCV64(SdkConstants.ABI_RISCV64);

    @NonNull
    private final String name;

    /**
     * Constructor used for ABIs that exist in NDKs old enough that they didn't have meta/abis.json.
     */
    Abi(@NonNull String name) {
        this.name = name;
    }

    /**
     * Returns the ABI Enum with the specified name.
     */
    @Nullable
    public static Abi getByName(@NonNull String name) {
        for (Abi abi : values()) {
            if (abi.name.equals(name)) {
                return abi;
            }
        }
        return null;
    }

    /**
     * Returns name of the ABI like "armeabi-v7a". Not called getName(...) because that conflicts
     * confusingly with Kotlin's Enum::name.
     */
    @NonNull
    public String getTag() {
        return name;
    }
}

