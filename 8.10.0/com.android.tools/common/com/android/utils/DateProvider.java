/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.utils;

import java.util.Date;

/**
 * Provides abstraction over @{link Date#Date()}, to allow code to swap out calls to system date by
 * a fake date in tests to create reliable tests.
 */
public interface DateProvider {
    /**
     * Implementation that calls to the real @{link Date#Date()}. This should never be called
     * through directly in code. The right pattern is to create a variable and assign that variable
     * to {@link #SYSTEM} upon initialization and override the variable in tests.
     */
    DateProvider SYSTEM =
            new DateProvider() {
                @Override
                public Date now() {
                    return new Date();
                }
            };

    /**
     * Provides this DataProvider's notion of 'now'.
     */
    Date now();
}
