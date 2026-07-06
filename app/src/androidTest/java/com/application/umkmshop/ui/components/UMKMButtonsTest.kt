package com.application.umkmshop.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.application.umkmshop.ui.theme.UMKMShopTheme
import org.junit.Rule
import org.junit.Test

class UMKMButtonsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testPrimaryButtonStates() {
        var clicked = false
        composeTestRule.setContent {
            UMKMShopTheme {
                UMKMPrimaryButton(
                    onClick = { clicked = true },
                    enabled = true
                ) {
                    Text("Action")
                }
            }
        }

        composeTestRule.onNodeWithText("Action").assertIsEnabled().performClick()
        assert(clicked)
    }

    @Test
    fun testPrimaryButtonDisabled() {
        composeTestRule.setContent {
            UMKMShopTheme {
                UMKMPrimaryButton(
                    onClick = { },
                    enabled = false
                ) {
                    Text("Disabled Action")
                }
            }
        }

        composeTestRule.onNodeWithText("Disabled Action").assertIsNotEnabled()
    }

    @Test
    fun testSecondaryButtonStates() {
        var clicked = false
        composeTestRule.setContent {
            UMKMShopTheme {
                UMKMSecondaryButton(
                    onClick = { clicked = true },
                    enabled = true
                ) {
                    Text("Secondary Action")
                }
            }
        }

        composeTestRule.onNodeWithText("Secondary Action").assertIsEnabled().performClick()
        assert(clicked)
    }
}
