// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface DisplayDetailsOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.DisplayDetails)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Width of the display screen in pixels.
   * </pre>
   *
   * <code>optional int64 width = 1;</code>
   * @return Whether the width field is set.
   */
  boolean hasWidth();
  /**
   * <pre>
   * Width of the display screen in pixels.
   * </pre>
   *
   * <code>optional int64 width = 1;</code>
   * @return The width.
   */
  long getWidth();

  /**
   * <pre>
   * Height of the display screen in pixels.
   * </pre>
   *
   * <code>optional int64 height = 2;</code>
   * @return Whether the height field is set.
   */
  boolean hasHeight();
  /**
   * <pre>
   * Height of the display screen in pixels.
   * </pre>
   *
   * <code>optional int64 height = 2;</code>
   * @return The height.
   */
  long getHeight();

  /**
   * <pre>
   * Density of the pixels on the screen horiziontally.
   * </pre>
   *
   * <code>optional int32 dots_per_inch_horizontal = 3;</code>
   * @return Whether the dotsPerInchHorizontal field is set.
   */
  boolean hasDotsPerInchHorizontal();
  /**
   * <pre>
   * Density of the pixels on the screen horiziontally.
   * </pre>
   *
   * <code>optional int32 dots_per_inch_horizontal = 3;</code>
   * @return The dotsPerInchHorizontal.
   */
  int getDotsPerInchHorizontal();

  /**
   * <pre>
   * Densitiy of the pixels on the screen vertically.
   * </pre>
   *
   * <code>optional int32 dots_per_inch_vertical = 4;</code>
   * @return Whether the dotsPerInchVertical field is set.
   */
  boolean hasDotsPerInchVertical();
  /**
   * <pre>
   * Densitiy of the pixels on the screen vertically.
   * </pre>
   *
   * <code>optional int32 dots_per_inch_vertical = 4;</code>
   * @return The dotsPerInchVertical.
   */
  int getDotsPerInchVertical();

  /**
   * <pre>
   * System scale factor for the screen resolution.
   * </pre>
   *
   * <code>optional float system_scale = 5;</code>
   * @return Whether the systemScale field is set.
   */
  boolean hasSystemScale();
  /**
   * <pre>
   * System scale factor for the screen resolution.
   * </pre>
   *
   * <code>optional float system_scale = 5;</code>
   * @return The systemScale.
   */
  float getSystemScale();
}
