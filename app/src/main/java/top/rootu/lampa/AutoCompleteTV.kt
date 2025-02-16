package top.rootu.lampa

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.ListPopupWindow
import android.widget.ListView
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.core.view.updateLayoutParams
import top.rootu.lampa.helpers.Helpers.dp2px
import top.rootu.lampa.helpers.Prefs.remUrlHistory


// Extension of AppCompatAutoCompleteTextView that automatically hides the soft keyboard
// when the autocomplete dropdown is touched.

class AutoCompleteTV @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.autoCompleteTextViewStyle
) : AppCompatAutoCompleteTextView(context, attributeSet, defStyleAttr) {

    companion object {
        // need to access private field ('mPopup') of AutoCompleteTextView
        @SuppressLint("PrivateApi")
        private val popupWindowField = AutoCompleteTextView::class.java.getDeclaredField("mPopup")
            .also { it.isAccessible = true }
    }

    private val popupWindow = popupWindowField.get(this) as? ListPopupWindow?
    private val fadingEdgeH = dp2px(this.context, 64.0F)

    // this function actually hides the keyboard
    private fun hideKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(rootView.windowToken, 0)
    }


    private fun setListViewBasedOnChildren(listView: ListView) {
        listView.updateLayoutParams {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
        }
    }

    // add scroll listener to dropdown in order to hide IME when scrolled
    @SuppressLint("ClickableViewAccessibility")
    override fun showDropDown() {
        super.showDropDown()
        // ajust vertical dropdown offset
        this.dropDownVerticalOffset = resources.getDimensionPixelSize(R.dimen.dropdown_margin_top)
        // hide scrollbar and add fading edge
        popupWindow?.listView?.apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(fadingEdgeH)
            background = ColorDrawable(Color.TRANSPARENT)
            overScrollMode = OVER_SCROLL_NEVER
            // update listview layout
            setListViewBasedOnChildren(this)
        }
        // the popup list view shouldn't be null here because the dropdown was just shown
        // ... perhaps unless it's empty
        popupWindow?.listView?.setOnTouchListener { _, _ ->
            hideKeyboard()
            // have to return false here otherwise scrolling won't work
            false
        }
        // handle remove url from history
        popupWindow?.listView?.setOnItemLongClickListener { _, view, position, _ ->
            val url = adapter.getItem(position) as String?
            if (!url.isNullOrEmpty()) {
                view.context.remUrlHistory(url) // update Prefs
                MainActivity.urlAdapter.remove(url) // update GUI
            }
            true
        }
    }
}