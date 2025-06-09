/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.ide.common.resources;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.io.File;
import java.io.Serializable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * A data item is the most elementary merge unit in the data merging process. Data items will
 * generally belong to a {@link DataFile} although, temporarily during the merge process data items
 * may not be associated to any data file. This will happen when data items are moved from one file
 * to another.
 *
 * <p>Data items can represent entire files, <em>e.g.</em>, a PNG file, or they can represent
 * individual entries in a file, <em>e.g.</em>, a string in a strings file.
 *
 * <p>Data items have three markers that represent its "state": touched, removed and written. A
 * touched data is a data item that needs to be examined in the merge process. A removed data item
 * is a data item that has been removed from its file. A written data item is a data item that has
 * been changed or added.
 *
 * @param <F> the type of data file the item belongs to
 */
abstract class DataItem<F extends DataFile> implements Serializable {
    /** Bit flag marking {@link #mStatus} as touched. */
    private static final int MASK_TOUCHED = 0x01;

    /** Bit flag marking {@link #mStatus} as removed. */
    private static final int MASK_REMOVED = 0x02;

    /** Bit flag marking {@link #mStatus} as written. */
    private static final int MASK_WRITTEN = 0x10;

    /** Name of the data item. */
    @NonNull private final String mName;

    /** File the data item comes from. */
    @Nullable private F mSourceFile;

    /**
     * The status of the Item. It's a bit mask as opposed to an enum
     * to differentiate removed and removed+written
     */
    private int mStatus = 0;

    /**
     * Constructs the object with a name, type and optional value.
     * Note that the object is not fully usable as-is. It must be added to a DataFile first.
     * @param name the name of the item
     */
    DataItem(@NonNull String name) {
        mName = Preconditions.checkNotNull(name);
    }

    /**
     * Returns the name of the item.
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Returns the DataFile the item is coming from.
     */
    @Nullable
    public F getSourceFile() {
        return mSourceFile;
    }

    /**
     * Sets the DataFile. The item must not belong to a data file.
     *
     * @param sourceFile the data file, if null then the item is marked as being removed from the
     *     data file
     */
    public void setSourceFile(@Nullable F sourceFile) {
        mSourceFile = sourceFile;
    }

    /**
     * Resets the state of the item be nothing.
     * @return this
     */
    @NonNull
    DataItem<F> resetStatus() {
        mStatus = 0;
        return this;
    }

    /**
     * Resets the state of the item be WRITTEN. All other states are removed.
     * @return this
     * @see #isWritten()
     */
    @NonNull
    DataItem<F> resetStatusToWritten() {
        mStatus = MASK_WRITTEN;
        return this;
    }

    /**
     * Resets the state of the item be TOUCHED. All other states are removed.
     *
     * @return this
     * @see #isTouched()
     * @see #wasTouched()
     */
    @NonNull
    DataItem<F> resetStatusToTouched() {
        boolean wasTouched = isTouched();
        mStatus = MASK_TOUCHED;

        if (!wasTouched) {
            wasTouched();
        }

        return this;
    }

    /**
     * Sets the item status to contain WRITTEN. Other states are kept.
     * @return this
     * @see #isWritten()
     */
    @NonNull
    DataItem<F> setWritten() {
        mStatus |= MASK_WRITTEN;
        return this;
    }

    /**
     * Sets the item status to contain REMOVED. Other states are kept.
     * @return this
     * @see #isRemoved()
     */
    @NonNull
    DataItem<F> setRemoved() {
        mStatus |= MASK_REMOVED;
        return this;
    }

    /**
     * Sets the item status to contain TOUCHED. Other states are kept.
     * @return this
     * @see #isTouched()
     */
    @NonNull
    DataItem<F> setTouched() {
        if (!isTouched()) {
            mStatus |= MASK_TOUCHED;
            wasTouched();
        }

        return this;
    }

    /**
     * Returns whether the item status contains REMOVED.
     * @return <code>true</code> if removed
     */
    boolean isRemoved() {
        return (mStatus & MASK_REMOVED) != 0;
    }

    /**
     * Returns whether the item status contains TOUCHED
     * @return <code>true</code> if touched
     */
    boolean isTouched() {
        return (mStatus & MASK_TOUCHED) != 0;
    }

    /**
     * Returns whether the item status contains WRITTEN
     * @return <code>true</code> if written
     */
    boolean isWritten() {
        return (mStatus & MASK_WRITTEN) != 0;
    }


    /**
     * Obtains the full status of the data item; should not generally be used except
     * for debug purposes.
     * @return the internal representation
     */
    protected int getStatus() {
        return mStatus;
    }

    /**
     * Returns the key for this item. They key uniquely identifies this item.
     */
    public String getKey() {
        return mName;
    }

    /**
     * Overridden in ResourceItem, which adds the type attribute.
     */
    void addExtraAttributes(Document document, Node node, String namespaceUri) {
        // nothing
    }

    /**
     * Returns a node that describes additional properties of this {@link DataItem}.
     * If not <code>null</code>, it will be persisted in the merger XML blob and can be used
     * used to restore the exact state of this item. If <code>null</code> then the state of this
     * item will not be persisted.
     *
     * <p>The default implementation returns <code>null</code>.</p>
     */
    @Nullable
    Node getDetailsXml(Document document) {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DataItem other = (DataItem) o;

        if (!mName.equals(other.mName)) {
            return false;
        }
        DataFile source = getSourceFile();
        DataFile otherSource = other.getSourceFile();
        if (source == otherSource) {
            return true;
        }
        if ((source == null) != (otherSource == null)) {
            return false;
        }
        return source.getFile().equals(otherSource.getFile());
    }

    @Override
    public int hashCode() {
        return mName.hashCode();
    }

    /**
     * Hook invoked when the data item has been touched. The default implementation does nothing.
     */
    protected void wasTouched() {}

    /**
     * Returns the {@link File} from which this {@link DataItem} was created, if known. For some
     * subclasses of {@link DataItem}, the file is known even if {@link #getSourceFile()} returns null.
     */
    @Nullable
    public File getFile() {
        F source = getSourceFile();
        return source == null ? null : source.getFile();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this.getClass())
                .add("name", mName)
                .add("sourceFile", mSourceFile)
                .add("isRemoved", isRemoved())
                .add("isTouched", isTouched())
                .add("isWritten", isWritten())
                .toString();
    }
}
