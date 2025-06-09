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

package com.android.builder.internal.aapt;


/**
 * Exception thrown when there is a problem using {@code aapt}.
 */
public class AaptException extends Exception {

    /**
     * Creates a new exception.
     *
     * @param message the exception's message
     */
    public AaptException(String message) {
        super(message);
    }

    /**
     * Creates a new exception.
     *
     * @param message the exception's message
     * @param cause the cause of this exception
     */
    public AaptException(String message, Throwable cause) {
        super(message, cause);
    }
}
