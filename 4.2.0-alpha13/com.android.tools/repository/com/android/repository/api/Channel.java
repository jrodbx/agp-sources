/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.impl.meta.CommonFactory;

import javax.xml.bind.annotation.XmlTransient;

/**
 * An update channel, e.g. Stable or Beta. Channels are ordered from more to less stable.
 */
@XmlTransient
public abstract class Channel implements Comparable<Channel> {

    /**
     * The default channel is the most stable.
     */
    public static final Channel DEFAULT = create(0);

    /**
     * Create a new {@code Channel} with the specified ID.
     *
     * @param id The id of the channel. If this channel will ever be marshalled the
     *           value must be between 0 and 9 or the xml will fail validation.
     */
    @NonNull
    public static Channel create(int id) {
        return RepoManager.getCommonModule().createLatestFactory().createChannelType(id);
    }

    /**
     * Gets the (possibly null) display name for this channel.
     */
    @Nullable
    protected abstract String getValue();

    /**
     * Sets the displayName for this channel.
     */
    public abstract void setValue(@Nullable String displayName);

    /**
     * Gets the display name for this channel. If none is specified, the id (in the format
     * {@code channel-N}) is returned.
     */
    @NonNull
    public String getDisplayName() {
        return getValue() == null ? getId() : getValue();
    }

    /**
     * Gets the string ID for this channel, in the format {@code channel-N}.
     */
    @NonNull
    public abstract String getId();

    /**
     * Sets the string ID for this channel. Must be in the form "channel-N".
     */
    public abstract void setId(@NonNull String id);

    @Override
    public int compareTo(Channel o) {
        return getId().compareTo(o.getId());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Channel && getId().equals(((Channel) obj).getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
}
