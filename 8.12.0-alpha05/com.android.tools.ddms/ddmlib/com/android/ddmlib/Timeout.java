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

public class Timeout {
    private long deadline;

    public Timeout(long delayMs) {
        deadline = System.currentTimeMillis() + delayMs;
    }

    /**
     * Returns the number of ms remaining until deadline. Returns 0 if the deadline has been
     * crossed.
     */
    public long remaining() {
        long now = System.currentTimeMillis();
        long diff = deadline - now;
        return Math.max(diff, 0);
    }
}
