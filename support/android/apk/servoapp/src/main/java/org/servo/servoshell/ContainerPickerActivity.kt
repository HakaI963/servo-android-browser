/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package org.servo.servoshell

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.servo.servoview.ContainerRegistry

/** The app's home screen: a manager for independent browser containers. */
class ContainerPickerActivity : ComponentActivity() {
    private lateinit var containerManager: ContainerManager
    private var containers by mutableStateOf<List<BrowserContainer>>(emptyList())
    private var showCreate by mutableStateOf(false)
    private var name by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        containerManager = ContainerManager(this)
        if (containerManager.containers.isEmpty()) {
            containerManager.create("Personal")
        }
        containers = containerManager.containers

        setContent {
            ServoTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.containers)) })
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showCreate = true }) {
                            Icon(
                                painterResource(R.drawable.add),
                                stringResource(R.string.new_container),
                            )
                        }
                    },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            stringResource(R.string.containers_caption),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))

                        if (containers.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.no_containers),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(containers, key = { it.id }) { container ->
                                    ContainerCard(
                                        container = container,
                                        selected = container.id == containerManager.activeId,
                                        onOpen = { openContainer(container) },
                                        onDelete = { deleteContainer(container) },
                                    )
                                }
                            }
                        }
                    }

                    if (showCreate) {
                        AlertDialog(
                            onDismissRequest = { showCreate = false },
                            title = { Text(stringResource(R.string.new_container)) },
                            text = {
                                TextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.container_name)) },
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    if (name.isNotBlank()) {
                                        containerManager.create(name.trim())
                                        containers = containerManager.containers
                                        name = ""
                                    }
                                    showCreate = false
                                }) { Text(stringResource(R.string.create)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCreate = false }) {
                                    Text(stringResource(R.string.cancel))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::containerManager.isInitialized) containers = containerManager.containers
    }

    private fun openContainer(container: BrowserContainer) {
        containerManager.activeId = container.id
        ContainerRegistry.setActive(container.id)
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_CONTAINER_ID, container.id)
        }
        startActivity(intent)
    }

    private fun deleteContainer(container: BrowserContainer) {
        ContainerRegistry.kill(container.id)
        containerManager.remove(container.id)
        containers = containerManager.containers
    }
}

@Composable
private fun ContainerCard(
    container: BrowserContainer,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                else Modifier,
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(container.color.toInt())),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    container.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (selected) "Active" else "Separate browsing session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onOpen) {
                Text(stringResource(R.string.open))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.delete),
                    stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
