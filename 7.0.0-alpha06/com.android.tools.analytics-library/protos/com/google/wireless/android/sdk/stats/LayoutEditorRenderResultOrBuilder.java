// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface LayoutEditorRenderResultOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.LayoutEditorRenderResult)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Action that triggered the render
   * </pre>
   *
   * <code>optional .android_studio.LayoutEditorRenderResult.Trigger trigger = 1;</code>
   * @return Whether the trigger field is set.
   */
  boolean hasTrigger();
  /**
   * <pre>
   * Action that triggered the render
   * </pre>
   *
   * <code>optional .android_studio.LayoutEditorRenderResult.Trigger trigger = 1;</code>
   * @return The trigger.
   */
  com.google.wireless.android.sdk.stats.LayoutEditorRenderResult.Trigger getTrigger();

  /**
   * <pre>
   * Render result code
   * </pre>
   *
   * <code>optional int32 result_code = 2;</code>
   * @return Whether the resultCode field is set.
   */
  boolean hasResultCode();
  /**
   * <pre>
   * Render result code
   * </pre>
   *
   * <code>optional int32 result_code = 2;</code>
   * @return The resultCode.
   */
  int getResultCode();

  /**
   * <pre>
   * Full render time in ms
   * </pre>
   *
   * <code>optional int64 total_render_time_ms = 4;</code>
   * @return Whether the totalRenderTimeMs field is set.
   */
  boolean hasTotalRenderTimeMs();
  /**
   * <pre>
   * Full render time in ms
   * </pre>
   *
   * <code>optional int64 total_render_time_ms = 4;</code>
   * @return The totalRenderTimeMs.
   */
  long getTotalRenderTimeMs();

  /**
   * <pre>
   * Number of components rendered
   * </pre>
   *
   * <code>optional int32 component_count = 5;</code>
   * @return Whether the componentCount field is set.
   */
  boolean hasComponentCount();
  /**
   * <pre>
   * Number of components rendered
   * </pre>
   *
   * <code>optional int32 component_count = 5;</code>
   * @return The componentCount.
   */
  int getComponentCount();

  /**
   * <pre>
   * Total number of issues (warnings + errors) in the error panel
   * </pre>
   *
   * <code>optional int32 total_issue_count = 6;</code>
   * @return Whether the totalIssueCount field is set.
   */
  boolean hasTotalIssueCount();
  /**
   * <pre>
   * Total number of issues (warnings + errors) in the error panel
   * </pre>
   *
   * <code>optional int32 total_issue_count = 6;</code>
   * @return The totalIssueCount.
   */
  int getTotalIssueCount();

  /**
   * <pre>
   * Errors displayed in the error panel
   * </pre>
   *
   * <code>optional int32 error_count = 7;</code>
   * @return Whether the errorCount field is set.
   */
  boolean hasErrorCount();
  /**
   * <pre>
   * Errors displayed in the error panel
   * </pre>
   *
   * <code>optional int32 error_count = 7;</code>
   * @return The errorCount.
   */
  int getErrorCount();

  /**
   * <pre>
   * Fidelity warnings
   * </pre>
   *
   * <code>optional int32 fidelity_warning_count = 8;</code>
   * @return Whether the fidelityWarningCount field is set.
   */
  boolean hasFidelityWarningCount();
  /**
   * <pre>
   * Fidelity warnings
   * </pre>
   *
   * <code>optional int32 fidelity_warning_count = 8;</code>
   * @return The fidelityWarningCount.
   */
  int getFidelityWarningCount();
}
