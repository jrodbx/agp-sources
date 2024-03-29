// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface VisualLintEventOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.VisualLintEvent)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * The type of the visual lint issue affected by this event
   * </pre>
   *
   * <code>optional .android_studio.VisualLintEvent.IssueType issue_type = 1;</code>
   * @return Whether the issueType field is set.
   */
  boolean hasIssueType();
  /**
   * <pre>
   * The type of the visual lint issue affected by this event
   * </pre>
   *
   * <code>optional .android_studio.VisualLintEvent.IssueType issue_type = 1;</code>
   * @return The issueType.
   */
  com.google.wireless.android.sdk.stats.VisualLintEvent.IssueType getIssueType();

  /**
   * <pre>
   * The kind of event affecting the issue
   * </pre>
   *
   * <code>optional .android_studio.VisualLintEvent.IssueEvent issue_event = 2;</code>
   * @return Whether the issueEvent field is set.
   */
  boolean hasIssueEvent();
  /**
   * <pre>
   * The kind of event affecting the issue
   * </pre>
   *
   * <code>optional .android_studio.VisualLintEvent.IssueEvent issue_event = 2;</code>
   * @return The issueEvent.
   */
  com.google.wireless.android.sdk.stats.VisualLintEvent.IssueEvent getIssueEvent();
}
