package com.neko.neuecode.ui.screen.paycode

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neko.neuecode.ui.components.NeuWebView
import com.neko.neuecode.ui.screen.ecode.ECodeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ECodeWebViewScreen(
    onBack: () -> Unit,
    viewModel: ECodeViewModel = hiltViewModel(),
) {
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("付款码") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NeuWebView(
                url = currentUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onPageFinished = { url -> viewModel.onPageFinished(url) },
            )
        }
    }
}
