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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

import java.net.Proxy;

/**
 * Settings used by the repository framework.
 * TODO: implement more settings.
 */
public interface SettingsController {

    /**
     * @return If {@code true}, all connections should be made using HTTP rather than HTTPS.
     */
    boolean getForceHttp();

    /**
     * @param force If {@code true}, all connections should be made using HTTP rather than HTTPS.
     */
    void setForceHttp(boolean force);

    /**
     * @return If {@code true}, SDK updates should be performed using full installer (optimizes for
     * local installation time, but not for download time). Recommended for lower-end hardware.
     */
    boolean getDisableSdkPatches();

    /**
     * @param disable If {@code true}, SDK updates should be performed using full installer
     *                (optimizes for local installation time, but not for download time).
     *                Recommended for lower-end hardware.
     */
    void setDisableSdkPatches(boolean disable);

    /**
     * Gets the current channel. Only packages of channels at least as stable as specified will be
     * downloaded.
     */
    @Nullable
    Channel getChannel();

    /**
     * Gets the proxy to use for http connections. If no proxy is configured, returns
     * {@link Proxy#NO_PROXY}.
     */
    @NonNull
    default Proxy getProxy() {
        return Proxy.NO_PROXY;
    }
}
