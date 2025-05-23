// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface AutoSyncSettingChangeEventOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.AutoSyncSettingChangeEvent)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Whether the Auto-Sync got enabled or disabled
   * </pre>
   *
   * <code>optional bool state = 1;</code>
   * @return Whether the state field is set.
   */
  boolean hasState();
  /**
   * <pre>
   * Whether the Auto-Sync got enabled or disabled
   * </pre>
   *
   * <code>optional bool state = 1;</code>
   * @return The state.
   */
  boolean getState();

  /**
   * <pre>
   * Which UI element does the change originate from
   * </pre>
   *
   * <code>optional .android_studio.AutoSyncSettingChangeEvent.ChangeSource change_source = 2;</code>
   * @return Whether the changeSource field is set.
   */
  boolean hasChangeSource();
  /**
   * <pre>
   * Which UI element does the change originate from
   * </pre>
   *
   * <code>optional .android_studio.AutoSyncSettingChangeEvent.ChangeSource change_source = 2;</code>
   * @return The changeSource.
   */
  com.google.wireless.android.sdk.stats.AutoSyncSettingChangeEvent.ChangeSource getChangeSource();
}
