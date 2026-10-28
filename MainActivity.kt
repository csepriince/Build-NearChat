package com.nearchat.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import com.nearchat.app.core.BtController
import com.nearchat.app.ui.NearChatApp

class MainVm(app: Application) : AndroidViewModel(app) {
    val ctl = BtController(app)
    override fun onCleared() {
        ctl.shutdown()
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MainVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NearChatApp(vm.ctl) }
    }
}
