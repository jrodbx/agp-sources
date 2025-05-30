// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface TaskFailedMetadataOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.TaskFailedMetadata)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * The task data.
   * </pre>
   *
   * <code>optional .android_studio.TaskMetadata task_data = 1 [lazy = true];</code>
   * @return Whether the taskData field is set.
   */
  boolean hasTaskData();
  /**
   * <pre>
   * The task data.
   * </pre>
   *
   * <code>optional .android_studio.TaskMetadata task_data = 1 [lazy = true];</code>
   * @return The taskData.
   */
  com.google.wireless.android.sdk.stats.TaskMetadata getTaskData();
  /**
   * <pre>
   * The task data.
   * </pre>
   *
   * <code>optional .android_studio.TaskMetadata task_data = 1 [lazy = true];</code>
   */
  com.google.wireless.android.sdk.stats.TaskMetadataOrBuilder getTaskDataOrBuilder();

  /**
   * <pre>
   * The point in the task lifecycle it failed.
   * </pre>
   *
   * <code>optional .android_studio.TaskFailedMetadata.FailingPoint failing_point = 2;</code>
   * @return Whether the failingPoint field is set.
   */
  boolean hasFailingPoint();
  /**
   * <pre>
   * The point in the task lifecycle it failed.
   * </pre>
   *
   * <code>optional .android_studio.TaskFailedMetadata.FailingPoint failing_point = 2;</code>
   * @return The failingPoint.
   */
  com.google.wireless.android.sdk.stats.TaskFailedMetadata.FailingPoint getFailingPoint();

  /**
   * <pre>
   * Set if |failing_point| is |TASK_START|
   * </pre>
   *
   * <code>.android_studio.TaskFailedMetadata.TaskStartFailedMetadata task_start_failure_metadata = 3 [lazy = true];</code>
   * @return Whether the taskStartFailureMetadata field is set.
   */
  boolean hasTaskStartFailureMetadata();
  /**
   * <pre>
   * Set if |failing_point| is |TASK_START|
   * </pre>
   *
   * <code>.android_studio.TaskFailedMetadata.TaskStartFailedMetadata task_start_failure_metadata = 3 [lazy = true];</code>
   * @return The taskStartFailureMetadata.
   */
  com.google.wireless.android.sdk.stats.TaskFailedMetadata.TaskStartFailedMetadata getTaskStartFailureMetadata();
  /**
   * <pre>
   * Set if |failing_point| is |TASK_START|
   * </pre>
   *
   * <code>.android_studio.TaskFailedMetadata.TaskStartFailedMetadata task_start_failure_metadata = 3 [lazy = true];</code>
   */
  com.google.wireless.android.sdk.stats.TaskFailedMetadata.TaskStartFailedMetadataOrBuilder getTaskStartFailureMetadataOrBuilder();

  /**
   * <pre>
   * Set if |failing_point| is |TASK_STOP|
   * </pre>
   *
   * <code>.android_studio.TaskFailedMetadata.TaskStopFailedMetadata task_stop_failure_metadata = 4 [lazy = true];</code>
   * @return Whether the taskStopFailureMetadata field is set.
   */
  boolean hasTaskStopFailureMetadata();
  /**
   * <pre>
   * Set if |failing_point| is |TASK_STOP|
   * </pre>
   *
   * <code>.android_studio.TaskFailedMetadata.TaskStopFailedMetadata task_stop_failure_metadata = 4 [lazy = true];</code>
   * @return The taskStopFailureMetadata.
   */
  com.google.wireless.android.sdk.stats.TaskFailedMetadata.TaskStopFailedMetadata getTaskStopFailureMetadata();
  /**
   * <pre>
   * Set if |failing_point| is |TASK_STOP|
   * </pre>
   *
   * <code>.android_studio.TaskFailedMetadata.TaskStopFailedMetadata task_stop_failure_metadata = 4 [lazy = true];</code>
   */
  com.google.wireless.android.sdk.stats.TaskFailedMetadata.TaskStopFailedMetadataOrBuilder getTaskStopFailureMetadataOrBuilder();

  /**
   * <pre>
   * Set if |failing_point| is |TASK_PROCESSING|
   * </pre>
   *
   * <code>.android_studio.TaskFailedMetadata.TaskProcessingFailedMetadata task_processing_failure_metadata = 5 [lazy = true];</code>
   * @return Whether the taskProcessingFailureMetadata field is set.
   */
  boolean hasTaskProcessingFailureMetadata();
  /**
   * <pre>
   * Set if |failing_point| is |TASK_PROCESSING|
   * </pre>
   *
   * <code>.android_studio.TaskFailedMetadata.TaskProcessingFailedMetadata task_processing_failure_metadata = 5 [lazy = true];</code>
   * @return The taskProcessingFailureMetadata.
   */
  com.google.wireless.android.sdk.stats.TaskFailedMetadata.TaskProcessingFailedMetadata getTaskProcessingFailureMetadata();
  /**
   * <pre>
   * Set if |failing_point| is |TASK_PROCESSING|
   * </pre>
   *
   * <code>.android_studio.TaskFailedMetadata.TaskProcessingFailedMetadata task_processing_failure_metadata = 5 [lazy = true];</code>
   */
  com.google.wireless.android.sdk.stats.TaskFailedMetadata.TaskProcessingFailedMetadataOrBuilder getTaskProcessingFailureMetadataOrBuilder();

  com.google.wireless.android.sdk.stats.TaskFailedMetadata.UnionCase getUnionCase();
}
