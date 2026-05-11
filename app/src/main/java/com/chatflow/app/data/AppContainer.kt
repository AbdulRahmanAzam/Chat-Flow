package com.chatflow.app.data

import android.content.Context
import androidx.room.Room
import com.chatflow.app.crypto.CryptoManager
import com.chatflow.app.data.db.AppDatabase
import com.chatflow.app.data.repo.ChatRepository

/** Simple service locator — avoids pulling in Hilt for a class project. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val db: AppDatabase = Room.databaseBuilder(
        appContext, AppDatabase::class.java, "chatflow.db"
    ).fallbackToDestructiveMigration().build()

    val crypto = CryptoManager(appContext)
    val prefs = PrefsStore(appContext)
    val repo = ChatRepository(db, crypto)
}
