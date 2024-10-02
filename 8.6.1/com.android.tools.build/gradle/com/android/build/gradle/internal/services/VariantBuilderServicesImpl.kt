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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.services.VariantBuilderServices.Value

class VariantBuilderServicesImpl(
    projectServices: ProjectServices
): BaseServicesImpl(projectServices),
    VariantBuilderServices {

    override fun lockValues() {
        disallowSet = true
        disallowGet = false
    }

    private var disallowGet: Boolean = true
    private var disallowSet: Boolean = false

    override fun <T> valueOf(value: T): Value<T> = ValueImpl<T>(value)

    override val isPostVariantApi: Boolean
        get() = disallowSet

    inner class ValueImpl<T>(private var value: T): Value<T> {

        override fun set(value: T) {
            if (disallowSet) {
                throw RuntimeException("Values of Variant objects are locked.")
            }
            this.value = value
        }

        override fun get(): T {
            if (disallowGet) {
                throw RuntimeException("Values of Variant objects are not readable. Use VariantProperties instead.")
            }
            return this.value
        }
    }
}

