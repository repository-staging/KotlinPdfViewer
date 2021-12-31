package app.testing.kotlinpdfviewer.ui.fragment

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.NumberPicker

import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.DialogFragment

//import app.testing.kotlinpdfviewer.R
import app.testing.kotlinpdfviewer.viewmodel.PdfViewerViewModel

class JumpToPageFragment : DialogFragment() {

    private lateinit var numberPicker: NumberPicker
    private val model: PdfViewerViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState != null) {
            numberPicker.minValue = savedInstanceState.getInt(STATE_PICKER_MIN)
            numberPicker.maxValue = savedInstanceState.getInt(STATE_PICKER_MAX)
            numberPicker.value = savedInstanceState.getInt(STATE_PICKER_CUR)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        numberPicker = NumberPicker(activity)
        numberPicker.minValue = 1
        numberPicker.maxValue = model.numPages
        numberPicker.value = model.page
        val layout = FrameLayout(requireActivity())
        layout.addView(
            numberPicker, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        return AlertDialog.Builder(requireActivity())
            .setView(layout)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                numberPicker.clearFocus()
                val result = bundleOf(BUNDLE_KEY to numberPicker.value)
                //findNavController().navigate(R.id.action_send_to_page, result)
                parentFragmentManager.setFragmentResult(REQUEST_KEY, result)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PICKER_MIN, numberPicker.minValue)
        outState.putInt(STATE_PICKER_MAX, numberPicker.maxValue)
        outState.putInt(STATE_PICKER_CUR, numberPicker.value)
    }

    companion object {
        const val TAG = "JumpToPageFragment"
        const val REQUEST_KEY = "jumpToPage"
        const val BUNDLE_KEY: String = "jumpToPageBundle"
        private const val STATE_PICKER_CUR = "picker_cur"
        private const val STATE_PICKER_MIN = "picker_min"
        private const val STATE_PICKER_MAX = "picker_max"
    }
}