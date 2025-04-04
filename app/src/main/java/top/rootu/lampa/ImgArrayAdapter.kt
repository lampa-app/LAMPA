package top.rootu.lampa

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat

@SuppressLint("ResourceType")
class ImgArrayAdapter(
    context: Context,
    items: List<String?>,
    val images: List<Int>,
    layoutRes: Int = android.R.layout.select_dialog_item
) : ArrayAdapter<String?>(context, layoutRes, items) {

    private var selectedItem = 0
    private val drawablePaddingPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        12f,
        context.resources.displayMetrics
    ).toInt()
    private val activeBg = ContextCompat.getDrawable(context, R.drawable.active_menu_bg)
    private val transparentColor = ContextCompat.getColor(context, android.R.color.transparent)

    companion object {
        fun create(
            context: Context,
            items: List<String?>,
            images: List<Int>,
            layoutRes: Int = android.R.layout.select_dialog_item
        ) = ImgArrayAdapter(context, items, images, layoutRes)

        fun fromResources(
            context: Context,
            textArrayRes: Int,
            imageArrayRes: Int
        ): ImgArrayAdapter {
            val texts = context.resources.getTextArray(textArrayRes).map { it?.toString() }
            val typedArray = context.resources.obtainTypedArray(imageArrayRes)
            val images = try {
                List(typedArray.length()) { i -> typedArray.getResourceId(i, -1) }
            } finally {
                typedArray.recycle()
            }
            return create(context, texts, images)
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent).apply {
            findViewById<TextView>(android.R.id.text1)?.configureTextView(position)
            if (position == selectedItem) {
                // Set focus on selected position
                (parent as ListView).setSelectionFromTop(position, top)
            }
        }
        return view
    }

    private fun TextView.configureTextView(position: Int) {
        val drawableRes = images.getOrNull(position) ?: return
        val drawable = ContextCompat.getDrawable(context, drawableRes)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
        } else {
            setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
        }
        compoundDrawablePadding = drawablePaddingPx
        textSize = 18f
        maxLines = 2
        if (position == selectedItem) {
            background = activeBg
        } else {
            setBackgroundColor(transparentColor)
        }
    }

    fun setSelectedItem(position: Int) {
        if (selectedItem != position) {
            selectedItem = position
            notifyDataSetChanged()
        }
    }
}