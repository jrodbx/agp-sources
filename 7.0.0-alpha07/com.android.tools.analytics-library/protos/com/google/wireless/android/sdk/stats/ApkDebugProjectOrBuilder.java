// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface ApkDebugProjectOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.ApkDebugProject)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Client-side salted hash of the package name for tracking purposes.
   * </pre>
   *
   * <code>optional string package_id = 1;</code>
   * @return Whether the packageId field is set.
   */
  boolean hasPackageId();
  /**
   * <pre>
   * Client-side salted hash of the package name for tracking purposes.
   * </pre>
   *
   * <code>optional string package_id = 1;</code>
   * @return The packageId.
   */
  java.lang.String getPackageId();
  /**
   * <pre>
   * Client-side salted hash of the package name for tracking purposes.
   * </pre>
   *
   * <code>optional string package_id = 1;</code>
   * @return The bytes for packageId.
   */
  com.google.protobuf.ByteString
      getPackageIdBytes();
}
