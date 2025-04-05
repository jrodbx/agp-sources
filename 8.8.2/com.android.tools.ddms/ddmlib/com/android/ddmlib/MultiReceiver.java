/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Base implementation of {@link IShellOutputReceiver}, that takes multiple instances of {@link
 * IShellOutputReceiver} and broadcast the received data to all of them.
 */
public class MultiReceiver implements IShellOutputReceiver {

    private final @NonNull ArrayList<IShellOutputReceiver> myReceivers;

    public MultiReceiver(@NonNull IShellOutputReceiver... receivers) {
        myReceivers = new ArrayList<>(Arrays.asList(receivers));
    }

    @Override
    public void addOutput(@NonNull byte[] data, int offset, int length) {
        updateReceiverList();
        for (IShellOutputReceiver receiver : myReceivers) {
            receiver.addOutput(data, offset, length);
        }
    }

    @Override
    public void flush() {
        updateReceiverList();
        for (IShellOutputReceiver receiver : myReceivers) {
            receiver.flush();
        }
        myReceivers.clear();
    }

    @Override
    public boolean isCancelled() {
        updateReceiverList();
        return myReceivers.isEmpty();
    }

    /** Removes any cancelled receiver from the current list. */
    private void updateReceiverList() {
        myReceivers.removeIf(IShellOutputReceiver::isCancelled);
    }
}
