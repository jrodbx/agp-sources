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

package com.android.builder.internal.packaging;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.base.Supplier;
import java.util.Locale;

/**
 * Supplier that provides sequential names for dex files.
 */
class DexFileNameSupplier implements Supplier<String> {

    /**
     * Current dex-file index.
     */
    private int mIndex;

    /**
     * Creates a new renamer.
     */
    DexFileNameSupplier() {
        mIndex = 1;
    }

    @Override
    @NonNull
    public String get() {
        String dexFileName;
        if (mIndex == 1) {
            dexFileName = SdkConstants.FN_APK_CLASSES_DEX;
        } else {
            dexFileName = String.format(Locale.US, SdkConstants.FN_APK_CLASSES_N_DEX, mIndex);
        }

        mIndex++;
        return dexFileName;
    }
}
