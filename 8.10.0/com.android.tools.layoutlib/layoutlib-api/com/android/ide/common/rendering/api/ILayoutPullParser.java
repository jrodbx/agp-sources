/*
 * Copyright (C) 2008 The Android Open Source Project
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
import org.xmlpull.v1.XmlPullParser;

/**
 * Extended version of {@link XmlPullParser} to use with
 * {@link Bridge#createSession(SessionParams)}
 */
public interface ILayoutPullParser extends XmlPullParser {

    /**
     * Returns a cookie for the current XML node.
     *
     * <p>This cookie will be passed back in the {@link ViewInfo} objects, allowing association of a
     * particular XML node with its result from the layout computation.
     *
     * @see ViewInfo#getCookie()
     */
    @Nullable
    Object getViewCookie();

    /**
     * Returns the aapt {@link ResourceNamespace} of the layout being parsed, that is of the module
     * from which this layout comes from.
     *
     * <p>This namespace is used to resolve "relative" resource references within the layout, that
     * is strings like {@code @string/foo}, which don't explicitly specify the namespace of {@code
     * foo}.
     */
    @NonNull
    ResourceNamespace getLayoutNamespace();
}

