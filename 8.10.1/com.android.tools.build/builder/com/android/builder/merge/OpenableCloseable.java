/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.builder.merge;

import java.io.Closeable;

/**
 * Specifies the general contract for an object that needs to be open or closed. An
 * openable/closeable object has two states: open and closed. {@link #open()} moves from closed to
 * open and {@link #close()} from open to close. What operations can be performed in each state is
 * not specified by this interface, only the state machine.
 *
 * <p>Openable / closeable objects are always initialized as closed.
 */
public interface OpenableCloseable extends Closeable {

    /**
     * Opens the object.
     */
    void open();
}
