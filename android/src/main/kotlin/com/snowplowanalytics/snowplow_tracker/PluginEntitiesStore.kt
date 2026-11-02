// Copyright (c) 2022-present Snowplow Analytics Ltd. All rights reserved.
//
// This program is licensed to you under the Apache License Version 2.0,
// and you may not use this file except in compliance with the Apache License Version 2.0.
// You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the Apache License Version 2.0 is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
package com.snowplowanalytics.snowplow_tracker

import com.snowplowanalytics.snowplow.payload.SelfDescribingJson

/// Entities of a plugin and when each value took effect.
///
/// The tracker creates events in the order they are tracked but adds plugin
/// entities later on a thread pool, in no particular order. Keeping the
/// recent values lets each event get the value that was set when it was
/// tracked. Writes are serialized, reads take no lock.
class PluginEntitiesStore(entities: List<SelfDescribingJson>, now: Long = System.currentTimeMillis()) {

    private class Entry(val since: Long, val entities: List<SelfDescribingJson>)

    /// Values from oldest to newest, replaced as a whole on every write
    @Volatile
    private var history: List<Entry> = listOf(Entry(now, entities))

    @Synchronized
    fun set(entities: List<SelfDescribingJson>, now: Long = System.currentTimeMillis()) {
        val retained = history.filterIndexed { index, _ ->
            // a value is replaced when the next one takes effect
            index == history.size - 1 || now - history[index + 1].since <= RETENTION_MS
        }
        history = (retained + Entry(now, entities)).takeLast(MAX_ENTRIES)
    }

    /// Entities in effect at [timestamp], the current ones if it is null.
    ///
    /// Events from before the oldest value kept get the oldest value.
    fun entitiesAt(timestamp: Long?): List<SelfDescribingJson> {
        val entries = history
        if (timestamp == null) {
            return entries.last().entities
        }
        return (entries.lastOrNull { it.since <= timestamp } ?: entries.first()).entities
    }

    companion object {
        /// How long a value is kept after it is replaced
        const val RETENTION_MS = 60_000L
        const val MAX_ENTRIES = 16
    }
}
