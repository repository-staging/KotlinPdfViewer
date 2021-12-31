package app.testing.kotlinpdfviewer.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

import com.google.android.material.snackbar.Snackbar

import app.testing.kotlinpdfviewer.R
import app.testing.kotlinpdfviewer.databinding.PdfViewerFragmentBinding
import app.testing.kotlinpdfviewer.ui.fragment.DocumentPropertiesFragment
import app.testing.kotlinpdfviewer.ui.fragment.JumpToPageFragment
import app.testing.kotlinpdfviewer.viewmodel.PdfViewerViewModel

import java.io.InputStream
import java.io.IOException
import java.util.HashMap
import kotlin.math.abs

class PdfViewerFragment : Fragment() {

    companion object {
        fun newInstance() = PdfViewerFragment()
        const val TAG = "PdfViewerFragment"
        private const val JAVASCRIPT_INTERFACE_NAME = "channel"

        private const val CONTENT_SECURITY_POLICY = "default-src 'none'; " +
                "form-action 'none'; " +
                "connect-src https://localhost/placeholder.pdf; " +
                "img-src blob: 'self'; " +
                "script-src 'self' 'resource://pdf.js'; " +
                "style-src 'self'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'none'"

        private const val FEATURE_POLICY = "accelerometer 'none'; " +
                "ambient-light-sensor 'none'; " +
                "autoplay 'none'; " +
                "camera 'none'; " +
                "encrypted-media 'none'; " +
                "fullscreen 'none'; " +
                "geolocation 'none'; " +
                "gyroscope 'none'; " +
                "magnetometer 'none'; " +
                "microphone 'none'; " +
                "midi 'none'; " +
                "payment 'none'; " +
                "picture-in-picture 'none'; " +
                "speaker 'none'; " +
                "sync-xhr 'none'; " +
                "usb 'none'; " +
                "vr 'none'"

        private const val MIN_ZOOM_RATIO = 0.5f
        private const val MAX_ZOOM_RATIO = 1.5f
        private const val ALPHA_LOW = 130
        private const val ALPHA_HIGH = 255
        private const val PADDING = 10
        private const val STATE_STARTED = 0
        private const val STATE_LOADED = 1
        private const val STATE_END = 2

        /**
         * GestureHelper is a simple gesture API for PDFViewerFragment.
         * Due to Kotlin's way of handling visibility in classes and objects, and not having
         * a package-level only visibility available, GestureHelper "object" is instead placed
         * at the companion object of PdfViewerFragment, as it's only used here in its
         * Java counterpart.
         * Currently based on automatic conversion to Kotlin by Android Studio.
         */
        private object GestureHelper {
            interface GestureListener {
                fun onTapUp(): Boolean
                // Can be replaced with ratio when supported
                fun onZoomIn(value: Float)
                fun onZoomOut(value: Float)
                fun onZoomEnd()
            }
            @SuppressLint("ClickableViewAccessibility")
            fun attach(context: Context?, gestureView: View, listener: GestureListener) {
                val detector = GestureDetector(context,
                    object : GestureDetector.SimpleOnGestureListener() {
                        override fun onSingleTapUp(motionEvent: MotionEvent): Boolean {
                            return listener.onTapUp()
                        }
                    })
                val scaleDetector = ScaleGestureDetector(context,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        val SPAN_RATIO = 600f
                        var initialSpan = 0f
                        var prevNbStep = 0f
                        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                            initialSpan = detector.currentSpan
                            prevNbStep = 0f
                            return true
                        }

                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val spanDiff = initialSpan - detector.currentSpan
                            val curNbStep = spanDiff / SPAN_RATIO
                            val stepDiff = curNbStep - prevNbStep
                            if (stepDiff > 0) {
                                listener.onZoomOut(stepDiff)
                            } else {
                                listener.onZoomIn(abs(stepDiff))
                            }
                            prevNbStep = curNbStep
                            return true
                        }

                        override fun onScaleEnd(detector: ScaleGestureDetector) {
                            listener.onZoomEnd()
                        }
                    })
                gestureView.setOnTouchListener { _, motionEvent: MotionEvent? ->
                    detector.onTouchEvent(motionEvent)
                    scaleDetector.onTouchEvent(motionEvent)
                    false
                }
            }
        }
    }

    private var documentState: Int = STATE_STARTED
    private var windowInsetsTop: Int = 0
    private var inputStream: InputStream? = null
    private var toast: Toast? = null
    private var areBarsShowing = true

    private val viewModel: PdfViewerViewModel by activityViewModels()
    private lateinit var binding: PdfViewerFragmentBinding
    private lateinit var snackbar: Snackbar
    private lateinit var textView: TextView

    private inner class Channel {
        // TODO: Implement vertical scrolling preference
        // @JavascriptInterface
        // fun getScrollingPreference() = PreferenceManager.getDefaultSharedPreferences(
        //         requireContext().applicationContext).getBoolean("vertical-scrolling", false)
        @JavascriptInterface
        fun getWindowInsetTop() = windowInsetsTop

        @JavascriptInterface
        fun getPage() = viewModel.page

        @JavascriptInterface
        fun getZoomRatio() = viewModel.zoomRatio

        @JavascriptInterface
        fun getDocumentOrientationDegrees() = viewModel.documentOrientationDegrees

        @JavascriptInterface
        fun setNumPages(numPages: Int) {
            viewModel.numPages = numPages
            updateMenuUi()
        }

        @JavascriptInterface
        fun setDocumentProperties(properties: String) {
            val list: List<CharSequence>? = viewModel.getDocumentProperties().value
            if (list != null && list.isEmpty() && activity != null) {
                viewModel.loadProperties(properties, requireActivity().applicationContext)
            } else {
                Log.d(TAG, "setDocumentProperties: not loading properties because " +
                        if (list == null) "list is null" else "list is not empty")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.coordinatorLayout.removeAllViews()
        // Load about:blank to reset the view state and release page resources.
        binding.webView.removeJavascriptInterface(JAVASCRIPT_INTERFACE_NAME)
        binding.webView.loadUrl("about:blank")
        binding.webView.onPause()

        // Note: This pauses JS, layout, parsing timers for all WebViews.
        // Need to resume timers when creating again.
        binding.webView.pauseTimers()
        binding.webView.settings.javaScriptEnabled = false
        binding.webView.removeAllViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webView.destroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = PdfViewerFragmentBinding.inflate(
            inflater,
            container,
            false
        )
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Navigation API currently doesn't support navigation to/from Dialog Fragments?
        // val newPage = arguments?.getInt(JumpToPageFragment.BUNDLE_KEY) ?: 1
        parentFragmentManager.setFragmentResultListener(JumpToPageFragment.REQUEST_KEY,
            this, { _: String?, result: Bundle ->
                val newPage = result.getInt(JumpToPageFragment.BUNDLE_KEY)
                onJumpToPageInDocument(newPage)
            })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.saveState()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.webView.resumeTimers()

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_previous -> {
                    onJumpToPageInDocument(viewModel.page - 1)
                    true
                }
                R.id.action_next -> {
                    onJumpToPageInDocument(viewModel.page + 1)
                    true
                }
                R.id.action_first -> {
                    onJumpToPageInDocument(1)
                    true
                }
                R.id.action_last -> {
                    onJumpToPageInDocument(viewModel.numPages)
                    true
                }
                R.id.action_open -> {
                    openDocument()
                    true
                }
                R.id.action_zoom_out -> {
                    zoomOut(0.25f, true)
                    true
                }
                R.id.action_zoom_in -> {
                    zoomIn(0.25f, true)
                    true
                }
                R.id.action_rotate_clockwise -> {
                    documentOrientationChanged(90)
                    true
                }
                R.id.action_rotate_counterclockwise -> {
                    documentOrientationChanged(-90)
                    true
                }
                R.id.action_view_document_properties -> {
                    // findNavController().navigate(R.id.action_to_document_properties)
                    DocumentPropertiesFragment
                        .newInstance()
                        .show(parentFragmentManager, DocumentPropertiesFragment.TAG)
                    true
                }
                R.id.action_jump_to_page -> {
                    // findNavController().navigate(R.id.action_to_num_page_picker)
                    JumpToPageFragment()
                        .show(parentFragmentManager, JumpToPageFragment.TAG)
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { currentView, insets ->
            val paddingInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.mandatorySystemGestures() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            currentView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = /*if (areBarsShowing) */ paddingInsets.left /* else 0 */
                rightMargin = /*if (areBarsShowing) */ paddingInsets.right /* else 0 */
                topMargin = /*if (areBarsShowing) */ paddingInsets.top /* else 0 */
                bottomMargin = 0
            }
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.webView) { currentView, insets ->
            val paddingInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.mandatorySystemGestures() or
                        WindowInsetsCompat.Type.displayCutout()
            )
            currentView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                windowInsetsTop = if (areBarsShowing) {
                    binding.toolbar.minimumHeight + paddingInsets.top
                } else 0
            }
            binding.webView.evaluateJavascript("updateInset()", null)
            insets
        }

        val type = WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.systemGestures() or
                WindowInsetsCompat.Type.displayCutout()

        ViewCompat.setOnApplyWindowInsetsListener(
            requireActivity().window.decorView
        ) { currentView, insets ->
            val fullInset = insets.getInsetsIgnoringVisibility(type)
            if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                currentView.updatePadding(
                    left = fullInset.left,
                    top = fullInset.top,
                    right = fullInset.right,
                    bottom = fullInset.bottom,
                )
            } else {
                currentView.updatePadding(0, 0, 0, 0)
            }
            insets
        }

        updateMenuUi()

        binding.webView.settings.apply {
            allowContentAccess = false
            allowFileAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            javaScriptEnabled = true
            /* This does not set the dark theme on pdf as intended.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                binding.webView.isForceDarkAllowed = true
                val isInDarkMode = (resources.configuration.uiMode
                        and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (isInDarkMode) {
                    WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_ON)
                    WebSettingsCompat.setForceDarkStrategy(this,
                        WebSettingsCompat.DARK_STRATEGY_USER_AGENT_DARKENING_ONLY)
                }
            }
            */
        }

        CookieManager.getInstance().setAcceptCookie(false)
        binding.webView.addJavascriptInterface(Channel(), JAVASCRIPT_INTERFACE_NAME)

        /**
         * Code below on this function is generated by Android Studio auto-conversion.
         */
        binding.webView.webViewClient = object : WebViewClient() {
            private fun fromAsset(mime: String, path: String): WebResourceResponse? {
                return if (activity == null) {
                    null
                } else try {
                    val inputStream = requireActivity().assets.open(path.substring(1))
                    WebResourceResponse(mime, null, inputStream)
                } catch (e: IOException) {
                    null
                }
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                if ("GET" != request.method) {
                    return null
                }
                val url = request.url
                if ("localhost" != url.host) {
                    return null
                }
                val path = url.path
                Log.d(TAG, "path $path")
                if ("/placeholder.pdf" == path) {
                    return WebResourceResponse("application/pdf", null, inputStream)
                }
                if ("/viewer.html" == path) {
                    val response = fromAsset("text/html", path) ?: return null
                    val headers = HashMap<String, String>()
                    headers["Content-Security-Policy"] = CONTENT_SECURITY_POLICY
                    headers["Feature-Policy"] = FEATURE_POLICY
                    headers["X-Content-Type-Options"] = "nosniff"
                    response.responseHeaders = headers
                    return response
                }
                if ("/viewer.css" == path) {
                    return fromAsset("text/css", path)
                }
                return if ("/viewer.js" == path || "/pdf.js" == path || "/pdf.worker.js" == path) {
                    fromAsset("application/javascript", path)
                } else null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                documentState = STATE_STARTED
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String) {
                documentState = STATE_LOADED
                updateMenuUi()
            }
        }

        GestureHelper.attach(context, binding.webView, object : GestureHelper.GestureListener {
                override fun onTapUp(): Boolean {
                    if (viewModel.uri != null) {
                        binding.webView.evaluateJavascript("isTextSelected()") { selection ->
                            if (!selection.toBoolean() && activity != null) {
                                showOrHideSystemUi()
                            }
                        }
                        return true
                    }
                    return false
                }

                override fun onZoomIn(value: Float) {
                    zoomIn(value, false)
                }

                override fun onZoomOut(value: Float) {
                    zoomOut(value, false)
                }

                override fun onZoomEnd() {
                    zoomEnd()
                }
            })

        textView = TextView(context).apply {
            setBackgroundColor(Color.DKGRAY)
            setTextColor(ColorStateList.valueOf(Color.WHITE))
            textSize = 18F
            setPadding(PADDING, 0, PADDING, 0)
        }

        snackbar = Snackbar.make(binding.coordinatorLayout, "", Snackbar.LENGTH_LONG)

        val intent = requireActivity().intent
        if (Intent.ACTION_VIEW == intent.action) {
            if ("application/pdf" != intent.type) {
                snackbar.setText(R.string.invalid_mime_type).show()
                return
            }
            viewModel.uri = intent.data
            viewModel.page = 1
        }

        if (savedInstanceState != null) {
            viewModel.restoreState()
        }

        val uri: Uri? = viewModel.uri
        if (uri != null) {
            if ("file" == uri.scheme) {
                snackbar.setText(R.string.legacy_file_uri).show()
                return
            }
            loadPdf(savedInstanceState == null)
        }

    }

    private val getDocumentUriLauncher = registerForActivityResult<String, Uri>(
        ActivityResultContracts.GetContent()) { uriToLoad ->
        uriToLoad?.let { uri ->
            viewModel.uri = uri
            viewModel.page = 1
            loadPdf(true)
        }
    }

    private fun loadPdf(isLoading: Boolean) {
        val uri = viewModel.uri ?: return
        try {
            inputStream?.close()
            inputStream = requireActivity().contentResolver.openInputStream(uri)
        } catch (exception: IOException) {
            snackbar.setText(R.string.io_error).show()
            viewModel.clearDocumentProperties()
            return
        }
        if (isLoading) {
            viewModel.clearDocumentProperties()
        }
        showOrHideSystemUi(true)
        binding.webView.loadUrl("https://localhost/viewer.html")
    }

    private fun openDocument() {
        getDocumentUriLauncher.launch("application/pdf")
    }

    private fun runOnUiThread(action: Runnable) {
        activity?.runOnUiThread(action)
    }

    /** Code below are currently based on automatic conversion to Kotlin by Android Studio */

    private fun zoomIn(value: Float, end: Boolean) {
        if (viewModel.zoomRatio < MAX_ZOOM_RATIO) {
            viewModel.zoomRatio =
                (viewModel.zoomRatio + value).coerceAtMost(MAX_ZOOM_RATIO)
            renderPage(if (end) 1 else 2)
            updateMenuUi()
        }
    }

    private fun zoomOut(value: Float, end: Boolean) {
        if (viewModel.zoomRatio > MIN_ZOOM_RATIO) {
            viewModel.zoomRatio =
                (viewModel.zoomRatio - value).coerceAtLeast(MIN_ZOOM_RATIO)
            renderPage(if (end) 1 else 2)
            updateMenuUi()
        }
    }

    private fun renderPage(zoom: Int) {
        binding.webView.evaluateJavascript("onRenderPage($zoom)", null)
    }

    private fun documentOrientationChanged(orientationDegreesOffset: Int) {
        var newOrientation: Int = (viewModel.documentOrientationDegrees
                + orientationDegreesOffset) % 360
        if (newOrientation < 0) {
            newOrientation += 360
        }
        viewModel.documentOrientationDegrees = newOrientation
        renderPage(0)
    }

    private fun zoomEnd() {
        renderPage(1)
    }

    private fun enableDisableMenuItem(item: MenuItem, enable: Boolean) {
        if (enable) {
            item.isEnabled = true
            item.icon.alpha = ALPHA_HIGH
        } else {
            item.isEnabled = false
            item.icon.alpha = ALPHA_LOW
        }
    }

    private fun onJumpToPageInDocument(selected_page: Int) {
        if (selected_page <= viewModel.numPages && viewModel.page != selected_page
            && selected_page >= 1) {
            viewModel.page = selected_page
            renderPage(0)
            showPageNumber()
            updateMenuUi()
        }
    }

    private fun showOrHideSystemUi(shouldShow: Boolean = !areBarsShowing) {
        ViewCompat.getWindowInsetsController(requireActivity().window.decorView)?.let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (shouldShow) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            binding.toolbar.isVisible = shouldShow
            areBarsShowing = shouldShow
        }
    }

    private fun showPageNumber() {
        toast?.cancel()
        textView.text = String.format("%s/%s", viewModel.page.toString(), viewModel.numPages.toString())

        toast = Toast(context)
        toast?.apply {
            setGravity(Gravity.BOTTOM or Gravity.END, PADDING, PADDING)
            duration = Toast.LENGTH_SHORT
            view = textView
        }?.show()
    }

    private fun updateMenuUi() {
        runOnUiThread {
            binding.toolbar.menu.findItem(R.id.action_open).isVisible = true
            val ids = intArrayOf(
                R.id.action_zoom_in, R.id.action_zoom_out, R.id.action_jump_to_page,
                R.id.action_next, R.id.action_previous, R.id.action_first, R.id.action_last,
                R.id.action_rotate_clockwise, R.id.action_rotate_counterclockwise,
                R.id.action_view_document_properties
            )
            if (documentState < STATE_LOADED) {
                for (id in ids) {
                    val item = binding.toolbar.menu.findItem(id)
                    if (item.isVisible) {
                        item.isVisible = false
                    }
                }
            } else if (documentState == STATE_LOADED) {
                for (id in ids) {
                    val item = binding.toolbar.menu.findItem(id)
                    if (!item.isVisible) {
                        item.isVisible = true
                    }
                }
                documentState = STATE_END
            }
            enableDisableMenuItem(
                binding.toolbar.menu.findItem(R.id.action_zoom_in),
                viewModel.zoomRatio != MAX_ZOOM_RATIO
            )
            enableDisableMenuItem(
                binding.toolbar.menu.findItem(R.id.action_zoom_out),
                viewModel.zoomRatio != MIN_ZOOM_RATIO
            )
            enableDisableMenuItem(
                binding.toolbar.menu.findItem(R.id.action_next),
                viewModel.page < viewModel.numPages
            )
            enableDisableMenuItem(
                binding.toolbar.menu.findItem(R.id.action_previous),
                viewModel.page > 1
            )
        }
    }
}
