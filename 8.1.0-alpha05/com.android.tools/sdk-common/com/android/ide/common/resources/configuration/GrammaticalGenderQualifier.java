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
import com.android.resources.GrammaticalGender;
import com.android.resources.ResourceEnum;

public class GrammaticalGenderQualifier extends EnumBasedResourceQualifier {

    public static final String NAME = "Grammatical Gender";

    @Nullable private GrammaticalGender mValue = null;

    public GrammaticalGenderQualifier() {}

    public GrammaticalGenderQualifier(@Nullable GrammaticalGender value) {
        mValue = value;
    }

    public GrammaticalGender getValue() {
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
        GrammaticalGender enumValue = GrammaticalGender.getEnum(value);
        if (enumValue != null) {
            GrammaticalGenderQualifier qualifier = new GrammaticalGenderQualifier(enumValue);
            config.setGrammaticalGenderQualifier(qualifier);
            return true;
        }

        return false;
    }
}
