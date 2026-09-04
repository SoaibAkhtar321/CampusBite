package com.campusbite.app.ui.screens.shopkeeper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.campusbite.app.ui.viewmodel.ShopkeeperViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopkeeperAnalyticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ShopkeeperViewModel = hiltViewModel()
) {
    val analyticsState by viewModel.analyticsState.collectAsStateWithLifecycle()


    LaunchedEffect(Unit) {
        viewModel.loadAnalytics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sales & Analytics",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.loadAnalytics()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        if (analyticsState.isLoading) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!analyticsState.error.isNullOrBlank()) {
                ErrorCard(
                    message = analyticsState.error.orEmpty()
                )
            }

            AnalyticsPeriodCard(
                title = "Today",
                sales = analyticsState.todaySales,
                orders = analyticsState.todayOrders,
                verified = analyticsState.todayVerified,
                cancelled = analyticsState.todayCancelled,
                pending = analyticsState.todayPendingVerification
            )

            AnalyticsPeriodCard(
                title = "This Month",
                sales = analyticsState.monthSales,
                orders = analyticsState.monthOrders,
                verified = analyticsState.monthVerified,
                cancelled = analyticsState.monthCancelled,
                pending = analyticsState.monthPendingVerification
            )

            AnalyticsPeriodCard(
                title = "Lifetime",
                sales = analyticsState.lifetimeSales,
                orders = analyticsState.lifetimeOrders,
                verified = analyticsState.lifetimeVerified,
                cancelled = analyticsState.lifetimeCancelled,
                pending = analyticsState.lifetimePendingVerification,
                highlight = true
            )
        }
    }


}

@Composable
private fun ErrorCard(
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun AnalyticsPeriodCard(
    title: String,
    sales: Double,
    orders: Int,
    verified: Int,
    cancelled: Int,
    pending: Int,
    highlight: Boolean = false
) {
    val containerColor = if (highlight) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }


    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (highlight) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = "₹${"%.2f".format(sales)}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "$orders total orders",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            HorizontalDivider()

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(
                    label = "Verified",
                    value = verified.toString(),
                    positive = true
                )

                MetricItem(
                    label = "Cancelled",
                    value = cancelled.toString(),
                    negative = cancelled > 0
                )

                MetricItem(
                    label = "Pending",
                    value = pending.toString(),
                    warning = pending > 0
                )
            }
        }
    }


}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    positive: Boolean = false,
    negative: Boolean = false,
    warning: Boolean = false
) {
    val valueColor = when {
        negative -> MaterialTheme.colorScheme.error
        warning -> MaterialTheme.colorScheme.tertiary
        positive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }


    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }


}
