package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.AppThemeMode
import com.example.ui.BookViewModel
import com.example.ui.MainContainerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            com.example.data.remote.FirebaseHelper.initialize(applicationContext)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Firebase initialization deferred: ${e.message}")
        }
        try {
            val coilImageLoader = coil.ImageLoader.Builder(applicationContext)
                .okHttpClient {
                    okhttp3.OkHttpClient.Builder()
                        .addInterceptor { chain ->
                            val request = chain.request()
                            val host = request.url.host.lowercase()
                            if (host.contains("ibs.it") || host.contains("libraccio.it") || host.contains("mondadori")) {
                                val newRequest = request.newBuilder()
                                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                    .header("Referer", "https://www.ibs.it/")
                                    .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                                    .build()
                                chain.proceed(newRequest)
                            } else {
                                chain.proceed(request)
                            }
                        }
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()
                }
                .crossfade(true)
                .build()
            coil.Coil.setImageLoader(coilImageLoader)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Coil image loader setup deferred: ${e.message}")
        }
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = isDark) {
                MainContainerScreen(viewModel = viewModel)
            }
        }
    }
}

