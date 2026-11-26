package com.sharek.macromandate.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sharek.macromandate.model.MealEntry
import com.sharek.macromandate.ui.components.SharedMealFormFields
import com.sharek.macromandate.ui.components.TerminalMealRow
import com.sharek.macromandate.ui.theme.MacroMandateTheme
import com.sharek.macromandate.ui.theme.TerminalTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class MacroMandateComposeUiTest {

    /**
     * Custom Compose test harness that drives Compose semantics via [AndroidComposeUiTestEnvironment]
     * without entering `kotlinx.coroutines.test.runTest` (which fails on split test/app classloaders
     * when D8 folds `ServiceLoader(CoroutineExceptionHandler)` in the target APK).
     */
    @OptIn(ExperimentalTestApi::class)
    private fun runComposeTest(block: ComposeUiTest.() -> Unit) {
        lateinit var scenario: ActivityScenario<androidx.activity.ComponentActivity>
        val environment = object : AndroidComposeUiTestEnvironment<androidx.activity.ComponentActivity>(
            effectContext = kotlin.coroutines.EmptyCoroutineContext
        ) {
            override val activity: androidx.activity.ComponentActivity
                get() {
                    var act: androidx.activity.ComponentActivity? = null
                    scenario.onActivity { act = it }
                    return checkNotNull(act) { "Activity was not initialized" }
                }
        }
        val registryField = AndroidComposeUiTestEnvironment::class.java
            .getDeclaredField("composeRootRegistry")
            .apply { isAccessible = true }
        val registry = registryField.get(environment)
        val setupMethod = registry.javaClass.getDeclaredMethod("setupRegistry").apply { isAccessible = true }
        val tearDownMethod = registry.javaClass.getDeclaredMethod("tearDownRegistry\$ui_test").apply { isAccessible = true }
        val idlingStrategyField = AndroidComposeUiTestEnvironment::class.java
            .getDeclaredField("idlingStrategy")
            .apply { isAccessible = true }
        val idlingStrategy = idlingStrategyField.get(environment)
        val runUntilIdleMethod = idlingStrategy.javaClass.getMethod("runUntilIdle")

        try {
            scenario = ActivityScenario.launch(androidx.activity.ComponentActivity::class.java)
            runUntilIdleMethod.invoke(idlingStrategy)
            setupMethod.invoke(registry)
            environment.test.block()
        } finally {
            runCatching { tearDownMethod.invoke(registry) }
            runCatching { environment.cancelAndRecreateRecomposer() }
            runCatching { scenario.close() }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dashboardIntakeCardExposesMergedAccessibilitySummaryAndFractionalMacros() = runComposeTest {
        setContent {
            MacroMandateTheme(terminalTheme = TerminalTheme.MATRIX_GREEN) {
                DashboardIntakeCard(
                    totalCalories = 1450,
                    target = 2500,
                    proteinGrams = 95.5f,
                    carbsGrams = 130.0f,
                    fatGrams = 42.3f,
                    isViewingToday = true,
                    selectedDate = LocalDate.of(2026, 11, 25)
                )
            }
        }

        onNode(
            hasContentDescription(
                value = "1450 of 2500 calories",
                substring = true
            )
        ).assertExists()

        onNode(
            hasContentDescription(
                value = "Protein 95.5 grams",
                substring = true
            )
        ).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun sharedMealFormFieldsSupportsCommaDecimalsAndMacroCalorieEstimate() = runComposeTest {
        var caloriesValue = ""
        var proteinValue = ""

        setContent {
            var name by remember { mutableStateOf("Salmon Bowl") }
            var cal by remember { mutableStateOf("") }
            var p by remember { mutableStateOf("30") }
            var c by remember { mutableStateOf("40") }
            var f by remember { mutableStateOf("10") }
            var liquid by remember { mutableStateOf(false) }

            MacroMandateTheme {
                SharedMealFormFields(
                    foodName = name,
                    onFoodNameChange = { name = it },
                    caloriesStr = cal,
                    onCaloriesChange = {
                        cal = it
                        caloriesValue = it
                    },
                    proteinStr = p,
                    onProteinChange = {
                        p = it
                        proteinValue = it
                    },
                    carbsStr = c,
                    onCarbsChange = { c = it },
                    fatStr = f,
                    onFatChange = { f = it },
                    isLiquid = liquid,
                    onLiquidChange = { liquid = it }
                )
            }
        }

        // Verify TalkBack semantics for macro inputs
        onNodeWithContentDescription("Protein in grams").assertExists()
        onNodeWithContentDescription("Carbs in grams").assertExists()
        onNodeWithContentDescription("Fat in grams").assertExists()

        // Tap the Atwater calorie calculation helper (30*4 + 40*4 + 10*9 = 370 kcal)
        onNodeWithContentDescription("Set calories from macros: 370 calories").performClick()
        assertEquals("370", caloriesValue)

        // Verify comma decimal input is sanitized to period decimal
        onNodeWithContentDescription("Protein in grams").performTextReplacement("42,5")
        assertEquals("42.5", proteinValue)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun manualMealDialogDisablesSaveUntilNameAndCaloriesAreProvided() {
        // 1. Blank manual dialog disables SAVE MEAL
        runComposeTest {
            setContent {
                MacroMandateTheme {
                    ManualMealDialog(
                        estimate = null,
                        onDismiss = {},
                        onSave = { _, _, _, _, _, _, _, complete -> complete(true) }
                    )
                }
            }
            onNodeWithText("LOG MEAL MANUALLY").assertExists()
            onNodeWithText("SAVE MEAL").assertIsNotEnabled()
        }

        // 2. Complete estimate with positive calories enables SAVE MEAL and invokes callback
        var savedMealName: String? = null
        var savedCalories: Int? = null
        runComposeTest {
            setContent {
                MacroMandateTheme {
                    ManualMealDialog(
                        estimate = MealEntry(
                            id = "draft-2",
                            timestamp = 1_700_000_000_000L,
                            imageUri = null,
                            foodName = "Grilled Chicken",
                            calories = 480,
                            proteinGrams = 42f,
                            carbsGrams = 15f,
                            fatGrams = 12f,
                            isLiquid = false
                        ),
                        onDismiss = {},
                        onSave = { name, cal, _, _, _, _, _, complete ->
                            savedMealName = name
                            savedCalories = cal
                            complete(true)
                        }
                    )
                }
            }
            onNodeWithText("REVIEW NUTRITION ESTIMATE").assertExists()
            onNodeWithText("SAVE MEAL").assertIsEnabled()
            onNodeWithText("SAVE MEAL").performClick()
        }

        assertEquals("Grilled Chicken", savedMealName)
        assertEquals(480, savedCalories)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun terminalMealRowExposesDeleteActionSemanticsWithMealName() = runComposeTest {
        var deleteClicked = false
        val meal = MealEntry(
            id = "meal-1",
            timestamp = 1_700_000_000_000L,
            imageUri = null,
            foodName = "Avocado Toast",
            calories = 320,
            proteinGrams = 12.5f,
            carbsGrams = 34f,
            fatGrams = 16.2f,
            isLiquid = false
        )

        setContent {
            MacroMandateTheme {
                TerminalMealRow(
                    entry = meal,
                    onClick = {},
                    onDelete = { deleteClicked = true }
                )
            }
        }

        onNodeWithContentDescription("Delete Avocado Toast").performClick()
        assertTrue(deleteClicked)
    }
}
