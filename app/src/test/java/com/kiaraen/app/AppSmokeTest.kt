package com.kiaraen.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Device-free crash reproduction: boots the REAL Application + Activity chain
 * exactly as a phone would (Application.onCreate → MainActivity.onCreate →
 * first Compose composition → AuthScreen LaunchedEffect → session restore).
 * Any exception in that chain fails this test with the true stack trace.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = KiaraApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSmokeTest {

    @Test
    fun `app boots to auth screen without crashing`() {
        // buildActivity runs Application.onCreate + Activity.onCreate + first
        // composition on the main looper, like a real launch would.
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
        controller.setup() // onCreate → onStart → onResume
        org.robolectric.Robolectric.flushForegroundThreadScheduler()
        controller.get().finish()
    }

    @Test
    fun `application creates full dependency graph`() {
        val app = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<KiaraApplication>()
        // touches Room, DataStore, Supabase client, repositories, SyncEngine
        val c = app.container
        assert(c.database.openHelper.writableDatabase != null)
    }
}
