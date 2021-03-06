// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface EmulatorFeatureFlagStateOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.EmulatorFeatureFlagState)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Which features were enabled by default or through the server-side config.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag attempted_enabled_feature_flags = 1;</code>
   * @return A list containing the attemptedEnabledFeatureFlags.
   */
  java.util.List<com.google.wireless.android.sdk.stats.EmulatorFeatureFlagState.EmulatorFeatureFlag> getAttemptedEnabledFeatureFlagsList();
  /**
   * <pre>
   * Which features were enabled by default or through the server-side config.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag attempted_enabled_feature_flags = 1;</code>
   * @return The count of attemptedEnabledFeatureFlags.
   */
  int getAttemptedEnabledFeatureFlagsCount();
  /**
   * <pre>
   * Which features were enabled by default or through the server-side config.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag attempted_enabled_feature_flags = 1;</code>
   * @param index The index of the element to return.
   * @return The attemptedEnabledFeatureFlags at the given index.
   */
  com.google.wireless.android.sdk.stats.EmulatorFeatureFlagState.EmulatorFeatureFlag getAttemptedEnabledFeatureFlags(int index);

  /**
   * <pre>
   * Which features were enabled through user override.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag user_overridden_enabled_features = 2;</code>
   * @return A list containing the userOverriddenEnabledFeatures.
   */
  java.util.List<com.google.wireless.android.sdk.stats.EmulatorFeatureFlagState.EmulatorFeatureFlag> getUserOverriddenEnabledFeaturesList();
  /**
   * <pre>
   * Which features were enabled through user override.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag user_overridden_enabled_features = 2;</code>
   * @return The count of userOverriddenEnabledFeatures.
   */
  int getUserOverriddenEnabledFeaturesCount();
  /**
   * <pre>
   * Which features were enabled through user override.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag user_overridden_enabled_features = 2;</code>
   * @param index The index of the element to return.
   * @return The userOverriddenEnabledFeatures at the given index.
   */
  com.google.wireless.android.sdk.stats.EmulatorFeatureFlagState.EmulatorFeatureFlag getUserOverriddenEnabledFeatures(int index);

  /**
   * <pre>
   * Which features were disabled through user override.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag user_overridden_disabled_features = 3;</code>
   * @return A list containing the userOverriddenDisabledFeatures.
   */
  java.util.List<com.google.wireless.android.sdk.stats.EmulatorFeatureFlagState.EmulatorFeatureFlag> getUserOverriddenDisabledFeaturesList();
  /**
   * <pre>
   * Which features were disabled through user override.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag user_overridden_disabled_features = 3;</code>
   * @return The count of userOverriddenDisabledFeatures.
   */
  int getUserOverriddenDisabledFeaturesCount();
  /**
   * <pre>
   * Which features were disabled through user override.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag user_overridden_disabled_features = 3;</code>
   * @param index The index of the element to return.
   * @return The userOverriddenDisabledFeatures at the given index.
   */
  com.google.wireless.android.sdk.stats.EmulatorFeatureFlagState.EmulatorFeatureFlag getUserOverriddenDisabledFeatures(int index);

  /**
   * <pre>
   * Which features ended up being enabled overall.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag resulting_enabled_features = 4;</code>
   * @return A list containing the resultingEnabledFeatures.
   */
  java.util.List<com.google.wireless.android.sdk.stats.EmulatorFeatureFlagState.EmulatorFeatureFlag> getResultingEnabledFeaturesList();
  /**
   * <pre>
   * Which features ended up being enabled overall.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag resulting_enabled_features = 4;</code>
   * @return The count of resultingEnabledFeatures.
   */
  int getResultingEnabledFeaturesCount();
  /**
   * <pre>
   * Which features ended up being enabled overall.
   * </pre>
   *
   * <code>repeated .android_studio.EmulatorFeatureFlagState.EmulatorFeatureFlag resulting_enabled_features = 4;</code>
   * @param index The index of the element to return.
   * @return The resultingEnabledFeatures at the given index.
   */
  com.google.wireless.android.sdk.stats.EmulatorFeatureFlagState.EmulatorFeatureFlag getResultingEnabledFeatures(int index);
}
