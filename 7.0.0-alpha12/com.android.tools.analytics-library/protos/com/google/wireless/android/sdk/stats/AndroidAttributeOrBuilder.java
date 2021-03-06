// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

public interface AndroidAttributeOrBuilder extends
    // @@protoc_insertion_point(interface_extends:android_studio.AndroidAttribute)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <pre>
   * Specifies an attribute name of an Android View class or Navigation element.
   * Only properties defined on Google View classes or Google-provided
   * Navigation destinations will be specified by name.
   * Properties defined on custom views or destinations, or custom properties on
   * Google-provided destinations will be left unspecified.
   * </pre>
   *
   * <code>optional string attribute_name = 1;</code>
   * @return Whether the attributeName field is set.
   */
  boolean hasAttributeName();
  /**
   * <pre>
   * Specifies an attribute name of an Android View class or Navigation element.
   * Only properties defined on Google View classes or Google-provided
   * Navigation destinations will be specified by name.
   * Properties defined on custom views or destinations, or custom properties on
   * Google-provided destinations will be left unspecified.
   * </pre>
   *
   * <code>optional string attribute_name = 1;</code>
   * @return The attributeName.
   */
  java.lang.String getAttributeName();
  /**
   * <pre>
   * Specifies an attribute name of an Android View class or Navigation element.
   * Only properties defined on Google View classes or Google-provided
   * Navigation destinations will be specified by name.
   * Properties defined on custom views or destinations, or custom properties on
   * Google-provided destinations will be left unspecified.
   * </pre>
   *
   * <code>optional string attribute_name = 1;</code>
   * @return The bytes for attributeName.
   */
  com.google.protobuf.ByteString
      getAttributeNameBytes();

  /**
   * <pre>
   * The namespace of this attribute
   * </pre>
   *
   * <code>optional .android_studio.AndroidAttribute.AttributeNamespace attribute_namespace = 2;</code>
   * @return Whether the attributeNamespace field is set.
   */
  boolean hasAttributeNamespace();
  /**
   * <pre>
   * The namespace of this attribute
   * </pre>
   *
   * <code>optional .android_studio.AndroidAttribute.AttributeNamespace attribute_namespace = 2;</code>
   * @return The attributeNamespace.
   */
  com.google.wireless.android.sdk.stats.AndroidAttribute.AttributeNamespace getAttributeNamespace();
}
