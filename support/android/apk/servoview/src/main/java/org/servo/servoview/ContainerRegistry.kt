/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.servo.servoview

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-level owner of all browser container sessions.
 *
 * Sessions live here rather than in a Composable or Activity, so closing the screen, rotating
 * the device or sending the app to the background never destroys a page. A session is destroyed
 * only through [kill] or [killAll], i.e. when the user explicitly asks for it.
 */
object ContainerRegistry {
    private const val MAX_CONTAINERS = 256

    private val engines = ConcurrentHashMap<String, Servo>()
    private val slots = ConcurrentHashMap<String, Int>()
    private val nextSlot = AtomicInteger(0)
    private val freeSlots = ConcurrentLinkedQueue<Int>()

    /** Each container owns one thread-bound slot in the native engine. */
    fun slotFor(containerId: String): Int =
        slots.computeIfAbsent(containerId) {
            freeSlots.poll() ?: (nextSlot.getAndIncrement() % MAX_CONTAINERS)
        }

    fun getOrCreate(
        containerId: String,
        args: String?,
        url: String?,
        logStr: String?,
        experimentalMode: Boolean,
        client: Servo.Client,
        context: Context,
        navigator: ServoNavigator,
        onImeShow: () -> Unit,
        onImeHide: () -> Unit,
    ): Servo {
        engines[containerId]?.let { existing ->
            existing.update(client, navigator, onImeShow, onImeHide)
            return existing
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val engine = Servo(
            args = args,
            url = url,
            logStr = logStr,
            experimentalMode = experimentalMode,
            containerId = containerId,
            slot = slotFor(containerId),
            scope = scope,
            client = client,
            context = context,
            navigator = navigator,
            onImeShow = onImeShow,
            onImeHide = onImeHide,
        )
        val raced = engines.putIfAbsent(containerId, engine)
        if (raced != null) {
            engine.kill()
            raced.update(client, navigator, onImeShow, onImeHide)
            return raced
        }
        return engine
    }

    fun kill(containerId: String) {
        engines.remove(containerId)?.kill()
        slots.remove(containerId)?.let { freeSlots.add(it) }
    }

    fun killAll() {
        engines.values.forEach { it.kill() }
        engines.clear()
        freeSlots.addAll(slots.values)
        slots.clear()
    }
}
