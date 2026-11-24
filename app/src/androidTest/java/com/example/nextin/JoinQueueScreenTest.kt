package com.example.nextin

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.example.nextin.ui.customer.JoinQueueScreen
import org.junit.Rule
import org.junit.Test

class JoinQueueScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missingBusinessShowsErrorInsteadOfJoinForm() {
        composeRule.setContent {
            MaterialTheme {
                JoinQueueScreen(
                    business = null,
                    hasActiveEntry = false,
                    busy = false,
                    error = null,
                    onJoin = { _, _ -> },
                    onMyQueue = {},
                    onBack = {}
                )
            }
        }

        composeRule
            .onNodeWithText("Business not found. Please go back.")
            .assertIsDisplayed()

        composeRule
            .onNodeWithText("Save your spot")
            .assertDoesNotExist()
    }
}
