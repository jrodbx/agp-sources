// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface TransportDaemonStartedInfoOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.TransportDaemonStartedInfo)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Whether it's a restart due to reasons from the device such as a crash or
   * being killed by the OS (not because IDE or Project is closed and then
   * reopened, or USB cable is unplugged).
   * </pre>
   *
   * <code>optional bool is_restart = 1;</code>
   * @return Whether the isRestart field is set.
   */
  boolean hasIsRestart();
  /**
   * <pre>
   * Whether it's a restart due to reasons from the device such as a crash or
   * being killed by the OS (not because IDE or Project is closed and then
   * reopened, or USB cable is unplugged).
   * </pre>
   *
   * <code>optional bool is_restart = 1;</code>
   * @return The isRestart.
   */
  boolean getIsRestart();

  /**
   * <pre>
   * Set only when it's a restart.
   * </pre>
   *
   * <code>optional int64 millisec_since_last_start = 2;</code>
   * @return Whether the millisecSinceLastStart field is set.
   */
  boolean hasMillisecSinceLastStart();
  /**
   * <pre>
   * Set only when it's a restart.
   * </pre>
   *
   * <code>optional int64 millisec_since_last_start = 2;</code>
   * @return The millisecSinceLastStart.
   */
  long getMillisecSinceLastStart();
}
