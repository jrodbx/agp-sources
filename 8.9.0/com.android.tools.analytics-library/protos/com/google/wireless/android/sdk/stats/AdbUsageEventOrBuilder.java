// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface AdbUsageEventOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.AdbUsageEvent)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>.android_studio.AdbUsageEvent.JdwpProcessPropertiesCollectorEvent process_properties_event = 1 [lazy = true];</code>
   * @return Whether the processPropertiesEvent field is set.
   */
  boolean hasProcessPropertiesEvent();
  /**
   * <code>.android_studio.AdbUsageEvent.JdwpProcessPropertiesCollectorEvent process_properties_event = 1 [lazy = true];</code>
   * @return The processPropertiesEvent.
   */
  com.google.wireless.android.sdk.stats.AdbUsageEvent.JdwpProcessPropertiesCollectorEvent getProcessPropertiesEvent();
  /**
   * <code>.android_studio.AdbUsageEvent.JdwpProcessPropertiesCollectorEvent process_properties_event = 1 [lazy = true];</code>
   */
  com.google.wireless.android.sdk.stats.AdbUsageEvent.JdwpProcessPropertiesCollectorEventOrBuilder getProcessPropertiesEventOrBuilder();

  /**
   * <pre>
   * Add other types of events, e.g. attaching debugger, etc
   * </pre>
   *
   * <code>.android_studio.AdbUsageEvent.AdbDeviceStateChangeEvent device_state_change_event = 2 [lazy = true];</code>
   * @return Whether the deviceStateChangeEvent field is set.
   */
  boolean hasDeviceStateChangeEvent();
  /**
   * <pre>
   * Add other types of events, e.g. attaching debugger, etc
   * </pre>
   *
   * <code>.android_studio.AdbUsageEvent.AdbDeviceStateChangeEvent device_state_change_event = 2 [lazy = true];</code>
   * @return The deviceStateChangeEvent.
   */
  com.google.wireless.android.sdk.stats.AdbUsageEvent.AdbDeviceStateChangeEvent getDeviceStateChangeEvent();
  /**
   * <pre>
   * Add other types of events, e.g. attaching debugger, etc
   * </pre>
   *
   * <code>.android_studio.AdbUsageEvent.AdbDeviceStateChangeEvent device_state_change_event = 2 [lazy = true];</code>
   */
  com.google.wireless.android.sdk.stats.AdbUsageEvent.AdbDeviceStateChangeEventOrBuilder getDeviceStateChangeEventOrBuilder();

  com.google.wireless.android.sdk.stats.AdbUsageEvent.EventCase getEventCase();
}
