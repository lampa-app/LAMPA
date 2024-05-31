package top.rootu.lampa

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.ListPopupWindow
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import top.rootu.lampa.helpers.Helpers.dp2px


// Extension of AppCompatAutoCompleteTextView that automatically hides the soft keyboard
// when the autocomplete dropdown is touched.

class AutoCompleteTV @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = R.attr.autoCompleteTextViewStyle
) : AppCompatAutoCompleteTextView(context, attributeSet, defStyleAttr) {

    companion object {
        // need to access private field ('mPopup') of AutoCompleteTextView
        @SuppressLint("PrivateApi")
        private val popupWindowField = AutoCompleteTextView::class.java.getDeclaredField("mPopup")
            .also { it.isAccessible = true }
    }

    private val popupWindow = popupWindowField.get(this) as? ListPopupWindow?

    // this function actually hides the keyboard
    private fun hideKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(rootView.windowToken, 0)
    }

    // add scroll listener to dropdown in order to hide IME when scrolled
    @SuppressLint("ClickableViewAccessibility")
    override fun showDropDown() {
        super.showDropDown()
        // hide scrollbar and add fading edge
        popupWindow?.listView?.apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp2px(this.context, 80.0F))
            background = ColorDrawable(Color.TRANSPARENT)
            overScrollMode = OVER_SCROLL_NEVER
        }
        // the popup list view shouldn't be null here because the dropdown was just shown
        // ... perhaps unless it's empty
        popupWindow?.listView?.setOnTouchListener { _, _ ->
            hideKeyboard()
            // have to return false here otherwise scrolling won't work
            false
        }
    }
}