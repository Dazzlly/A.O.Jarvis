package com.example

import android.content.Context
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("A.O.Jarvis", appName)
    }

    @Test
    fun `main activity launches cleanly without firebase crash`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()

        shadowOf(Looper.getMainLooper()).idle()

        val activity = controller.get()
        assertFalse("MainActivity should remain open after startup", activity.isFinishing)
        assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)

        controller.pause().stop().destroy()
    }
}
