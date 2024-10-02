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
package com.android.ide.common.resources;

import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link Map} that treats all the keys as resources names. This class takes care of the key
 * flattening done by AAPT where '.', '-' and ':' are all replaced with '_' and will be able to find
 * resources with those characters in the name no matter the format. Because of that, for {@link
 * ResourceNameKeyedMap} keys like 'my.key', 'my_key' and 'my-key' are exactly the same key.
 *
 * <p>{@link ResourceNameKeyedMap} will keep the original names given to the resources so calls to
 * {@link #keySet()} will return the names without modifications. Note that the set returned from
 * {@link #keySet()} uses the same strategy for normalizing strings, so it will equate resource
 * names that normalize to the same string in calls to {@link java.util.Set#contains(Object)} etc.
 */
public class ResourceNameKeyedMap<T> extends THashMap<String, T> {

    public ResourceNameKeyedMap() {
        super(NORMALIZED_RESOURCE_NAME_STRATEGY);
    }

    public ResourceNameKeyedMap(int expectedSize) {
        super(expectedSize, NORMALIZED_RESOURCE_NAME_STRATEGY);
    }

    private static TObjectHashingStrategy<String> NORMALIZED_RESOURCE_NAME_STRATEGY =
            new TObjectHashingStrategy<String>() {
                @Override
                public int computeHashCode(@NotNull String object) {
                    int result = 0;
                    for (int i = 0; i < object.length(); i++) {
                        result = result * 31 + normalize(object.charAt(i));
                    }

                    return result;
                }

                @Override
                public boolean equals(@NotNull String o1, @NotNull String o2) {
                    if (o1.length() != o2.length()) return false;

                    for (int i = o1.length() - 1; i >= 0; i--) {
                        char c1 = normalize(o1.charAt(i));
                        char c2 = normalize(o2.charAt(i));
                        if (c1 != c2) return false;
                    }

                    return true;
                }

                private char normalize(char c) {
                    if (ResourcesUtil.isInvalidResourceFieldNameCharacter(c)) {
                        return '_';
                    } else {
                        return c;
                    }
                }
            };
}
