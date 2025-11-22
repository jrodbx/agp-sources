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

package com.android.build.gradle.internal.instrumentation

import com.android.build.api.instrumentation.ClassData

data class ClassDataImpl(
    override val className: String,
    override val classAnnotations: List<String>,
    override val interfaces: List<String>,
    override val superClasses: List<String>
) : ClassData

class ClassDataLazyImpl(
    override val className: String,
    private val classAnnotationsSupplier: () -> List<String>,
    private val interfacesSupplier: () -> List<String>,
    private val superClassesSupplier: () -> List<String>,
) : ClassData {

    override val classAnnotations: List<String>
        get() = classAnnotationsSupplier.invoke()
    override val interfaces: List<String>
        get() = interfacesSupplier.invoke()
    override val superClasses: List<String>
        get() = superClassesSupplier.invoke()
}
