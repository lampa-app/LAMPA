package top.rootu.lampa

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class ImgArrayAdapter : ArrayAdapter<String?> {
    private val images: List<Int>

    constructor(context: Context?, items: List<String?>?, images: List<Int>) : super(
        context!!, android.R.layout.select_dialog_item, items!!
    ) {
        this.images = images
    }

    constructor(context: Context?, items: Array<String?>?, images: Array<Int>) : super(
        context!!, android.R.layout.select_dialog_item, items!!
    ) {
        this.images = mutableListOf(*images)
    }

    constructor(context: Context, items: Int, images: Int) : super(
        context,
        android.R.layout.select_dialog_item,
        (context.resources.getTextArray(items) as Array<String?>)
    ) {
        val imgs = context.resources.obtainTypedArray(images)
        this.images = object : ArrayList<Int>() {
            init {
                for (i in 0 until imgs.length()) {
                    add(imgs.getResourceId(i, -1))
                }
            }
        }
        // recycle the array
        imgs.recycle()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(images[position], 0, 0, 0)
        } else {
            textView.setCompoundDrawablesWithIntrinsicBounds(images[position], 0, 0, 0)
        }
        textView.compoundDrawablePadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            12f,
            context.resources.displayMetrics
        ).toInt()
        textView.textSize = 20f
        textView.setLines(2)
        return view
    }
}