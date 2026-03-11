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
package com.android.ddmlib;

import com.android.annotations.NonNull;

/**
 * This represents a single profilealbe client, usually an Android VM process.
 *
 * <p>This class gives access to basic client information.
 *
 * <p>More detailed information, usually updated in real time, can be access through the {@link
 * ProfileableClientData} class. Each <code>ProfileableClient</code> object has its own <code>
 * ProfileableClientData</code> accessed through {@link #getProfileableClientData()}.
 */
public interface ProfileableClient {
    /** Returns the {@link ProfileableClientData} object containing this client information. */
    @NonNull
    ProfileableClientData getProfileableClientData();
}
