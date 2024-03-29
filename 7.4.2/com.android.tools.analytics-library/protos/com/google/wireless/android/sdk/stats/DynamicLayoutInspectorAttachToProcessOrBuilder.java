// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface DynamicLayoutInspectorAttachToProcessOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.DynamicLayoutInspectorAttachToProcess)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Client type: Legacy or AppInspection
   * </pre>
   *
   * <code>optional .android_studio.DynamicLayoutInspectorAttachToProcess.ClientType client_type = 1;</code>
   * @return Whether the clientType field is set.
   */
  boolean hasClientType();
  /**
   * <pre>
   * Client type: Legacy or AppInspection
   * </pre>
   *
   * <code>optional .android_studio.DynamicLayoutInspectorAttachToProcess.ClientType client_type = 1;</code>
   * @return The clientType.
   */
  com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAttachToProcess.ClientType getClientType();

  /**
   * <pre>
   * True if the attach to process succeeded
   * </pre>
   *
   * <code>optional bool success = 2;</code>
   * @return Whether the success field is set.
   */
  boolean hasSuccess();
  /**
   * <pre>
   * True if the attach to process succeeded
   * </pre>
   *
   * <code>optional bool success = 2;</code>
   * @return The success.
   */
  boolean getSuccess();

  /**
   * <pre>
   * Error information if the attach failed
   * </pre>
   *
   * <code>optional .android_studio.DynamicLayoutInspectorErrorInfo error_info = 3;</code>
   * @return Whether the errorInfo field is set.
   */
  boolean hasErrorInfo();
  /**
   * <pre>
   * Error information if the attach failed
   * </pre>
   *
   * <code>optional .android_studio.DynamicLayoutInspectorErrorInfo error_info = 3;</code>
   * @return The errorInfo.
   */
  com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo getErrorInfo();
  /**
   * <pre>
   * Error information if the attach failed
   * </pre>
   *
   * <code>optional .android_studio.DynamicLayoutInspectorErrorInfo error_info = 3;</code>
   */
  com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfoOrBuilder getErrorInfoOrBuilder();
}
