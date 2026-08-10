package com.baxailab.cadebot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.baxailab.cadebot.ui.navigation.CadebotNavGraph
import com.baxailab.cadebot.ui.theme.CadebotTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CadebotTheme {
                val navController = rememberNavController()
                CadebotNavGraph(navController = navController)
            }
        }
    }
}
