package com.kiaraen.app.core

import android.content.Context
import com.kiaraen.app.data.local.KiaraDatabase
import com.kiaraen.app.data.remote.ContentRemoteDataSource
import com.kiaraen.app.data.remote.ProgressRemoteDataSource
import com.kiaraen.app.data.remote.SupabaseProvider
import com.kiaraen.app.data.repository.AuthRepository
import com.kiaraen.app.data.repository.ContentRepository
import com.kiaraen.app.data.repository.ProgressRepository
import com.kiaraen.app.data.sync.SyncEngine
import com.kiaraen.app.data.sync.SyncWorker

/**
 * Manual dependency graph (Google's recommended lightweight pattern for apps
 * this size — see docs/01-architecture.md §DI). Every dependency is injected
 * through constructors, so unit tests build fakes and a future Hilt adoption
 * stays mechanical.
 */
class AppContainer(context: Context) {

    val database: KiaraDatabase = KiaraDatabase.get(context)

    private val supabase = SupabaseProvider.client

    val authRepository = AuthRepository(supabase)
    val contentRepository = ContentRepository(
        context = context,
        db = database,
        remote = ContentRemoteDataSource(supabase),
    )
    val progressRepository = ProgressRepository(
        db = database,
        remote = ProgressRemoteDataSource(supabase),
    )

    val syncEngine = SyncEngine(
        db = database,
        remote = ProgressRemoteDataSource(supabase),
        content = contentRepository,
        auth = authRepository,
        progressRepo = progressRepository,
    )

    suspend fun bootstrap() {
        contentRepository.bootstrapIfEmpty()   // bundled pack → usable offline immediately
    }
}
