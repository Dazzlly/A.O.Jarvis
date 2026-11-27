package com.example

import android.content.Context
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.cloud.FirebaseRuntimeConfig
import com.example.voice.AudioRecordWakeWordDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `main activity launches cleanly without crash`() {
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

    @Test
    fun `wake word matcher extracts inline command accurately`() {
        val inline = AudioRecordWakeWordDetector.matchWakePhrase("Hey Jarvis ligar a lanterna", "hey jarvis")
        assertTrue(inline.matched)
        assertEquals("ligar a lanterna", inline.inlineCommand)

        val bare = AudioRecordWakeWordDetector.matchWakePhrase("Hey Jarvis", "hey jarvis")
        assertTrue(bare.matched)
        assertEquals("", bare.inlineCommand)
    }

    @Test
    fun `firebase runtime config parses standard google services json`() {
        val sampleJson = """
            {
              "project_info": {
                "project_number": "1234567890",
                "project_id": "demo-project-id",
                "storage_bucket": "demo-project-id.firebasestorage.app"
              },
              "client": [
                {
                  "client_info": {
                    "mobilesdk_app_id": "1:1234567890:android:abcdef123456",
                    "android_client_info": {
                      "package_name": "com.aistudio.openjarvis.vkmqrx"
                    }
                  },
                  "oauth_client": [
                    {
                      "client_id": "1234567890-webclient.apps.googleusercontent.com",
                      "client_type": 3
                    }
                  ],
                  "api_key": [
                    {
                      "current_key": "test-api-key-placeholder"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = FirebaseRuntimeConfig.parseGoogleServicesJson(
            rawJson = sampleJson,
            packageName = "com.aistudio.openjarvis.vkmqrx"
        )
        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("demo-project-id", parsed.projectId)
        assertEquals("1:1234567890:android:abcdef123456", parsed.applicationId)
        assertEquals("1234567890-webclient.apps.googleusercontent.com", parsed.defaultWebClientId)
    }
}
