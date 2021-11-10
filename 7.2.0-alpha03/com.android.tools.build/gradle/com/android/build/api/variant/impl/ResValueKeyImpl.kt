/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.variant.impl

import com.android.build.api.variant.ResValue

data class ResValueKeyImpl(
    override val type: String,
    override val name: String
): ResValue.Key {

    /**
     * As [com.android.builder.model.BaseConfig.resValues] map has a string key, this method is used
     * to convert the resValue key into a string representation to be used in the model to avoid
     * changing the method signature.
     */
    override fun toString(): String {
        return "$type/$name"
    }
}
