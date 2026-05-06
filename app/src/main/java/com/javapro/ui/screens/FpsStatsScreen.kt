package com.javapro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.javapro.fps.ui.ChartContainer
import com.javapro.fps.ui.FpsViewModel
import com.javapro.fps.ui.SummaryGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FpsStatsScreen(navController: NavController, viewModel: FpsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("FPS Stats Monitor", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (uiState.isRunning) {
                        Text(
                            text = uiState.activeBackend,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.toggleMonitoring("com.android.settings") }, // Example target
                containerColor = if (uiState.isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (uiState.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (uiState.isRunning) "Stop" else "Start"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                SummaryGrid(fpsStats = uiState.fpsStats, systemStats = uiState.systemStats)
            }

            item {
                ChartContainer(
                    title = "FPS",
                    currentValue = "%.1f".format(uiState.fpsStats.currentFps),
                    data = viewModel.fpsHistory.toList(),
                    lineColor = Color(0xFF4CAF50),
                    maxY = 125f
                )
            }

            item {
                ChartContainer(
                    title = "Frame Time",
                    currentValue = "%.1f ms".format(uiState.fpsStats.frameTimeMs),
                    data = viewModel.frameTimeHistory.toList(),
                    lineColor = Color(0xFF2196F3),
                    maxY = 50f
                )
            }

            item {
                ChartContainer(
                    title = "CPU Usage",
                    currentValue = "%.0f%%".format(uiState.systemStats.cpuUsage),
                    data = viewModel.cpuHistory.toList(),
                    lineColor = Color(0xFFFF9800),
                    maxY = 100f
                )
            }

            item {
                ChartContainer(
                    title = "GPU Usage",
                    currentValue = "%.0f%%".format(uiState.systemStats.gpuUsage),
                    data = viewModel.gpuHistory.toList(),
                    lineColor = Color(0xFFE91E63),
                    maxY = 100f
                )
            }

            item {
                ChartContainer(
                    title = "Temperature",
                    currentValue = "%.1f °C".format(uiState.systemStats.temperature),
                    data = viewModel.tempHistory.toList(),
                    lineColor = Color(0xFFF44336),
                    maxY = 80f,
                    minY = 20f
                )
            }

            item {
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}
