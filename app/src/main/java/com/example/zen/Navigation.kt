package com.example.zen

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.zen.data.DefaultZenStatusProvider
import com.example.zen.ui.main.MainScreen
import com.example.zen.ui.main.MainScreenViewModel

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)
  val context = LocalContext.current

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          // Construct ZenStatusProvider cleanly and wire up the ViewModel using Jetpack Compose delegation
          val statusProvider = remember(context) { DefaultZenStatusProvider(context.applicationContext) }
          val viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(statusProvider) }

          MainScreen(
            viewModel = viewModel,
            onItemClick = { navKey -> backStack.add(navKey) },
            modifier = Modifier.safeDrawingPadding()
          )
        }
      },
  )
}
