/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.ddmlib.internal.ClientImpl;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * Options for initialization of AndroidDebugBridge.
 *
 * <p>See {@link AdbInitOptions.Builder} for a list of options and their default values.
 */
public class AdbInitOptions {
    /** Default init options. See {@link AdbInitOptions.Builder} for default values. */
    public static final AdbInitOptions DEFAULT = builder().build();

    /**
     * Indicates whether or not ddmlib should actively monitor clients using JDWP.
     *
     * <p>Ddmlib monitors devices via ADB, but won't monitor the applications running on these
     * devices unless <var>clientSupport</var> is enabled.
     *
     * <ul>
     *   <li>When <var>clientSupport</var> == <code>true</code>:<br>
     *       The library monitors the devices and the applications running on them. It will connect
     *       to each application, as a debugger of sort, to be able to interact with them through
     *       JDWP packets.
     *   <li>When <var>clientSupport</var> == <code>false</code>:<br>
     *       The library only monitors devices. The applications are left untouched, letting other
     *       tools built on <code>ddmlib</code> to connect a debugger to them.
     * </ul>
     *
     * <p><b>Only one client support enabled tool can run at any given time. If other tools need to
     * communicate with the underlying ADB daemon, disable client support to avoid clobbering
     * communications of other tools.</b>
     *
     * <p>Note that client support does not prevent debugging of applications running on devices. It
     * lets debuggers connect to <code>ddmlib</code> which acts as a proxy between the debuggers and
     * the applications to debug. See {@link ClientImpl#getDebuggerListenPort()}.
     */
    public final boolean clientSupport;

    /**
     * Enable user managed ADB mode where ddmlib will not start, restart, or terminate the ADB
     * server.
     */
    public final boolean userManagedAdbMode;

    /**
     * ADB server port of the user managed ADB server. Only in effect when in user managed ADB mode.
     */
    public final int userManagedAdbPort;

    /** Environment variables specifically for the ADB server process. */
    public final ImmutableMap<String, String> adbEnvVars;

    /**
     * Enable jdwp proxy service allowing for multiple client support DDMLIB clients to be used at
     * the same time.
     */
    public final boolean useJdwpProxyService;

    /**
     * Any jdwp packets detected larger than this size will throw a {@link
     * java.nio.BufferOverflowException}
     */
    public final int maxJdwpPacketSize;

    /** @return a new builder with default values. */
    public static Builder builder() {
        return new Builder();
    }

    /** {@link AdbInitOptions.Builder} for default values. */
    private AdbInitOptions(
            boolean clientSupport,
            boolean userManagedAdbMode,
            int userManagedAdbPort,
            ImmutableMap<String, String> adbEnvVars,
            boolean useJdwpService,
            int maxJdwpPacketSize) {
        this.clientSupport = clientSupport;
        this.userManagedAdbMode = userManagedAdbMode;
        this.userManagedAdbPort = userManagedAdbPort;
        this.adbEnvVars = adbEnvVars;
        this.useJdwpProxyService = useJdwpService;
        this.maxJdwpPacketSize = maxJdwpPacketSize;
    }

    /**
     * Builds initialization options for ADB.
     *
     * <p>Default settings are:
     *
     * <ul>
     *   <li>clientSupport = false
     *   <li>userManagedAdbMode = false
     * </ul>
     */
    public static class Builder {
        boolean clientSupport = false;
        boolean userManagedAdbMode = false;
        // Default to DDMLIB_JDWP_PROXY_ENABLED environment variable.
        boolean useJdwpProxyService = DdmPreferences.isJdwpProxyEnabled();
        int jdwpMaxPacketSize = DdmPreferences.getJdwpMaxPacketSize();
        int userManagedAdbPort = 0;
        ImmutableMap.Builder<String, String> envVarBuilder = ImmutableMap.builder();

        /** See {@link AdbInitOptions#clientSupport}. */
        public Builder setClientSupportEnabled(boolean enabled) {
            clientSupport = enabled;
            return this;
        }

        /** See {@link AdbInitOptions#useJdwpProxyService}. */
        public Builder useJdwpProxyService(boolean enabled) {
            useJdwpProxyService = enabled;
            return this;
        }

        public Builder setJdwpMaxPacketSize(int size) {
            jdwpMaxPacketSize = size;
            return this;
        }

        /**
         * See {@link AdbInitOptions#userManagedAdbMode} and {@link
         * AdbInitOptions#userManagedAdbPort}.
         */
        public Builder enableUserManagedAdbMode(int port) {
            userManagedAdbMode = true;
            userManagedAdbPort = port;
            return this;
        }

        /**
         * Add an environment variable for the ADB process. Note these environment variables won't
         * be used in user managed ADB mode because ADB server management is entirely up to the
         * user.
         */
        public Builder withEnv(@NonNull String key, String value) {
            envVarBuilder.put(key, value);
            return this;
        }

        /**
         * Add all environment variables from the given map for the ADB process. Note these
         * environment variables won't be used in user managed ADB mode because ADB server
         * management is entirely up to the user.
         */
        public Builder withEnv(@NonNull Map<String, String> envVars) {
            envVarBuilder.putAll(envVars);
            return this;
        }

        public AdbInitOptions build() {
            return new AdbInitOptions(
                    clientSupport,
                    userManagedAdbMode,
                    userManagedAdbPort,
                    envVarBuilder.build(),
                    useJdwpProxyService,
                    jdwpMaxPacketSize);
        }

    }
}
