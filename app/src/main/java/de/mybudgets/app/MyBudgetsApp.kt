package de.mybudgets.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.mybudgets.app.data.repository.CategoryRepository
import de.mybudgets.app.data.repository.GamificationRepository
import de.mybudgets.app.util.DataSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyBudgetsApp : Application() {

    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var gamificationRepository: GamificationRepository

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            categoryRepository.insertAll(DataSeeder.defaultCategories())
            gamificationRepository.seed(DataSeeder.defaultBadges())
        }
    }
}
