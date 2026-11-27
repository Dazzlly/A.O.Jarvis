package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.OpenJarvisApp
import com.example.ui.OpenJarvisViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge display
        enableEdgeToEdge()

        // STEP 3: Render Compose UI
        setContent {
            val viewModel: OpenJarvisViewModel = viewModel()
            OpenJarvisApp(viewModel = viewModel)
        }
    }
}
