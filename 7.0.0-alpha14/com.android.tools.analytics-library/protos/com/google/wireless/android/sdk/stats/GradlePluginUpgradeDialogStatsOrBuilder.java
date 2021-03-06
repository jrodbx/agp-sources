// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface GradlePluginUpgradeDialogStatsOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.GradlePluginUpgradeDialogStats)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * The current Gradle Version used by the project e.g "4.10.3"
   * </pre>
   *
   * <code>optional string current_gradle_version = 1;</code>
   * @return Whether the currentGradleVersion field is set.
   */
  boolean hasCurrentGradleVersion();
  /**
   * <pre>
   * The current Gradle Version used by the project e.g "4.10.3"
   * </pre>
   *
   * <code>optional string current_gradle_version = 1;</code>
   * @return The currentGradleVersion.
   */
  java.lang.String getCurrentGradleVersion();
  /**
   * <pre>
   * The current Gradle Version used by the project e.g "4.10.3"
   * </pre>
   *
   * <code>optional string current_gradle_version = 1;</code>
   * @return The bytes for currentGradleVersion.
   */
  com.google.protobuf.ByteString
      getCurrentGradleVersionBytes();

  /**
   * <pre>
   * The current Android Gradle Plugin version used by the project e.g
   * "3.4-alpha01"
   * </pre>
   *
   * <code>optional string current_android_gradle_plugin_version = 2;</code>
   * @return Whether the currentAndroidGradlePluginVersion field is set.
   */
  boolean hasCurrentAndroidGradlePluginVersion();
  /**
   * <pre>
   * The current Android Gradle Plugin version used by the project e.g
   * "3.4-alpha01"
   * </pre>
   *
   * <code>optional string current_android_gradle_plugin_version = 2;</code>
   * @return The currentAndroidGradlePluginVersion.
   */
  java.lang.String getCurrentAndroidGradlePluginVersion();
  /**
   * <pre>
   * The current Android Gradle Plugin version used by the project e.g
   * "3.4-alpha01"
   * </pre>
   *
   * <code>optional string current_android_gradle_plugin_version = 2;</code>
   * @return The bytes for currentAndroidGradlePluginVersion.
   */
  com.google.protobuf.ByteString
      getCurrentAndroidGradlePluginVersionBytes();

  /**
   * <pre>
   * The Gradle version that the update prompt recommended e.g "5.1"
   * </pre>
   *
   * <code>optional string recommended_gradle_version = 3;</code>
   * @return Whether the recommendedGradleVersion field is set.
   */
  boolean hasRecommendedGradleVersion();
  /**
   * <pre>
   * The Gradle version that the update prompt recommended e.g "5.1"
   * </pre>
   *
   * <code>optional string recommended_gradle_version = 3;</code>
   * @return The recommendedGradleVersion.
   */
  java.lang.String getRecommendedGradleVersion();
  /**
   * <pre>
   * The Gradle version that the update prompt recommended e.g "5.1"
   * </pre>
   *
   * <code>optional string recommended_gradle_version = 3;</code>
   * @return The bytes for recommendedGradleVersion.
   */
  com.google.protobuf.ByteString
      getRecommendedGradleVersionBytes();

  /**
   * <pre>
   * The Android Gradle Plugin version that the upgrade prompt recommended e.g
   * "3.5-beta02"
   * </pre>
   *
   * <code>optional string recommended_android_gradle_plugin_version = 4;</code>
   * @return Whether the recommendedAndroidGradlePluginVersion field is set.
   */
  boolean hasRecommendedAndroidGradlePluginVersion();
  /**
   * <pre>
   * The Android Gradle Plugin version that the upgrade prompt recommended e.g
   * "3.5-beta02"
   * </pre>
   *
   * <code>optional string recommended_android_gradle_plugin_version = 4;</code>
   * @return The recommendedAndroidGradlePluginVersion.
   */
  java.lang.String getRecommendedAndroidGradlePluginVersion();
  /**
   * <pre>
   * The Android Gradle Plugin version that the upgrade prompt recommended e.g
   * "3.5-beta02"
   * </pre>
   *
   * <code>optional string recommended_android_gradle_plugin_version = 4;</code>
   * @return The bytes for recommendedAndroidGradlePluginVersion.
   */
  com.google.protobuf.ByteString
      getRecommendedAndroidGradlePluginVersionBytes();

  /**
   * <pre>
   * The action the user took
   * </pre>
   *
   * <code>optional .android_studio.GradlePluginUpgradeDialogStats.UserAction user_action = 5;</code>
   * @return Whether the userAction field is set.
   */
  boolean hasUserAction();
  /**
   * <pre>
   * The action the user took
   * </pre>
   *
   * <code>optional .android_studio.GradlePluginUpgradeDialogStats.UserAction user_action = 5;</code>
   * @return The userAction.
   */
  com.google.wireless.android.sdk.stats.GradlePluginUpgradeDialogStats.UserAction getUserAction();
}
