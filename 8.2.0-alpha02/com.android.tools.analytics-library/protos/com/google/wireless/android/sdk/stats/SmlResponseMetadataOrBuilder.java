// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface SmlResponseMetadataOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.SmlResponseMetadata)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Trace id (provided by the server) for this response
   * </pre>
   *
   * <code>optional int64 rpc_global_id = 1;</code>
   * @return Whether the rpcGlobalId field is set.
   */
  boolean hasRpcGlobalId();
  /**
   * <pre>
   * Trace id (provided by the server) for this response
   * </pre>
   *
   * <code>optional int64 rpc_global_id = 1;</code>
   * @return The rpcGlobalId.
   */
  long getRpcGlobalId();
}
