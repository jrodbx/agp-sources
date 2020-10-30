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
package com.android.projectmodel

/**
 * Type of paths that are referred to in an Android project.
 */
enum class AndroidPathType {
    /**
     * Aidl source folder.
     */
    AIDL,
    /**
     * Directory containing an Android assets folder.
     */
    ASSETS,
    /**
     * Directory containing C source files.
     */
    C,
    /**
     * Proguard file to be used when building artifacts that consume libraries belonging to this [Config].
     */
    CONSUMER_PROGUARD_FILE,
    /**
     * Directory containing CPP source files.
     */
    CPP,
    /**
     * Directory containing a valid Java source root. (Subfolders are organized by package, according to the
     * Java spec).
     */
    JAVA,
    /**
     * Directory containing native libs.
     */
    JNI_LIBS,
    /**
     * Android manifest to be merged into Android artifacts belonging to this [Config].
     */
    MANIFEST,
    /**
     * Proguard file to be used when building artifacts belonging to this [Config].
     */
    PROGUARD_FILE,
    /**
     * Renderscript source folder.
     */
    RENDERSCRIPT,
    /**
     * Android resource folder.
     */
    RES,
    /**
     * Java resources folder.
     */
    RESOURCE,
    /**
     * A shader folder.
     */
    SHADERS
}
