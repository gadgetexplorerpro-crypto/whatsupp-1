package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.LocalCryptoManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context and verify AES-256-GCM local encryption`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Whatsupp", appName)

        val secretMsg = "Family Connected Device GPS: 37.4220, -122.0841"
        val encrypted = LocalCryptoManager.encrypt(secretMsg, "family_uid_123")
        val decrypted = LocalCryptoManager.decrypt(encrypted, "family_uid_123")
        assertEquals(secretMsg, decrypted)
    }
}
