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

interface ClassContext {

    /**
     * Contains information about the class that will be instrumented.
     */
    val currentClassData: ClassData

    /**
     * Loads the class data for the class with given [className].
     *
     * Returns null if a class named [className] couldn't be found in the runtime classpath of the
     * class defined by the [currentClassData].
     *
     * @param className the fully qualified name of the class,
     *                  (e.g. "com.android.build.api.instrumentation.ClassContext")
     */
    fun loadClassData(className: String): ClassData?
}
