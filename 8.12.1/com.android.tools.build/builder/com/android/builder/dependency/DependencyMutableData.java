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

package com.android.builder.dependency;

import com.google.common.base.MoreObjects;

/**
 * Mutable data for an Android dependency.
 */
public class DependencyMutableData {
    // TODO skip in two different classes since different graph use the booleans. No graph use both.

    private boolean isSkipped = false;
    private boolean isProvided = false;

    public boolean isSkipped() {
        return isSkipped;
    }

    public void skip() {
        isSkipped = true;
    }

    public boolean isProvided() {
        return isProvided;
    }

    public void setProvided(boolean provided) {
        isProvided = provided;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("isSkipped", isSkipped)
                .add("isProvided", isProvided)
                .toString();
    }
}
