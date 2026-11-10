package app.routewise

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.routewise.data.TripDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@HiltAndroidApp
class RouteWiseApplication : Application()

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): TripDatabase =
        Room.databaseBuilder(context, TripDatabase::class.java, "routewise.db")
            .addMigrations(TripDatabase.MIGRATION_1_2, TripDatabase.MIGRATION_2_3, TripDatabase.MIGRATION_3_4)
            .addCallback(TripDatabase.INBOX_CONSTRAINT).build()
}
