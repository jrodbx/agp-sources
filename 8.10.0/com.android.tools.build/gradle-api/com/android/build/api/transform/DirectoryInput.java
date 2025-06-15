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
import java.io.File;
import java.util.Collection;
import java.util.Map;

/**
 * A {@link QualifiedContent} of type directory.
 * <p>
 * This means the {@link #getFile()} is the root directory containing the content.
 * <p>
 * This also contains incremental data if the transform is in incremental mode through
 * {@link #getChangedFiles()}.
 * <p>
 * For a transform to run in incremental mode:
 * <ul>
 *     <li>{@link Transform#isIncremental()} must return <code>true</code></li>
 *     <li>The parameter <var>isIncremental</var> of
 *     {@link Transform#transform(Context, Collection, Collection, TransformOutputProvider, boolean)}
 *     must be <code>true</code>.</li>
 * </ul>
 *
 * If the transform is not in incremental mode, {@link #getChangedFiles()} will not contain any
 * information (it will <strong>not</strong> contain the list of all the files with state
 * {@link Status#NOTCHANGED}.)
 *
 * <p>
 * When a root level directory is removed, and incremental mode is on, the instance will receive
 * a {@link DirectoryInput} instance for the removed folder, but {@link QualifiedContent#getFile()}
 * will return a directory that does not exist. In this case, the transform should prcess this
 * as a removed input.
 * @deprecated
 */
@Deprecated
public interface DirectoryInput extends QualifiedContent {

    /**
     * Returns the changed files. This is only valid if the transform is in incremental mode.
     */
    @NonNull
    Map<File, Status> getChangedFiles();
}
