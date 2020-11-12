/*
 * Copyright (C) 2007 The Android Open Source Project
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

import com.android.ddmlib.Log.LogLevel;
import java.util.function.Function;

/**
 * Preferences for the ddm library.
 * <p>This class does not handle storing the preferences. It is merely a central point for
 * applications using the ddmlib to override the default values.
 * <p>Various components of the ddmlib query this class to get their values.
 * <p>Calls to some <code>set##()</code> methods will update the components using the values
 * right away, while other methods will have no effect once {@link AndroidDebugBridge#init(boolean)}
 * has been called.
 * <p>Check the documentation of each method.
 */
public final class DdmPreferences {

  /**
   * Default value for thread update flag upon client connection.
   */
  public static final boolean DEFAULT_INITIAL_THREAD_UPDATE = false;
  /**
   * Default value for heap update flag upon client connection.
   */
  public static final boolean DEFAULT_INITIAL_HEAP_UPDATE = false;
  /**
   * Default value for the logcat {@link LogLevel}
   */
  public static final LogLevel DEFAULT_LOG_LEVEL = LogLevel.ERROR;
  /**
   * Default timeout values for adb connection (milliseconds)
   */
  public static final int DEFAULT_TIMEOUT = 5000; // standard delay, in ms
  /**
   * Default profiler buffer size (megabytes)
   */
  public static final int DEFAULT_PROFILER_BUFFER_SIZE_MB = 8;
  /**
   * Default values for the use of the ADBHOST environment variable.
   */
  public static final boolean DEFAULT_USE_ADBHOST = false;
  public static final String DEFAULT_ADBHOST_VALUE = "127.0.0.1";

  private static boolean sThreadUpdate = DEFAULT_INITIAL_THREAD_UPDATE;
  private static boolean sInitialHeapUpdate = DEFAULT_INITIAL_HEAP_UPDATE;

  private static LogLevel sLogLevel = DEFAULT_LOG_LEVEL;
  private static int sTimeOut = DEFAULT_TIMEOUT;
  private static int sProfilerBufferSizeMb = DEFAULT_PROFILER_BUFFER_SIZE_MB;

  private static boolean sUseAdbHost = DEFAULT_USE_ADBHOST;
  private static String sAdbHostValue = DEFAULT_ADBHOST_VALUE;
    private static int sJdwpMaxPacketSize =
            getPropertyOrDefault(
                    "DDMLIB_JDWP_MAX_PACKET_SIZE", 800 * 1024 * 1024, Integer::parseInt);

    /** Enable / Disable the JdwpProxy feature. */
    private static boolean sJdwpProxyEnabled =
            getPropertyOrDefault("DDMLIB_JDWP_PROXY_ENABLED", true, Boolean::parseBoolean);
    /** Port used by JdwpProxy feature */
    private static int sJdwpProxyPort =
            getPropertyOrDefault("DDMLIB_JDWP_PROXY_PORT", 8599, Integer::parseInt);

  /**
   * Returns the initial {@link Client} flag for thread updates.
   *
   * @see #setInitialThreadUpdate(boolean)
   */
  public static boolean getInitialThreadUpdate() {
    return sThreadUpdate;
  }

  /**
   * Sets the initial {@link Client} flag for thread updates.
   * <p>This change takes effect right away, for newly created {@link Client} objects.
   */
    public static void setInitialThreadUpdate(boolean state) {
        sThreadUpdate = state;
    }

    /**
     * Returns the initial {@link Client} flag for heap updates.
     * @see #setInitialHeapUpdate(boolean)
     */
    public static boolean getInitialHeapUpdate() {
        return sInitialHeapUpdate;
    }

    /**
     * Sets the initial {@link Client} flag for heap updates.
     * <p>If <code>true</code>, the {@link ClientData} will automatically be updated with
     * the VM heap information whenever a GC happens.
     * <p>This change takes effect right away, for newly created {@link Client} objects.
     */
    public static void setInitialHeapUpdate(boolean state) {
        sInitialHeapUpdate = state;
    }

    /**
     * Returns the minimum {@link LogLevel} being displayed.
     */
    public static LogLevel getLogLevel() {
        return sLogLevel;
    }

    /**
     * Sets the minimum {@link LogLevel} to display.
     * <p>This change takes effect right away.
     */
    public static void setLogLevel(String value) {
        sLogLevel = LogLevel.getByString(value);

        Log.setLevel(sLogLevel);
    }

    /**
     * Returns the timeout to be used in adb connections (milliseconds).
     */
    public static int getTimeOut() {
        return sTimeOut;
    }

    /**
     * Sets the timeout value for adb connection.
     * <p>This change takes effect for newly created connections only.
     * @param timeOut the timeout value (milliseconds).
     */
    public static void setTimeOut(int timeOut) {
        sTimeOut = timeOut;
    }

    /**
     * Returns the profiler buffer size (megabytes).
     */
    public static int getProfilerBufferSizeMb() {
        return sProfilerBufferSizeMb;
    }

    /**
     * Sets the profiler buffer size value.
     * @param bufferSizeMb the buffer size (megabytes).
     */
    public static void setProfilerBufferSizeMb(int bufferSizeMb) {
        sProfilerBufferSizeMb = bufferSizeMb;
    }

    /**
     * Returns a boolean indicating that the user uses or not the variable ADBHOST.
     */
    public static boolean getUseAdbHost() {
        return sUseAdbHost;
    }

    /**
     * Sets the value of the boolean indicating that the user uses or not the variable ADBHOST.
     * @param useAdbHost true if the user uses ADBHOST
     */
    public static void setUseAdbHost(boolean useAdbHost) {
        sUseAdbHost = useAdbHost;
    }

    /**
     * Returns the value of the ADBHOST variable set by the user.
     */
    public static String getAdbHostValue() {
        return sAdbHostValue;
    }

    /**
     * Sets the value of the ADBHOST variable.
     * @param adbHostValue
     */
    public static void setAdbHostValue(String adbHostValue) {
        sAdbHostValue = adbHostValue;
    }

    /**
     * Enable jdwp proxy service allowing for multiple client support DDMLIB clients to be used at
     * the same time.
     */
    public static void enableJdwpProxyService(boolean enabled) {
        sJdwpProxyEnabled = enabled;
    }

    public static boolean isJdwpProxyEnabled() {
        return sJdwpProxyEnabled;
    }

    /**
     * Set the port used by the jdwp proxy service. This port should be consistent across all
     * instances of the jdwp proxy service run on a single machine.
     *
     * @param port
     */
    public static void setJdwpProxyPort(int port) {
        sJdwpProxyPort = port;
    }

    public static int getJdwpProxyPort() {
        return sJdwpProxyPort;
    }

    private static <T> T getPropertyOrDefault(String property, T def, Function<String, T> parser) {
        try {
            return parser.apply(System.getProperty(property, def + ""));
        } catch (Exception ignored) {
            return def;
        }
    }

    /**
     * Packets that are larger than this will throw a buffer overflow exception and disconnect the
     * client.
     */
    public static int getJdwpMaxPacketSize() {
        return sJdwpMaxPacketSize;
    }

    public static void setsJdwpMaxPacketSize(int size) {
        sJdwpMaxPacketSize = size;
    }

    /**
     * Non accessible constructor.
     */
    private DdmPreferences() {
        // pass, only static methods in the class.
    }
}
