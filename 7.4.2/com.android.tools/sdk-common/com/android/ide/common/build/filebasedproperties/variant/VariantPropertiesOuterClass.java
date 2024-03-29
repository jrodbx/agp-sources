// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: src/main/proto/variant_properties.proto

package com.android.ide.common.build.filebasedproperties.variant;

public final class VariantPropertiesOuterClass {
  private VariantPropertiesOuterClass() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_VariantProperties_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_VariantProperties_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_CommonProperties_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_CommonProperties_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_ArtifactOutputProperties_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_ArtifactOutputProperties_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_ArtifactOutputProperties_ManifestPlaceholdersEntry_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_ArtifactOutputProperties_ManifestPlaceholdersEntry_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_ApplicationVariantProperties_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_ApplicationVariantProperties_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_LibraryVariantProperties_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_LibraryVariantProperties_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_DynamicFeatureVariantProperties_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_DynamicFeatureVariantProperties_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_AndroidTestVariantProperties_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_AndroidTestVariantProperties_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_UnitTestVariantProperties_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_UnitTestVariantProperties_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_TestVariantProperties_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_TestVariantProperties_fieldAccessorTable;
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_TestFixturesVariantProperties_descriptor;
  static final
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_TestFixturesVariantProperties_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\'src/main/proto/variant_properties.prot" +
      "o\"\235\004\n\021VariantProperties\022!\n\006common\030\001 \001(\0132" +
      "\021.CommonProperties\022E\n\034applicationVariant" +
      "Properties\030\002 \001(\0132\035.ApplicationVariantPro" +
      "pertiesH\000\022=\n\030libraryVariantProperties\030\003 " +
      "\001(\0132\031.LibraryVariantPropertiesH\000\022K\n\037dyna" +
      "micFeatureVariantProperties\030\004 \001(\0132 .Dyna" +
      "micFeatureVariantPropertiesH\000\022E\n\034android" +
      "TestVariantProperties\030\005 \001(\0132\035.AndroidTes" +
      "tVariantPropertiesH\000\022?\n\031unitTestVariantP" +
      "roperties\030\006 \001(\0132\032.UnitTestVariantPropert" +
      "iesH\000\0227\n\025testVariantProperties\030\007 \001(\0132\026.T" +
      "estVariantPropertiesH\000\022F\n\034testFixtureVar" +
      "iantProperties\030\010 \001(\0132\036.TestFixturesVaria" +
      "ntPropertiesH\000B\t\n\007variant\"\022\n\020CommonPrope" +
      "rties\"\252\001\n\030ArtifactOutputProperties\022Q\n\024ma" +
      "nifestPlaceholders\030\001 \003(\01323.ArtifactOutpu" +
      "tProperties.ManifestPlaceholdersEntry\032;\n" +
      "\031ManifestPlaceholdersEntry\022\013\n\003key\030\001 \001(\t\022" +
      "\r\n\005value\030\002 \001(\t:\0028\001\"r\n\034ApplicationVariant" +
      "Properties\022;\n\030artifactOutputProperties\030\001" +
      " \001(\0132\031.ArtifactOutputProperties\022\025\n\rappli" +
      "cationId\030\002 \001(\t\"W\n\030LibraryVariantProperti" +
      "es\022;\n\030artifactOutputProperties\030\001 \001(\0132\031.A" +
      "rtifactOutputProperties\"^\n\037DynamicFeatur" +
      "eVariantProperties\022;\n\030artifactOutputProp" +
      "erties\030\001 \001(\0132\031.ArtifactOutputProperties\"" +
      "r\n\034AndroidTestVariantProperties\022;\n\030artif" +
      "actOutputProperties\030\001 \001(\0132\031.ArtifactOutp" +
      "utProperties\022\025\n\rapplicationId\030\002 \001(\t\"\033\n\031U" +
      "nitTestVariantProperties\"T\n\025TestVariantP" +
      "roperties\022;\n\030artifactOutputProperties\030\001 " +
      "\001(\0132\031.ArtifactOutputProperties\"\037\n\035TestFi" +
      "xturesVariantPropertiesB<\n8com.android.i" +
      "de.common.build.filebasedproperties.vari" +
      "antP\001b\006proto3"
    };
    descriptor = com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        });
    internal_static_VariantProperties_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_VariantProperties_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_VariantProperties_descriptor,
        new java.lang.String[] { "Common", "ApplicationVariantProperties", "LibraryVariantProperties", "DynamicFeatureVariantProperties", "AndroidTestVariantProperties", "UnitTestVariantProperties", "TestVariantProperties", "TestFixtureVariantProperties", "Variant", });
    internal_static_CommonProperties_descriptor =
      getDescriptor().getMessageTypes().get(1);
    internal_static_CommonProperties_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_CommonProperties_descriptor,
        new java.lang.String[] { });
    internal_static_ArtifactOutputProperties_descriptor =
      getDescriptor().getMessageTypes().get(2);
    internal_static_ArtifactOutputProperties_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_ArtifactOutputProperties_descriptor,
        new java.lang.String[] { "ManifestPlaceholders", });
    internal_static_ArtifactOutputProperties_ManifestPlaceholdersEntry_descriptor =
      internal_static_ArtifactOutputProperties_descriptor.getNestedTypes().get(0);
    internal_static_ArtifactOutputProperties_ManifestPlaceholdersEntry_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_ArtifactOutputProperties_ManifestPlaceholdersEntry_descriptor,
        new java.lang.String[] { "Key", "Value", });
    internal_static_ApplicationVariantProperties_descriptor =
      getDescriptor().getMessageTypes().get(3);
    internal_static_ApplicationVariantProperties_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_ApplicationVariantProperties_descriptor,
        new java.lang.String[] { "ArtifactOutputProperties", "ApplicationId", });
    internal_static_LibraryVariantProperties_descriptor =
      getDescriptor().getMessageTypes().get(4);
    internal_static_LibraryVariantProperties_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_LibraryVariantProperties_descriptor,
        new java.lang.String[] { "ArtifactOutputProperties", });
    internal_static_DynamicFeatureVariantProperties_descriptor =
      getDescriptor().getMessageTypes().get(5);
    internal_static_DynamicFeatureVariantProperties_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_DynamicFeatureVariantProperties_descriptor,
        new java.lang.String[] { "ArtifactOutputProperties", });
    internal_static_AndroidTestVariantProperties_descriptor =
      getDescriptor().getMessageTypes().get(6);
    internal_static_AndroidTestVariantProperties_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_AndroidTestVariantProperties_descriptor,
        new java.lang.String[] { "ArtifactOutputProperties", "ApplicationId", });
    internal_static_UnitTestVariantProperties_descriptor =
      getDescriptor().getMessageTypes().get(7);
    internal_static_UnitTestVariantProperties_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_UnitTestVariantProperties_descriptor,
        new java.lang.String[] { });
    internal_static_TestVariantProperties_descriptor =
      getDescriptor().getMessageTypes().get(8);
    internal_static_TestVariantProperties_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_TestVariantProperties_descriptor,
        new java.lang.String[] { "ArtifactOutputProperties", });
    internal_static_TestFixturesVariantProperties_descriptor =
      getDescriptor().getMessageTypes().get(9);
    internal_static_TestFixturesVariantProperties_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_TestFixturesVariantProperties_descriptor,
        new java.lang.String[] { });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
