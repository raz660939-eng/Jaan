package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.assistant.MahiPersonality
import com.example.assistant.MahiVibe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Mahi AI", appName)
    }

    @Test
    fun `verify mahi personality instructions`() {
        val instructions = MahiPersonality.buildSystemInstruction(MahiVibe.SASSY_FLIRTY)
        assertTrue(instructions.contains("Mahi"))
        assertTrue(instructions.contains("sassy"))
        assertTrue(instructions.contains("openWebsite"))
    }
}
