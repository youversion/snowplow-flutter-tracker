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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginEntitiesStoreTest {

    private fun entity(id: String) = listOf(SelfDescribingJson("iglu:com.example/guest/jsonschema/1-0-0", mapOf("id" to id)))

    private fun idAt(store: PluginEntitiesStore, timestamp: Long?): String? =
        (store.entitiesAt(timestamp).firstOrNull()?.map?.get("data") as? Map<*, *>)?.get("id") as? String

    @Test
    fun returnsTheValueInEffectWhenTheEventWasTracked() {
        val store = PluginEntitiesStore(entity("a"), now = 1_000)
        store.set(entity("b"), now = 2_000)
        store.set(entity("c"), now = 3_000)

        assertEquals("a", idAt(store, 1_999))
        assertEquals("b", idAt(store, 2_000))
        assertEquals("b", idAt(store, 2_999))
        assertEquals("c", idAt(store, 3_000))
        assertEquals("c", idAt(store, null))
    }

    @Test
    fun returnsTheOldestValueForEventsTrackedBeforeIt() {
        val store = PluginEntitiesStore(entity("a"), now = 1_000)
        store.set(entity("b"), now = 2_000)

        assertEquals("a", idAt(store, 500))
    }

    @Test
    fun keepsReplacedValuesForTheRetentionPeriod() {
        val store = PluginEntitiesStore(entity("a"), now = 1_000)
        store.set(entity("b"), now = 2_000)
        store.set(entity("c"), now = 2_000 + PluginEntitiesStore.RETENTION_MS)
        assertEquals("a", idAt(store, 1_500))

        store.set(entity("d"), now = 2_001 + PluginEntitiesStore.RETENTION_MS)
        // "a" was replaced more than the retention period ago
        assertEquals("b", idAt(store, 1_500))
    }

    @Test
    fun keepsAtMostTheMaximumNumberOfValues() {
        val store = PluginEntitiesStore(entity("0"), now = 0)
        for (i in 1..40) {
            store.set(entity("$i"), now = i.toLong())
        }

        assertEquals("${40 - PluginEntitiesStore.MAX_ENTRIES + 1}", idAt(store, 0))
        assertEquals("40", idAt(store, 40))
    }

    @Test
    fun allowsEmptyEntities() {
        val store = PluginEntitiesStore(emptyList(), now = 1_000)
        store.set(entity("a"), now = 2_000)

        assertTrue(store.entitiesAt(1_500).isEmpty())
        assertEquals("a", idAt(store, 2_500))
    }
}
