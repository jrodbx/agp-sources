/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

public interface StyleItemResourceValue extends ResourceValue {
    /**
     * Returns contents of the {@code name} XML attribute that defined this style item. This is
     * supposed to be a reference to an {@code attr} resource.
     */
    @NonNull
    String getAttrName();

    /**
     * Returns a {@link ResourceReference} to the {@code attr} resource this item is defined for, if
     * the name was specified using the correct syntax.
     */
    @Nullable
    ResourceReference getAttr();

    /**
     * Returns just the name part of the attribute being referenced, for backwards compatibility
     * with layoutlib. Don't call this method, the item may be in a different namespace than the
     * attribute and the value being referenced, use {@link #getAttr()} instead.
     *
     * @deprecated Use {@link #getAttr()} instead.
     */
    @Deprecated
    @Override
    @NonNull
    String getName();
}
