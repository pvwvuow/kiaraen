package com.kiaraen.app.core

import android.content.Context
import com.kiaraen.app.data.local.AppPrefs
import com.kiaraen.app.data.local.KiaraDatabase
import com.kiaraen.app.data.remote.ContentRemoteDataSource
import com.kiaraen.app.data.remote.ProgressRemoteDataSource
import com.kiaraen.app.data.remote.SupabaseProvider
import com.kiaraen.app.data.repository.AuthRepository
import com.kiaraen.app.data.repository.ContentRepository
import com.kiaraen.app.data.repository.ProgressRepository
import com.kiaraen.app.data.sync.SyncEngine

/**
 * Manual dependency graph (Google's recommended lightweight pattern for apps
 * this size — see docs/01-architecture.md §DI). Every dependency is injected
 * through constructors, so unit tests build fakes and a future Hilt adoption
 * stays mechanical.
 *
 * Everything is LAZY: creating the container is free, so the main thread never
 * pays for the Supabase client (ktor engine, JSON, auth plugin) or Room before
 * it is actually needed. KiaraApplication warms the heavy parts up on a
 * background thread right after launch.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: KiaraDatabase by lazy { KiaraDatabase.get(appContext) }

    val appPrefs: AppPrefs by lazy { AppPrefs(appContext) }

    private val supabase by lazy { SupabaseProvider.client }

    val authRepository: AuthRepository by lazy { AuthRepository(supabase) }

    val contentRepository: ContentRepository by lazy {
        ContentRepository(
            context = appContext,
            db = database,
            remote = ContentRemoteDataSource(supabase),
        )
    }

    val progressRepository: ProgressRepository by lazy {
        ProgressRepository(
            db = database,
            remote = ProgressRemoteDataSource(supabase),
        )
    }

    val syncEngine: SyncEngine by lazy {
        SyncEngine(
            db = database,
            remote = ProgressRemoteDataSource(supabase),
            content = contentRepository,
            auth = authRepository,
            progressRepo = progressRepository,
        )
    }

    suspend fun bootstrap() {
        contentRepository.bootstrapIfEmpty()   // bundled pack → usable offline immediately
    }

    /** Background warm-up: build the expensive singletons before the UI asks. */
    fun warmUp() {
        database.openHelper.writableDatabase
        authRepository
    }
}
