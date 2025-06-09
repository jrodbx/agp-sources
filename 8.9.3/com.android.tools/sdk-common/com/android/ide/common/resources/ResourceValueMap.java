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
package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceValue;

/** A {@link ResourceNameKeyedMap} that stores {@link ResourceValue}s as values. */
public class ResourceValueMap extends ResourceNameKeyedMap<ResourceValue> {
    private ResourceValueMap(int expectedSize) {
        super(expectedSize);
    }

    private ResourceValueMap() {
        super();
    }

    @NonNull
    public static ResourceValueMap createWithExpectedSize(int expectedSize) {
        return new ResourceValueMap(expectedSize);
    }

    @NonNull
    public static ResourceValueMap create() {
        return new ResourceValueMap();
    }

    public void put(@NonNull ResourceValue resourceValue) {
        put(resourceValue.getName(), resourceValue);
    }
}
