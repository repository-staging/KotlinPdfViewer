package app.testing.kotlinpdfviewer.ui.container

import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity

import app.testing.kotlinpdfviewer.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        window.setDecorFitsSystemWindows(false)
    }
}