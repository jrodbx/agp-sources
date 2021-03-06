// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface EnergyEventMetadataOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.EnergyEventMetadata)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>optional .android_studio.EnergyEvent.Type type = 1;</code>
   * @return Whether the type field is set.
   */
  boolean hasType();
  /**
   * <code>optional .android_studio.EnergyEvent.Type type = 1;</code>
   * @return The type.
   */
  com.google.wireless.android.sdk.stats.EnergyEvent.Type getType();

  /**
   * <code>optional .android_studio.EnergyEvent.Subtype subtype = 2;</code>
   * @return Whether the subtype field is set.
   */
  boolean hasSubtype();
  /**
   * <code>optional .android_studio.EnergyEvent.Subtype subtype = 2;</code>
   * @return The subtype.
   */
  com.google.wireless.android.sdk.stats.EnergyEvent.Subtype getSubtype();

  /**
   * <code>repeated .android_studio.EnergyEvent.Subevent subevents = 3;</code>
   * @return A list containing the subevents.
   */
  java.util.List<com.google.wireless.android.sdk.stats.EnergyEvent.Subevent> getSubeventsList();
  /**
   * <code>repeated .android_studio.EnergyEvent.Subevent subevents = 3;</code>
   * @return The count of subevents.
   */
  int getSubeventsCount();
  /**
   * <code>repeated .android_studio.EnergyEvent.Subevent subevents = 3;</code>
   * @param index The index of the element to return.
   * @return The subevents at the given index.
   */
  com.google.wireless.android.sdk.stats.EnergyEvent.Subevent getSubevents(int index);
}
