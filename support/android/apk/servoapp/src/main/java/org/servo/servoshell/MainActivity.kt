/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package org.servo.servoshell

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch
import org.servo.servoview.ContainerRegistry
import org.servo.servoview.Servo
import org.servo.servoview.ServoNavigator

class MainActivity : ComponentActivity(), Servo.Client {
    private val urlTextFieldState = TextFieldState()
    private var isRefreshing by mutableStateOf(false)
    private var alertMessage by mutableStateOf<String?>(null)

    private lateinit var containerManager: ContainerManager
    private var activeContainer by mutableStateOf("")
    private var showContainers by mutableStateOf(false)
    private var showCreateContainer by mutableStateOf(false)
    private var newContainerName by mutableStateOf("")

    private val navigators = mutableMapOf<String, ServoNavigator>()
    private val historyManagers = mutableMapOf<String, HistoryManager>()
    private val currentUrls = mutableMapOf<String, String>()
    private val currentTitles = mutableMapOf<String, String>()

    private class Settings(preferences: SharedPreferences) {
        var experimental = preferences.getBoolean("experimental", false)
    }

    private fun preferencesFor(id: String): SharedPreferences =
        getSharedPreferences("servo_container_$id", MODE_PRIVATE)

    private fun navigatorFor(id: String): ServoNavigator = navigators.getOrPut(id) { ServoNavigator() }
    private fun historyFor(id: String): HistoryManager = historyManagers.getOrPut(id) { HistoryManager(this, id) }

    private fun switchContainer(id: String) {
        activeContainer = id
        containerManager.activeId = id
        showContainers = false
    }

    private fun deleteContainer(id: String) {
        ContainerRegistry.kill(id)
        navigators.remove(id)
        historyManagers.remove(id)
        currentUrls.remove(id)
        currentTitles.remove(id)
        containerManager.remove(id)
        if (activeContainer == id) switchContainer(containerManager.activeId)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        containerManager = ContainerManager(this)
        if (containerManager.containers.isEmpty()) {
            containerManager.create("Personal")
        }
        val restored = containerManager.activeId
        activeContainer = if (restored.isNotEmpty() && containerManager.containers.any { it.id == restored }) {
            restored
        } else {
            containerManager.containers.first().id
        }
        containerManager.activeId = activeContainer

        var experimentalModeEnabled by mutableStateOf(Settings(preferencesFor(activeContainer)).experimental)

        val historyActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                (resultCode, data) ->
                if (resultCode == RESULT_OK && data != null) {
                    val url = data.getStringExtra("url")
                    if (!url.isNullOrEmpty()) {
                        urlTextFieldState.edit { replace(0, length, url) }
                        navigatorFor(activeContainer).navigate(urlTextFieldState.text.toString())
                    }
                }
            }

        setContent {
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                experimentalModeEnabled = Settings(preferencesFor(activeContainer)).experimental
            }
            LaunchedEffect(activeContainer) {
                urlTextFieldState.edit { replace(0, length, currentUrls[activeContainer].orEmpty()) }
            }

            val isWindowWidthAtLeastMedium =
                currentWindowAdaptiveInfo()
                    .windowSizeClass
                    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

            val servoFocusRequester = remember { FocusRequester() }
            val navigator = remember(activeContainer) { navigatorFor(activeContainer) }

            Scaffold(
                topBar = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showContainers = true }) {
                            Icon(
                                painterResource(R.drawable.containers),
                                stringResource(R.string.containers),
                            )
                        }
                        if (isWindowWidthAtLeastMedium) {
                            IconButton(
                                onClick = { navigator.back() },
                                enabled = navigator.canGoBack,
                            ) {
                                Icon(
                                    painterResource(R.drawable.arrow_back),
                                    stringResource(R.string.history_back),
                                )
                            }
                            IconButton(
                                onClick = { navigator.forward() },
                                enabled = navigator.canGoForward,
                            ) {
                                Icon(
                                    painterResource(R.drawable.arrow_forward),
                                    stringResource(R.string.history_forward),
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (isRefreshing) navigator.stop() else navigator.reload()
                                },
                            ) {
                                if (isRefreshing) {
                                    Icon(
                                        painterResource(R.drawable.cancel),
                                        stringResource(R.string.cancel),
                                    )
                                } else {
                                    Icon(
                                        painterResource(R.drawable.refresh),
                                        stringResource(R.string.refresh),
                                    )
                                }
                            }
                        }
                        Omnibox(
                            urlTextFieldState,
                            onSearch = { search ->
                                navigator.navigate(search)
                                servoFocusRequester.requestFocus()
                            },
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                        )
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 10.dp).size(20.dp),
                            )
                        }
                        if (isWindowWidthAtLeastMedium) {
                            IconButton(onClick = ::onSettingsMenuItemClicked) {
                                Icon(
                                    painterResource(R.drawable.settings),
                                    stringResource(R.string.options),
                                )
                            }
                            IconButton(onClick = { onHistoryMenuItemClicked(historyActivityResultLauncher) }) {
                                Icon(
                                    painterResource(R.drawable.history),
                                    stringResource(R.string.history_title),
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    if (!isWindowWidthAtLeastMedium) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = false,
                                enabled = navigator.canGoBack,
                                onClick = { navigator.back() },
                                icon = { Icon(painterResource(R.drawable.arrow_back), null) },
                                label = { Text(stringResource(R.string.history_back)) },
                            )
                            NavigationBarItem(
                                selected = false,
                                enabled = navigator.canGoForward,
                                onClick = { navigator.forward() },
                                icon = { Icon(painterResource(R.drawable.arrow_forward), null) },
                                label = { Text(stringResource(R.string.history_forward)) },
                            )
                            if (isRefreshing) {
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { navigator.stop() },
                                    icon = { Icon(painterResource(R.drawable.cancel), null) },
                                    label = { Text(stringResource(R.string.cancel)) },
                                )
                            } else {
                                NavigationBarItem(
                                    selected = false,
                                    onClick = { navigator.reload() },
                                    icon = { Icon(painterResource(R.drawable.refresh), null) },
                                    label = { Text(stringResource(R.string.refresh)) },
                                )
                            }
                            NavigationBarItem(
                                selected = false,
                                onClick = ::onSettingsMenuItemClicked,
                                icon = { Icon(painterResource(R.drawable.settings), null) },
                                label = { Text(stringResource(R.string.options)) },
                            )
                            NavigationBarItem(
                                selected = false,
                                onClick = { onHistoryMenuItemClicked(historyActivityResultLauncher) },
                                icon = { Icon(painterResource(R.drawable.history), null) },
                                label = { Text(stringResource(R.string.history_title)) },
                            )
                        }
                    }
                },
            ) { innerPadding ->
                Servo(
                    containerId = activeContainer,
                    experimentalModeEnabled = experimentalModeEnabled,
                    args = intent.getStringExtra("servoargs"),
                    initialUrl = if (Intent.ACTION_VIEW == intent.action) intent.data.toString() else null,
                    logStr = intent.getStringExtra("servolog"),
                    client = this,
                    navigator = navigator,
                    modifier = Modifier.padding(innerPadding).focusRequester(servoFocusRequester),
                )
                LaunchedEffect(servoFocusRequester) { servoFocusRequester.requestFocus() }
                alertMessage?.let { message ->
                    AlertDialog(
                        onDismissRequest = { alertMessage = null },
                        confirmButton = {
                            TextButton(onClick = { alertMessage = null }) {
                                Text(stringResource(android.R.string.ok))
                            }
                        },
                        text = { Text(message) },
                    )
                }
            }

            if (showContainers) {
                ModalBottomSheet(onDismissRequest = { showContainers = false }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.containers),
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            stringResource(R.string.container_note),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                            items(containerManager.containers, key = { it.id }) { container ->
                                ListItem(
                                    headlineContent = { Text(container.name) },
                                    leadingContent = {
                                        RadioButton(
                                            selected = container.id == activeContainer,
                                            onClick = { switchContainer(container.id) },
                                        )
                                    },
                                    trailingContent = {
                                        if (containerManager.containers.size > 1) {
                                            IconButton(onClick = { deleteContainer(container.id) }) {
                                                Icon(
                                                    painterResource(R.drawable.delete),
                                                    stringResource(R.string.cancel),
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                        Button(
                            onClick = {
                                showContainers = false
                                showCreateContainer = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.new_container))
                        }
                    }
                }
            }

            if (showCreateContainer) {
                AlertDialog(
                    onDismissRequest = { showCreateContainer = false },
                    title = { Text(stringResource(R.string.new_container)) },
                    text = {
                        TextField(
                            value = newContainerName,
                            onValueChange = { newContainerName = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.container_name)) },
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (newContainerName.isNotBlank()) {
                                switchContainer(containerManager.create(newContainerName.trim()).id)
                                newContainerName = ""
                            }
                            showCreateContainer = false
                        }) { Text(stringResource(R.string.create)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateContainer = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }

        val sdcard = getExternalFilesDir("")
        val host = sdcard!!.toPath().resolve("android_hosts").toString()
        try {
            Os.setenv("HOST_FILE", host, false)
        } catch (e: ErrnoException) {
            e.printStackTrace()
        }
    }

    private fun onSettingsMenuItemClicked() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun onHistoryMenuItemClicked(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(Intent(this, HistoryActivity::class.java))
    }

    override fun onAlert(message: String) {
        alertMessage = message
    }

    override fun onLoadStarted() {
        Log.i(TAG, "onLoadStarted: ")
        isRefreshing = true
    }

    override fun onLoadEnded() {
        Log.i(TAG, "onLoadEnded: ")
        val url = currentUrls[activeContainer].orEmpty()
        if (url.isNotEmpty()) {
            historyFor(activeContainer).addEntry(url, currentTitles[activeContainer])
        }
        isRefreshing = false
    }

    override fun onTitleChanged(title: String) {
        currentTitles[activeContainer] = title
    }

    override fun onUrlChanged(url: String) {
        urlTextFieldState.edit { replace(0, length, url) }
        currentUrls[activeContainer] = url
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Omnibox(
    textFieldState: TextFieldState,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchBarState = rememberSearchBarState()

    SearchBar(
        state = searchBarState,
        inputField = {
            val coroutineScope = rememberCoroutineScope()

            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = onSearch,
                modifier = Modifier.onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        coroutineScope.launch { textFieldState.edit { selectAll() } }
                    }
                },
                placeholder = { Text(stringResource(R.string.url_or_search)) },
            )
        },
        modifier = modifier,
    )
}
