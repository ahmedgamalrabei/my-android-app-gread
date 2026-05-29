package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.DocumentRepository
import com.example.ui.DocumentViewModel
import com.example.ui.DocumentViewModelFactory
import com.example.ui.screens.MainDashboard

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize SQLite Room database & Repository mapping
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = DocumentRepository(database.documentDao())
        val factory = DocumentViewModelFactory(repository)
        
        // Obtain ViewModel instance
        val viewModel = ViewModelProvider(this, factory)[DocumentViewModel::class.java]

        setContent {
            MainDashboard(viewModel = viewModel)
        }
    }
}
