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

package com.android.ide.common.resources.configuration;

import com.android.annotations.Nullable;
import com.android.resources.ResourceEnum;
import com.android.resources.WideGamutColor;

public class WideGamutColorQualifier extends EnumBasedResourceQualifier {

    public static final String NAME = "Color Gamut";

    @Nullable private WideGamutColor mValue = null;

    public WideGamutColorQualifier() {}

    public WideGamutColorQualifier(@Nullable WideGamutColor value) {
        mValue = value;
    }

    public WideGamutColor getValue() {
        return mValue;
    }

    @Override
    public ResourceEnum getEnumValue() {
        return mValue;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getShortName() {
        return NAME;
    }

    @Override
    public int since() {
        return 26;
    }

    @Override
    public boolean checkAndSet(String value, FolderConfiguration config) {
        WideGamutColor enumValue = WideGamutColor.getEnum(value);
        if (enumValue != null) {
            WideGamutColorQualifier qualifier = new WideGamutColorQualifier(enumValue);
            config.setWideColorGamutQualifier(qualifier);
            return true;
        }

        return false;
    }
}
