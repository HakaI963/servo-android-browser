/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.servo.servoshell

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** A completely separate browser. Containers never share site data, history or preferences. */
@Serializable
data class BrowserContainer(
    val id: String,
    val name: String,
    val color: Long,
    val createdAt: Long,
)

class ContainerManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var containers: List<BrowserContainer> = load()
        private set

    var activeId: String
        get() = prefs.getString(KEY_ACTIVE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_ACTIVE, value).apply()

    fun create(name: String): BrowserContainer {
        val container = BrowserContainer(
            id = UUID.randomUUID().toString(),
            name = name,
            color = COLORS[containers.size % COLORS.size],
            createdAt = System.currentTimeMillis(),
        )
        containers = containers + container
        save()
        activeId = container.id
        return container
    }

    fun remove(id: String) {
        if (containers.size <= 1) return
        containers = containers.filterNot { it.id == id }
        save()
        if (activeId == id) activeId = containers.last().id
    }

    fun rename(id: String, name: String) {
        containers = containers.map { if (it.id == id) it.copy(name = name) else it }
        save()
    }

    private fun load(): List<BrowserContainer> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<BrowserContainer>>(raw) }
            .getOrDefault(emptyList())
    }

    private fun save() {
        prefs.edit().putString(KEY_LIST, json.encodeToString(containers)).apply()
    }

    companion object {
        private const val PREFS_NAME = "servo_containers"
        private const val KEY_LIST = "containers"
        private const val KEY_ACTIVE = "active"
        val COLORS = listOf(
            0xFF5FC79AL, 0xFFA78BD2L, 0xFFE0A458L, 0xFF6FA8DCL, 0xFFD9736AL, 0xFF7FB069L,
        )
    }
}
