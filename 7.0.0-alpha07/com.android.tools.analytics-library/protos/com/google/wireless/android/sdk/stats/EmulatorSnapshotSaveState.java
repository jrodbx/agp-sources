// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: studio_stats.proto

package com.google.wireless.android.sdk.stats;

/**
 * <pre>
 * Generic snapshot save states. Distinguished
 * from Quickboot save states.
 * </pre>
 *
 * Protobuf enum {@code android_studio.EmulatorSnapshotSaveState}
 */
public enum EmulatorSnapshotSaveState
    implements com.google.protobuf.ProtocolMessageEnum {
  /**
   * <pre>
   * Successful saving.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_SUCCEEDED_NORMAL = 0;</code>
   */
  EMULATOR_SNAPSHOT_SAVE_SUCCEEDED_NORMAL(0),
  /**
   * <pre>
   * Generic failure when saving state.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_FAILED = 1;</code>
   */
  EMULATOR_SNAPSHOT_SAVE_FAILED(1),
  /**
   * <pre>
   * Saving skipped: not supported in current configuration.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_SKIPPED_UNSUPPORTED = 2;</code>
   */
  EMULATOR_SNAPSHOT_SAVE_SKIPPED_UNSUPPORTED(2),
  /**
   * <pre>
   * Saving skipped: Not booted yet.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_SKIPPED_NOT_BOOTED = 3;</code>
   */
  EMULATOR_SNAPSHOT_SAVE_SKIPPED_NOT_BOOTED(3),
  /**
   * <pre>
   * Saving skipped: No snapshot name given.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_SKIPPED_NO_SNAPSHOT = 4;</code>
   */
  EMULATOR_SNAPSHOT_SAVE_SKIPPED_NO_SNAPSHOT(4),
  /**
   * <pre>
   * Saving skipped: Disk under pressure.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_SKIPPED_DISK_PRESSURE = 5;</code>
   */
  EMULATOR_SNAPSHOT_SAVE_SKIPPED_DISK_PRESSURE(5),
  ;

  /**
   * <pre>
   * Successful saving.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_SUCCEEDED_NORMAL = 0;</code>
   */
  public static final int EMULATOR_SNAPSHOT_SAVE_SUCCEEDED_NORMAL_VALUE = 0;
  /**
   * <pre>
   * Generic failure when saving state.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_FAILED = 1;</code>
   */
  public static final int EMULATOR_SNAPSHOT_SAVE_FAILED_VALUE = 1;
  /**
   * <pre>
   * Saving skipped: not supported in current configuration.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_SKIPPED_UNSUPPORTED = 2;</code>
   */
  public static final int EMULATOR_SNAPSHOT_SAVE_SKIPPED_UNSUPPORTED_VALUE = 2;
  /**
   * <pre>
   * Saving skipped: Not booted yet.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_SKIPPED_NOT_BOOTED = 3;</code>
   */
  public static final int EMULATOR_SNAPSHOT_SAVE_SKIPPED_NOT_BOOTED_VALUE = 3;
  /**
   * <pre>
   * Saving skipped: No snapshot name given.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_SKIPPED_NO_SNAPSHOT = 4;</code>
   */
  public static final int EMULATOR_SNAPSHOT_SAVE_SKIPPED_NO_SNAPSHOT_VALUE = 4;
  /**
   * <pre>
   * Saving skipped: Disk under pressure.
   * </pre>
   *
   * <code>EMULATOR_SNAPSHOT_SAVE_SKIPPED_DISK_PRESSURE = 5;</code>
   */
  public static final int EMULATOR_SNAPSHOT_SAVE_SKIPPED_DISK_PRESSURE_VALUE = 5;


  public final int getNumber() {
    return value;
  }

  /**
   * @param value The numeric wire value of the corresponding enum entry.
   * @return The enum associated with the given numeric wire value.
   * @deprecated Use {@link #forNumber(int)} instead.
   */
  @java.lang.Deprecated
  public static EmulatorSnapshotSaveState valueOf(int value) {
    return forNumber(value);
  }

  /**
   * @param value The numeric wire value of the corresponding enum entry.
   * @return The enum associated with the given numeric wire value.
   */
  public static EmulatorSnapshotSaveState forNumber(int value) {
    switch (value) {
      case 0: return EMULATOR_SNAPSHOT_SAVE_SUCCEEDED_NORMAL;
      case 1: return EMULATOR_SNAPSHOT_SAVE_FAILED;
      case 2: return EMULATOR_SNAPSHOT_SAVE_SKIPPED_UNSUPPORTED;
      case 3: return EMULATOR_SNAPSHOT_SAVE_SKIPPED_NOT_BOOTED;
      case 4: return EMULATOR_SNAPSHOT_SAVE_SKIPPED_NO_SNAPSHOT;
      case 5: return EMULATOR_SNAPSHOT_SAVE_SKIPPED_DISK_PRESSURE;
      default: return null;
    }
  }

  public static com.google.protobuf.Internal.EnumLiteMap<EmulatorSnapshotSaveState>
      internalGetValueMap() {
    return internalValueMap;
  }
  private static final com.google.protobuf.Internal.EnumLiteMap<
      EmulatorSnapshotSaveState> internalValueMap =
        new com.google.protobuf.Internal.EnumLiteMap<EmulatorSnapshotSaveState>() {
          public EmulatorSnapshotSaveState findValueByNumber(int number) {
            return EmulatorSnapshotSaveState.forNumber(number);
          }
        };

  public final com.google.protobuf.Descriptors.EnumValueDescriptor
      getValueDescriptor() {
    return getDescriptor().getValues().get(ordinal());
  }
  public final com.google.protobuf.Descriptors.EnumDescriptor
      getDescriptorForType() {
    return getDescriptor();
  }
  public static final com.google.protobuf.Descriptors.EnumDescriptor
      getDescriptor() {
    return com.google.wireless.android.sdk.stats.AndroidStudioStats.getDescriptor().getEnumTypes().get(1);
  }

  private static final EmulatorSnapshotSaveState[] VALUES = values();

  public static EmulatorSnapshotSaveState valueOf(
      com.google.protobuf.Descriptors.EnumValueDescriptor desc) {
    if (desc.getType() != getDescriptor()) {
      throw new java.lang.IllegalArgumentException(
        "EnumValueDescriptor is not for this type.");
    }
    return VALUES[desc.getIndex()];
  }

  private final int value;

  private EmulatorSnapshotSaveState(int value) {
    this.value = value;
  }

  // @@protoc_insertion_point(enum_scope:android_studio.EmulatorSnapshotSaveState)
}

