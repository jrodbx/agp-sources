// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface EmulatorIceboxOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.EmulatorIcebox)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>optional .android_studio.EmulatorIcebox.StartIcebox start_icebox = 1;</code>
   * @return Whether the startIcebox field is set.
   */
  boolean hasStartIcebox();
  /**
   * <code>optional .android_studio.EmulatorIcebox.StartIcebox start_icebox = 1;</code>
   * @return The startIcebox.
   */
  com.google.wireless.android.sdk.stats.EmulatorIcebox.StartIcebox getStartIcebox();
  /**
   * <code>optional .android_studio.EmulatorIcebox.StartIcebox start_icebox = 1;</code>
   */
  com.google.wireless.android.sdk.stats.EmulatorIcebox.StartIceboxOrBuilder getStartIceboxOrBuilder();

  /**
   * <code>optional .android_studio.EmulatorIcebox.TakeSnapshot take_snapshot = 2;</code>
   * @return Whether the takeSnapshot field is set.
   */
  boolean hasTakeSnapshot();
  /**
   * <code>optional .android_studio.EmulatorIcebox.TakeSnapshot take_snapshot = 2;</code>
   * @return The takeSnapshot.
   */
  com.google.wireless.android.sdk.stats.EmulatorIcebox.TakeSnapshot getTakeSnapshot();
  /**
   * <code>optional .android_studio.EmulatorIcebox.TakeSnapshot take_snapshot = 2;</code>
   */
  com.google.wireless.android.sdk.stats.EmulatorIcebox.TakeSnapshotOrBuilder getTakeSnapshotOrBuilder();

  /**
   * <code>optional .android_studio.EmulatorIcebox.FinishIcebox finish_icebox = 3;</code>
   * @return Whether the finishIcebox field is set.
   */
  boolean hasFinishIcebox();
  /**
   * <code>optional .android_studio.EmulatorIcebox.FinishIcebox finish_icebox = 3;</code>
   * @return The finishIcebox.
   */
  com.google.wireless.android.sdk.stats.EmulatorIcebox.FinishIcebox getFinishIcebox();
  /**
   * <code>optional .android_studio.EmulatorIcebox.FinishIcebox finish_icebox = 3;</code>
   */
  com.google.wireless.android.sdk.stats.EmulatorIcebox.FinishIceboxOrBuilder getFinishIceboxOrBuilder();

  public com.google.wireless.android.sdk.stats.EmulatorIcebox.KindCase getKindCase();
}
