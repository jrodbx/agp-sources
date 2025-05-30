/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android;

/**
 * Constants used by the Android Gradle model and Android IntelliJ facet to store the type of a
 * module/project.
 */
public class AndroidProjectTypes {
    /** App module. */
    public static final int PROJECT_TYPE_APP = 0;

    /** Library module. */
    public static final int PROJECT_TYPE_LIBRARY = 1;

    /** Test-only module. */
    public static final int PROJECT_TYPE_TEST = 2;

    @Deprecated public static final int PROJECT_TYPE_ATOM = 3;

    /** Instant App Bundle. */
    public static final int PROJECT_TYPE_INSTANTAPP = 4;

    /** com.android.feature module. */
    public static final int PROJECT_TYPE_FEATURE = 5;

    /** com.android.dynamic-feature module. */
    public static final int PROJECT_TYPE_DYNAMIC_FEATURE = 6;
}
