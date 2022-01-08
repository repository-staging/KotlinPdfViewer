package app.testing.kotlinpdfviewer.ui.fragment

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer

import app.testing.kotlinpdfviewer.R
import app.testing.kotlinpdfviewer.viewmodel.PdfViewerViewModel

//import com.google.android.material.color.DynamicColors

class DocumentPropertiesFragment : DialogFragment() {

    private lateinit var arrayAdapter: ArrayAdapter<CharSequence>
    private lateinit var propertiesObserver: Observer<List<CharSequence>>
    private val viewModel: PdfViewerViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //DynamicColors.wrapContextIfAvailable(requireContext())
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.getDocumentProperties().removeObserver(propertiesObserver)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity: Activity = requireActivity()
        val dialog = AlertDialog.Builder(activity)
            .setPositiveButton(android.R.string.ok, null)
        val list: List<CharSequence>? = viewModel.getDocumentProperties().value
        arrayAdapter = ArrayAdapter<CharSequence>(
            requireActivity(), android.R.layout.simple_list_item_1,
            list ?: emptyList()
        )
        dialog.setAdapter(arrayAdapter, null)
        dialog.setTitle(getTitleStringIdForPropertiesState(list))
        val alertDialog = dialog.create()
        propertiesObserver = Observer { charSequences ->
            Log.d(TAG, "Properties changed!")
            alertDialog.setTitle(getTitleStringIdForPropertiesState(charSequences))
            arrayAdapter.notifyDataSetChanged()
        }
        viewModel.getDocumentProperties().observe(requireActivity(), propertiesObserver)
        return alertDialog
    }

    private fun getTitleStringIdForPropertiesState(properties: List<CharSequence>?): Int {
        return if (properties == null || properties.isEmpty()) R.string.document_properties_retrieval_failed else R.string.action_view_document_properties
    }

    companion object {
        const val TAG = "DocumentPropertiesFragment"
        fun newInstance(): DocumentPropertiesFragment {
            return DocumentPropertiesFragment()
        }
    }
}