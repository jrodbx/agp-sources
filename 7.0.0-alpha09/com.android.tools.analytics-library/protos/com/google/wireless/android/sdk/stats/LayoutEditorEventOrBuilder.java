// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface LayoutEditorEventOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.LayoutEditorEvent)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Type of event
   * </pre>
   *
   * <code>optional .android_studio.LayoutEditorEvent.LayoutEditorEventType type = 1;</code>
   * @return Whether the type field is set.
   */
  boolean hasType();
  /**
   * <pre>
   * Type of event
   * </pre>
   *
   * <code>optional .android_studio.LayoutEditorEvent.LayoutEditorEventType type = 1;</code>
   * @return The type.
   */
  com.google.wireless.android.sdk.stats.LayoutEditorEvent.LayoutEditorEventType getType();

  /**
   * <pre>
   * Layout editor current state
   * </pre>
   *
   * <code>optional .android_studio.LayoutEditorState state = 2;</code>
   * @return Whether the state field is set.
   */
  boolean hasState();
  /**
   * <pre>
   * Layout editor current state
   * </pre>
   *
   * <code>optional .android_studio.LayoutEditorState state = 2;</code>
   * @return The state.
   */
  com.google.wireless.android.sdk.stats.LayoutEditorState getState();
  /**
   * <pre>
   * Layout editor current state
   * </pre>
   *
   * <code>optional .android_studio.LayoutEditorState state = 2;</code>
   */
  com.google.wireless.android.sdk.stats.LayoutEditorStateOrBuilder getStateOrBuilder();

  /**
   * <pre>
   * Result of the render when (type = RENDER)
   * </pre>
   *
   * <code>optional .android_studio.LayoutEditorRenderResult render_result = 3;</code>
   * @return Whether the renderResult field is set.
   */
  boolean hasRenderResult();
  /**
   * <pre>
   * Result of the render when (type = RENDER)
   * </pre>
   *
   * <code>optional .android_studio.LayoutEditorRenderResult render_result = 3;</code>
   * @return The renderResult.
   */
  com.google.wireless.android.sdk.stats.LayoutEditorRenderResult getRenderResult();
  /**
   * <pre>
   * Result of the render when (type = RENDER)
   * </pre>
   *
   * <code>optional .android_studio.LayoutEditorRenderResult render_result = 3;</code>
   */
  com.google.wireless.android.sdk.stats.LayoutEditorRenderResultOrBuilder getRenderResultOrBuilder();

  /**
   * <pre>
   * Details related to using the palette (type = DROP_VIEW_FROM_PALETTE)
   * </pre>
   *
   * <code>optional .android_studio.LayoutPaletteEvent palette_event = 4;</code>
   * @return Whether the paletteEvent field is set.
   */
  boolean hasPaletteEvent();
  /**
   * <pre>
   * Details related to using the palette (type = DROP_VIEW_FROM_PALETTE)
   * </pre>
   *
   * <code>optional .android_studio.LayoutPaletteEvent palette_event = 4;</code>
   * @return The paletteEvent.
   */
  com.google.wireless.android.sdk.stats.LayoutPaletteEvent getPaletteEvent();
  /**
   * <pre>
   * Details related to using the palette (type = DROP_VIEW_FROM_PALETTE)
   * </pre>
   *
   * <code>optional .android_studio.LayoutPaletteEvent palette_event = 4;</code>
   */
  com.google.wireless.android.sdk.stats.LayoutPaletteEventOrBuilder getPaletteEventOrBuilder();

  /**
   * <pre>
   * Details related to changing an attribute (type = ATTRIBUTE_CHANGE)
   * </pre>
   *
   * <code>optional .android_studio.LayoutAttributeChangeEvent attribute_change_event = 5;</code>
   * @return Whether the attributeChangeEvent field is set.
   */
  boolean hasAttributeChangeEvent();
  /**
   * <pre>
   * Details related to changing an attribute (type = ATTRIBUTE_CHANGE)
   * </pre>
   *
   * <code>optional .android_studio.LayoutAttributeChangeEvent attribute_change_event = 5;</code>
   * @return The attributeChangeEvent.
   */
  com.google.wireless.android.sdk.stats.LayoutAttributeChangeEvent getAttributeChangeEvent();
  /**
   * <pre>
   * Details related to changing an attribute (type = ATTRIBUTE_CHANGE)
   * </pre>
   *
   * <code>optional .android_studio.LayoutAttributeChangeEvent attribute_change_event = 5;</code>
   */
  com.google.wireless.android.sdk.stats.LayoutAttributeChangeEventOrBuilder getAttributeChangeEventOrBuilder();

  /**
   * <pre>
   * Details related to favorite attribute changes (type = FAVORITE_CHANGE)
   * </pre>
   *
   * <code>optional .android_studio.LayoutFavoriteAttributeChangeEvent favorite_change_event = 6;</code>
   * @return Whether the favoriteChangeEvent field is set.
   */
  boolean hasFavoriteChangeEvent();
  /**
   * <pre>
   * Details related to favorite attribute changes (type = FAVORITE_CHANGE)
   * </pre>
   *
   * <code>optional .android_studio.LayoutFavoriteAttributeChangeEvent favorite_change_event = 6;</code>
   * @return The favoriteChangeEvent.
   */
  com.google.wireless.android.sdk.stats.LayoutFavoriteAttributeChangeEvent getFavoriteChangeEvent();
  /**
   * <pre>
   * Details related to favorite attribute changes (type = FAVORITE_CHANGE)
   * </pre>
   *
   * <code>optional .android_studio.LayoutFavoriteAttributeChangeEvent favorite_change_event = 6;</code>
   */
  com.google.wireless.android.sdk.stats.LayoutFavoriteAttributeChangeEventOrBuilder getFavoriteChangeEventOrBuilder();

  /**
   * <pre>
   * Result of the atf audit (type = ATF_AUDIT_RESULT)
   * </pre>
   *
   * <code>optional .android_studio.AtfAuditResult atf_audit_result = 7;</code>
   * @return Whether the atfAuditResult field is set.
   */
  boolean hasAtfAuditResult();
  /**
   * <pre>
   * Result of the atf audit (type = ATF_AUDIT_RESULT)
   * </pre>
   *
   * <code>optional .android_studio.AtfAuditResult atf_audit_result = 7;</code>
   * @return The atfAuditResult.
   */
  com.google.wireless.android.sdk.stats.AtfAuditResult getAtfAuditResult();
  /**
   * <pre>
   * Result of the atf audit (type = ATF_AUDIT_RESULT)
   * </pre>
   *
   * <code>optional .android_studio.AtfAuditResult atf_audit_result = 7;</code>
   */
  com.google.wireless.android.sdk.stats.AtfAuditResultOrBuilder getAtfAuditResultOrBuilder();
}
