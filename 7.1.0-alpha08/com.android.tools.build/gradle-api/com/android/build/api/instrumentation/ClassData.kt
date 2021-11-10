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

package com.android.build.api.instrumentation

/**
 * Data about the class to be instrumented.
 */
interface ClassData {
    /**
     * Fully qualified name of the class.
     */
    val className: String

    /**
     * List of the annotations the class has.
     */
    val classAnnotations: List<String>

    /**
     * List of all the interfaces that this class or a superclass of this class implements.
     */
    val interfaces: List<String>

    /**
     * List of all the super classes that this class or a super class of this class extends.
     */
    val superClasses: List<String>
}
