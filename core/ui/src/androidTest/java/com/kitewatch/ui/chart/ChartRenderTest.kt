package com.kitewatch.ui.chart

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kitewatch.domain.model.Paisa
import com.kitewatch.ui.theme.KiteWatchTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ChartRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleLineData =
        listOf(
            LocalDate.of(2024, 1, 1) to Paisa(-10_000L),
            LocalDate.of(2024, 2, 1) to Paisa(5_000L),
            LocalDate.of(2024, 3, 1) to Paisa(20_000L),
        )

    private val sampleBarData =
        listOf(
            "Jan" to Paisa(-5_000L),
            "Feb" to Paisa(10_000L),
            "Mar" to Paisa(8_000L),
        )

    private val sampleChargesData =
        listOf(
            "STT" to Paisa(3_000L),
            "Exchange" to Paisa(1_500L),
            "GST" to Paisa(500L),
        )

    @Test
    fun pnlLineChart_rendersWithoutCrash() {
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                PnlLineChart(
                    dataPoints = sampleLineData,
                    modifier = Modifier.size(300.dp, 150.dp),
                )
            }
        }
        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun monthlyBarChart_rendersWithoutCrash() {
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                MonthlyBarChart(
                    dataPoints = sampleBarData,
                    modifier = Modifier.size(300.dp, 150.dp),
                )
            }
        }
        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun chargesBreakdownChart_rendersWithoutCrash() {
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                ChargesBreakdownChart(
                    segments = sampleChargesData,
                    modifier = Modifier.size(300.dp, 60.dp),
                )
            }
        }
        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun pnlPieChart_rendersWithoutCrash() {
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                PnlPieChart(
                    realizedPnl = Paisa(20_000L),
                    totalCharges = Paisa(5_000L),
                    modifier = Modifier.size(200.dp),
                )
            }
        }
        composeTestRule.onRoot().assertIsDisplayed()
    }

    @Test
    fun pnlLineChart_emptyData_rendersWithoutCrash() {
        composeTestRule.setContent {
            KiteWatchTheme(darkTheme = false) {
                PnlLineChart(
                    dataPoints = emptyList(),
                    modifier = Modifier.size(300.dp, 150.dp),
                )
            }
        }
        // Empty data returns early — root still exists
        composeTestRule.onRoot().assertIsDisplayed()
    }
}
