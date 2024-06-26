// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface SmlConfigurationEventOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.SmlConfigurationEvent)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Whether SML features are available to this user.
   * </pre>
   *
   * <code>optional bool sml_available = 1;</code>
   * @return Whether the smlAvailable field is set.
   */
  boolean hasSmlAvailable();
  /**
   * <pre>
   * Whether SML features are available to this user.
   * </pre>
   *
   * <code>optional bool sml_available = 1;</code>
   * @return The smlAvailable.
   */
  boolean getSmlAvailable();

  /**
   * <pre>
   * Whether this user has started Studio Bot onboarding.
   * </pre>
   *
   * <code>optional bool bot_onboarding_started = 4;</code>
   * @return Whether the botOnboardingStarted field is set.
   */
  boolean hasBotOnboardingStarted();
  /**
   * <pre>
   * Whether this user has started Studio Bot onboarding.
   * </pre>
   *
   * <code>optional bool bot_onboarding_started = 4;</code>
   * @return The botOnboardingStarted.
   */
  boolean getBotOnboardingStarted();

  /**
   * <pre>
   * Whether this user has completed Studio Bot onboarding.
   * </pre>
   *
   * <code>optional bool bot_onboarding_completed = 5;</code>
   * @return Whether the botOnboardingCompleted field is set.
   */
  boolean hasBotOnboardingCompleted();
  /**
   * <pre>
   * Whether this user has completed Studio Bot onboarding.
   * </pre>
   *
   * <code>optional bool bot_onboarding_completed = 5;</code>
   * @return The botOnboardingCompleted.
   */
  boolean getBotOnboardingCompleted();

  /**
   * <pre>
   * User has ML completion enabled.
   * </pre>
   *
   * <code>optional bool completion_enabled = 2;</code>
   * @return Whether the completionEnabled field is set.
   */
  boolean hasCompletionEnabled();
  /**
   * <pre>
   * User has ML completion enabled.
   * </pre>
   *
   * <code>optional bool completion_enabled = 2;</code>
   * @return The completionEnabled.
   */
  boolean getCompletionEnabled();

  /**
   * <pre>
   * User has ML based fixes enabled.
   * </pre>
   *
   * <code>optional bool transform_enabled = 3;</code>
   * @return Whether the transformEnabled field is set.
   */
  boolean hasTransformEnabled();
  /**
   * <pre>
   * User has ML based fixes enabled.
   * </pre>
   *
   * <code>optional bool transform_enabled = 3;</code>
   * @return The transformEnabled.
   */
  boolean getTransformEnabled();
}
