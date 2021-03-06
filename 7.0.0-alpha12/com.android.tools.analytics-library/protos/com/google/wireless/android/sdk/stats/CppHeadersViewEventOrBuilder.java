// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface CppHeadersViewEventOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.CppHeadersViewEvent)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Metrics related to enhanced C++ header files view enabled by
   * under ENABLE_ENHANCED_NATIVE_HEADER_SUPPORT flag
   * </pre>
   *
   * <code>optional .android_studio.CppHeadersViewEvent.CppHeadersViewEventType type = 1;</code>
   * @return Whether the type field is set.
   */
  boolean hasType();
  /**
   * <pre>
   * Metrics related to enhanced C++ header files view enabled by
   * under ENABLE_ENHANCED_NATIVE_HEADER_SUPPORT flag
   * </pre>
   *
   * <code>optional .android_studio.CppHeadersViewEvent.CppHeadersViewEventType type = 1;</code>
   * @return The type.
   */
  com.google.wireless.android.sdk.stats.CppHeadersViewEvent.CppHeadersViewEventType getType();

  /**
   * <pre>
   * The amount of time taken for the event to complete in milliseconds.
   * </pre>
   *
   * <code>optional int64 event_duration_ms = 2;</code>
   * @return Whether the eventDurationMs field is set.
   */
  boolean hasEventDurationMs();
  /**
   * <pre>
   * The amount of time taken for the event to complete in milliseconds.
   * </pre>
   *
   * <code>optional int64 event_duration_ms = 2;</code>
   * @return The eventDurationMs.
   */
  long getEventDurationMs();

  /**
   * <pre>
   * The count of the immediate children of the node that was opened by
   * the user.
   * </pre>
   *
   * <code>optional int32 node_immediate_children = 3;</code>
   * @return Whether the nodeImmediateChildren field is set.
   */
  boolean hasNodeImmediateChildren();
  /**
   * <pre>
   * The count of the immediate children of the node that was opened by
   * the user.
   * </pre>
   *
   * <code>optional int32 node_immediate_children = 3;</code>
   * @return The nodeImmediateChildren.
   */
  int getNodeImmediateChildren();
}
