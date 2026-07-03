package com.spendwise.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.spendwise.ui.api.TrendBucketResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SHORT_DATE = DateTimeFormatter.ofPattern("dd MMM").withZone(ZoneId.systemDefault())

/**
 * E9-S2-T1 — dashboard: alerts panel (top), recommendations feed, budget progress with
 * category drilldown, 30-day trend chart (docs/user_flows.md "Reviewing Transactions").
 */
@Composable
fun DashboardScreen(
    onCategoryClick: (Int) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- Alerts panel ---
        SectionCard(title = "Alerts", section = state.alerts) { alerts ->
            if (alerts.isEmpty()) {
                EmptyHint("No unread alerts")
            } else {
                alerts.forEach { alert ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(alertHeadline(alert.type), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                alert.priority.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (alert.priority.equals("high", true)) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            if (alert.type == "recurring_payment") {
                                TextButton(onClick = { viewModel.confirmRecurringPayment(alert.id) }) {
                                    Text("Track as subscription")
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.dismissAlert(alert.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss alert")
                        }
                    }
                }
            }
        }

        // --- Savings recommendations feed ---
        SectionCard(title = "Savings ideas", section = state.recommendations) { recs ->
            if (recs.isEmpty()) {
                EmptyHint("Nothing right now — recommendations appear when spending patterns change")
            } else {
                recs.forEach { rec ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(rec.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.dismissRecommendation(rec.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss recommendation")
                        }
                    }
                }
            }
        }

        // --- Budget progress (tappable category rows → transactions drilldown) ---
        SectionCard(title = "Budgets this month", section = state.budgetProgress) { progress ->
            if (progress.isEmpty()) {
                EmptyHint("No budgets set — add them on the Budget tab")
            } else {
                progress.forEach { p ->
                    val name = state.categories.firstOrNull { it.id == p.categoryId }?.name ?: "Category ${p.categoryId}"
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onCategoryClick(p.categoryId) }
                            .padding(vertical = 6.dp),
                    ) {
                        Row {
                            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                "₹%,.0f / ₹%,.0f".format(p.spent, p.monthlyLimit),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (p.percentSpent / 100.0).coerceIn(0.0, 1.0).toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                            color = when {
                                p.percentSpent >= 100 -> MaterialTheme.colorScheme.error
                                p.percentSpent >= 80 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
        }

        // --- Spend trend (single series — the title names it; no legend needed) ---
        SectionCard(title = "Spending — last 30 days", section = state.trend) { buckets ->
            if (buckets.isEmpty()) {
                EmptyHint("Not enough data yet")
            } else {
                TrendChart(buckets)
            }
        }
    }
}

@Composable
private fun <T> SectionCard(
    title: String,
    section: DashboardViewModel.Section<T>,
    content: @Composable (T) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            when {
                section.isLoading -> CircularProgressIndicator(Modifier.padding(8.dp))
                section.error != null -> Text(
                    section.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                section.data != null -> content(section.data)
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Minimal single-series line chart: 2dp line in the theme's series color, soft area fill,
 * recessive baseline, selective labels only (peak value + endpoint dates, in text colors).
 */
@Composable
private fun TrendChart(buckets: List<TrendBucketResponse>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val values = buckets.map { it.totalSpend }
    val max = values.max().takeIf { it > 0 } ?: 1.0

    Column {
        Text(
            "Peak ₹%,.0f".format(values.max()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val stepX = if (buckets.size > 1) size.width / (buckets.size - 1) else size.width
            val points = values.mapIndexed { i, v ->
                Offset(x = i * stepX, y = size.height * (1f - (v / max).toFloat()))
            }

            // Recessive baseline.
            drawLine(
                color = baselineColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )

            if (points.size > 1) {
                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                // Soft area fill under the line.
                val areaPath = Path().apply {
                    addPath(linePath)
                    lineTo(points.last().x, size.height)
                    lineTo(points.first().x, size.height)
                    close()
                }
                drawPath(
                    areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                    ),
                )
                drawPath(linePath, color = lineColor, style = Stroke(width = 2.dp.toPx()))
            } else {
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = points.first())
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatBucketDate(buckets.first().bucketStart),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatBucketDate(buckets.last().bucketStart),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatBucketDate(iso: String): String =
    runCatching { SHORT_DATE.format(Instant.parse(iso)) }.getOrDefault(iso)

private fun alertHeadline(type: String): String = when (type) {
    "mid_month_budget" -> "50% of your monthly budget is spent"
    "category_approaching_limit" -> "A category budget is at 80%"
    "category_overspend" -> "A category budget was exceeded"
    "recurring_payment" -> "Recurring payment detected"
    else -> type.replace('_', ' ').replaceFirstChar(Char::uppercase)
}
