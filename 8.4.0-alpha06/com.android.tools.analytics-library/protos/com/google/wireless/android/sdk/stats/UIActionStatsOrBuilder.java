// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface UIActionStatsOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.UIActionStats)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * java class name (our code, not customer) of the UI Action reported on
   * e.g. 'com.android.build.instant_run.HotSwapBuildAction'
   * </pre>
   *
   * <code>optional string action_class_name = 1;</code>
   * @return Whether the actionClassName field is set.
   */
  boolean hasActionClassName();
  /**
   * <pre>
   * java class name (our code, not customer) of the UI Action reported on
   * e.g. 'com.android.build.instant_run.HotSwapBuildAction'
   * </pre>
   *
   * <code>optional string action_class_name = 1;</code>
   * @return The actionClassName.
   */
  java.lang.String getActionClassName();
  /**
   * <pre>
   * java class name (our code, not customer) of the UI Action reported on
   * e.g. 'com.android.build.instant_run.HotSwapBuildAction'
   * </pre>
   *
   * <code>optional string action_class_name = 1;</code>
   * @return The bytes for actionClassName.
   */
  com.google.protobuf.ByteString
      getActionClassNameBytes();

  /**
   * <pre>
   * How often since the last report this action was invoked.
   * </pre>
   *
   * <code>optional int64 invocations = 2;</code>
   * @return Whether the invocations field is set.
   */
  boolean hasInvocations();
  /**
   * <pre>
   * How often since the last report this action was invoked.
   * </pre>
   *
   * <code>optional int64 invocations = 2;</code>
   * @return The invocations.
   */
  long getInvocations();

  /**
   * <pre>
   * The way this action was invoked by the user.
   * </pre>
   *
   * <code>optional .android_studio.UIActionStats.InvocationKind invocation_kind = 3;</code>
   * @return Whether the invocationKind field is set.
   */
  boolean hasInvocationKind();
  /**
   * <pre>
   * The way this action was invoked by the user.
   * </pre>
   *
   * <code>optional .android_studio.UIActionStats.InvocationKind invocation_kind = 3;</code>
   * @return The invocationKind.
   */
  com.google.wireless.android.sdk.stats.UIActionStats.InvocationKind getInvocationKind();

  /**
   * <pre>
   * true if this message is sent directly, false if sent
   * with aggregated data over time.
   * </pre>
   *
   * <code>optional bool direct = 4;</code>
   * @return Whether the direct field is set.
   */
  boolean hasDirect();
  /**
   * <pre>
   * true if this message is sent directly, false if sent
   * with aggregated data over time.
   * </pre>
   *
   * <code>optional bool direct = 4;</code>
   * @return The direct.
   */
  boolean getDirect();

  /**
   * <pre>
   * The parent window/menu/toolbar that contains this action.
   * this is a software identifier specified in code in our product and does
   * not contain user data. Examples: "MainMenu", "Editor", "NavBarToolbar".
   * </pre>
   *
   * <code>optional string ui_place = 5;</code>
   * @return Whether the uiPlace field is set.
   */
  boolean hasUiPlace();
  /**
   * <pre>
   * The parent window/menu/toolbar that contains this action.
   * this is a software identifier specified in code in our product and does
   * not contain user data. Examples: "MainMenu", "Editor", "NavBarToolbar".
   * </pre>
   *
   * <code>optional string ui_place = 5;</code>
   * @return The uiPlace.
   */
  java.lang.String getUiPlace();
  /**
   * <pre>
   * The parent window/menu/toolbar that contains this action.
   * this is a software identifier specified in code in our product and does
   * not contain user data. Examples: "MainMenu", "Editor", "NavBarToolbar".
   * </pre>
   *
   * <code>optional string ui_place = 5;</code>
   * @return The bytes for uiPlace.
   */
  com.google.protobuf.ByteString
      getUiPlaceBytes();

  /**
   * <pre>
   * For toggle UI actions to track whether they are turning on or off.
   * </pre>
   *
   * <code>optional bool toggling_on = 6;</code>
   * @return Whether the togglingOn field is set.
   */
  boolean hasTogglingOn();
  /**
   * <pre>
   * For toggle UI actions to track whether they are turning on or off.
   * </pre>
   *
   * <code>optional bool toggling_on = 6;</code>
   * @return The togglingOn.
   */
  boolean getTogglingOn();
}
