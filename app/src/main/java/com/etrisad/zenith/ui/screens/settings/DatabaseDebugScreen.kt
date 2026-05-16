package com.etrisad.zenith.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.components.UsageHistoryList
import com.etrisad.zenith.ui.components.ZenithContainedLoadingIndicator
import com.etrisad.zenith.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DatabaseDebugScreen(
    viewModel: HomeViewModel,
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    val historyData by viewModel.fullUsageHistory.collectAsState(initial = emptyList())

    if (historyData.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ZenithContainedLoadingIndicator(
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Analysing database history...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        UsageHistoryList(
            historyData = historyData,
            formatDuration = { viewModel.formatDuration(it) },
            modifier = Modifier.padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            )
        )
    }
}
