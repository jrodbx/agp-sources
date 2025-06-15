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

package com.android.ide.common.blame;

import com.android.annotations.NonNull;

/**
 * A message receiver.
 *
 * {@link MessageReceiver}s receive build {@link Message}s and either
 * <ul><li>Output them to a logging system</li>
 * <li>Output them to a user interface</li>
 * <li>Transform them, such as mapping from intermediate files back to source files</li></ul>
 */
public interface MessageReceiver {

    /**
     * Process the given message.
     */
    void receiveMessage(@NonNull Message message);
}
