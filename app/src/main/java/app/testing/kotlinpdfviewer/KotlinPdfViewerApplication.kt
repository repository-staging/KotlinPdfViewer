package app.testing.kotlinpdfviewer

import android.app.Application

import com.google.android.material.color.DynamicColors

class KotlinPdfViewerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
    // TODO?: Move some of either PdfViewerFragment or PdfViewerViewModel logic to Application class
}