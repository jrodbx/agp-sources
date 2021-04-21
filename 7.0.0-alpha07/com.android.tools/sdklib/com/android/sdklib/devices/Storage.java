/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.devices;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Storage {
    // For parsing a string that represents a Storage
    static final Pattern storagePattern = Pattern.compile("([0-9]+)( *)([KMGT]?)(B?)");

    private long mNoBytes;

    public Storage(long amount, Unit unit) {
        mNoBytes = amount * unit.getNumberOfBytes();
    }

    public Storage(long amount) {
        this(amount, Unit.B);
    }

    /** Returns the amount of storage represented, in Bytes */
    public long getSize() {
        return getSizeAsUnit(Unit.B);
    }

    @NonNull
    public Storage deepCopy() {
        return new Storage(mNoBytes);
    }

    /**
     * Return the amount of storage represented by the instance in the given unit
     *
     * @param unit The unit of the result.
     * @return The size of the storage in the given unit.
     */
    public long getSizeAsUnit(@NonNull Unit unit) {
        return mNoBytes / unit.getNumberOfBytes();
    }

    /**
     * Returns the amount of storage represented by the instance in the given unit as a double to
     * get a more precise result
     *
     * @param unit The unit of the result.
     * @return The size of the storage in the given unit.
     */
    public double getPreciseSizeAsUnit(@NonNull Unit unit) {
        return ((double) mNoBytes) / unit.getNumberOfBytes();
    }

    /**
     * Decodes the given string and returns a {@link Storage} of the corresponding size. The input
     * string can look like these:
     *     "2" "2B" "2MB" "2 M" "2 MB"
     * But NOT like these:
     *     "2m" "2.6" "2 MiB"
     */
    @Nullable
    public static Storage getStorageFromString(@Nullable String storageString) {
    if (storageString == null || storageString.isEmpty()) {
      return null;
    }

    Matcher matcher = storagePattern.matcher(storageString);
    if (!matcher.matches()) {
      return null;
    }
    // Get the numeric part
    int numberPart;
    try {
      numberPart = Integer.parseInt(matcher.group(1));
    }
    catch (NumberFormatException unused) {
      return null;
    }
    // Get the units
    String unitString = matcher.group(3);
    Unit unitPart;
    if (!unitString.isEmpty()) {
      // The unit was specified
      unitPart = Unit.getEnum(matcher.group(3).charAt(0));
      if (unitPart == null) return null; // Should not happen
    }
    else if (matcher.group(4).isEmpty()) {
      // No unit specified at all. Use MiB.
      unitPart = Unit.MiB;
    }
    else {
      // Just "B"--use bytes
      unitPart = Unit.B;
    }
    return new Storage(numberPart, unitPart);
  }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof Storage)) {
            return false;
        }
        return this.getSize() == ((Storage) other).getSize();
    }

    public boolean lessThan(Object other) {
        if (!(other instanceof Storage)) {
            return false;
        }
        return this.getSize() < ((Storage) other).getSize();
    }

    @Override
    public int hashCode() {
        int result = 17;
        return 31 * result + (int) (mNoBytes ^ (mNoBytes >>> 32));
    }

    public enum Unit {
        B("B", "B", 1),
        KiB("KiB", "KB", 1024),
        MiB("MiB", "MB", 1024 * 1024),
        GiB("GiB", "GB", 1024 * 1024 * 1024),
        TiB("TiB", "TB", 1024L * 1024L * 1024L * 1024L);

        @NonNull private String mValue;

        @NonNull private String mDisplayValue;

        /** The number of bytes needed to have one of the given unit */
        private long mNoBytes;

        Unit(@NonNull String val, @NonNull String displayVal, long noBytes) {
            mValue = val;
            mDisplayValue = displayVal;
            mNoBytes = noBytes;
        }

        /** Accepts "B" "KiB" "MiB" "GiB" "TiB" */
        @Nullable
        public static Unit getEnum(@NonNull String val) {
            for (Unit unit : values()) {
                if (unit.mValue.equals(val)) {
                    return unit;
                }
            }
            return null;
        }

        /* Accepts 'B' 'K' 'M' 'G' 'T' */
        @Nullable
        public static Unit getEnum(char unitChar) {
            for (Unit unit : values()) {
                if (unitChar == unit.mValue.charAt(0)) {
                    return unit;
                }
            }
            return null;
        }

        public long getNumberOfBytes() {
            return mNoBytes;
        }

        @Override
        @NonNull
        public String toString() {
            return mValue;
        }

        @NonNull
        public String getDisplayValue() {
            return mDisplayValue;
        }

        public char getUnitChar() {
            return mValue.charAt(0);
        }
    }

    /**
     * Finds the largest {@link Unit} which can display the storage value as a positive integer with
     * no loss of accuracy.
     *
     * @return The most appropriate {@link Unit}.
     * @see {@link #getLargestReasonableUnits()}
     */
    @NonNull
    public Unit getAppropriateUnits() {
        Unit optimalUnit = Unit.B;
        for (Unit unit : Unit.values()) {
            if (mNoBytes % unit.getNumberOfBytes() == 0) {
                optimalUnit = unit;
            } else {
                break;
            }
        }
        return optimalUnit;
    }

    /**
     * Finds the largest {@link Unit} which can display the storage value with non-zero integer
     * part. This might be useful for displaying in user interface where full precision is not
     * critical and can be sacrificed in favor of better UX.
     *
     * @return The largest {@link Unit} such that getSize()/Unit.getNumberOfBytes() is not zero.
     * @see {@link #getAppropriateUnits()}
     */
    @NonNull
    public Unit getLargestReasonableUnits() {
        Unit optimalUnit = Unit.B;
        for (Unit unit : Unit.values()) {
            if (mNoBytes / unit.getNumberOfBytes() == 0) {
                break;
            }
            optimalUnit = unit;
        }
        return optimalUnit;
    }

    @Override
    @NonNull
    public String toString() {
        Unit unit = getAppropriateUnits();
        return String.format("%d %s", getSizeAsUnit(unit), unit.getDisplayValue());
    }

    /**
     * Represents a {@link Storage} as a string suitable for displaying in the UI.
     *
     * @see {@link #getLargestReasonableUnits()}
     */
    public String toUiString() {
        return toUiString(1);
    }

    /**
     * Represents a {@link Storage} as a string suitable for displaying in the UI.
     *
     * @param precision The number of digits after decimal point to display.
     * @see {@link #getLargestReasonableUnits()}
     */
    public String toUiString(int precision) {
        Unit reasonableUnit = getLargestReasonableUnits();
        if (reasonableUnit == Unit.B) {
            precision = 0; // It'd be silly to show decimal point in the number of bytes.
        }

        double sizeInReasonableUnits = getPreciseSizeAsUnit(reasonableUnit);
        String format = String.format("%%.%df %%s", precision);
        return String.format(format, sizeInReasonableUnits, reasonableUnit.getDisplayValue());
    }

  /**
   * Represents a {@link Storage} as a string suitable
   * for use in an INI file.
   */
  @NonNull
  public String toIniString() {
        Unit unit = getAppropriateUnits();
        return String.format("%d%c", getSizeAsUnit(unit), unit.getUnitChar());
  }
}
