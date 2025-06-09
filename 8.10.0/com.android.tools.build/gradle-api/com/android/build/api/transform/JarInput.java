/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.api.transform;

import com.android.annotations.NonNull;
import java.util.Collection;

/**
 * A {@link QualifiedContent} of type jar.
 * <p>
 * This means the {@link #getFile()} is the jar file containing the content.
 * <p>
 * This also contains the incremental state of the jar file, if the transform is in incremental
 * mode through {@link #getStatus()}.
 * <p>
 * For a transform to run in incremental mode:
 * <ul>
 *     <li>{@link Transform#isIncremental()} must return <code>true</code></li>
 *     <li>The parameter <var>isIncremental</var> of
 *     {@link Transform#transform(Context, Collection, Collection, TransformOutputProvider, boolean)}
 *     must be <code>true</code>.</li>
 * </ul>
 *
 * If the transform is not in incremental mode, {@link #getStatus()} always returns
 * {@link Status#NOTCHANGED}.
 * @deprecated
 */
@Deprecated
public interface JarInput extends QualifiedContent {

    @NonNull
    Status getStatus();
}
