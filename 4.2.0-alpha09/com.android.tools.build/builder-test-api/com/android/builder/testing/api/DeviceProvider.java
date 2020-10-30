/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.builder.testing.api;

import com.android.annotations.NonNull;
import com.google.common.annotations.Beta;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Provides a list of remote or local devices.
 */
@Beta
public abstract class DeviceProvider {

    /**
     * Returns the name of the provider. Must be unique, not contain spaces, and start with a lower
     * case.
     *
     * @return the name of the provider.
     */
    @NonNull
    public abstract String getName();

    /**
     * Uses the device provider to run the given action. This method calls {@link #init()} before
     * and {@link #terminate()} after executing the action.
     *
     * @param action the action to be executed
     * @return the returned value after executing the action
     * @throws DeviceException if initialization or termination fails
     * @throws ExecutionException if the action fails
     */
    public final <V> V use(@NonNull Callable<V> action) throws DeviceException, ExecutionException {
        init();
        try {
            try {
                return action.call();
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        } finally {
            terminate();
        }
    }

    /**
     * Initializes the provider. This must be called before any other method (except {@link
     * #getName()}). Each successful call to {@link #init()} MUST be followed by a call to {@link
     * #terminate()}.
     *
     * @throws DeviceException if initialization fails.
     * @see #use(Callable)
     */
    public abstract void init() throws DeviceException;

    /**
     * Terminates the provider and cleans up resources. Each successful call to {@link #init()} MUST
     * be followed by a call to {@link #terminate()}.
     *
     * @throws DeviceException if termination fails. Resources will still be cleaned up.
     * @see #use(Callable)
     */
    public abstract void terminate() throws DeviceException;

    /**
     * Returns a list of DeviceConnector.
     * @return a non-null list (but could be empty.)
     */
    @NonNull
    public abstract List<? extends DeviceConnector> getDevices();

    /**
     * Returns the timeout to use.
     * @return the time out in milliseconds.
     */
    public abstract int getTimeoutInMs();

    /**
     * Returns true if the provider is configured and able to run.
     *
     * @return if the provider is configured.
     */
    public abstract boolean isConfigured();
}
