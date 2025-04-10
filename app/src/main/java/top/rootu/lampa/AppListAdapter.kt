package top.rootu.lampa

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import java.util.Locale

class AppListAdapter internal constructor(
    context: Context,
    private val appsInfo: List<ResolveInfo>
) : BaseAdapter() {
    private val iconCache = mutableMapOf<String, Drawable>()
    private val labelCache = mutableMapOf<String, String>()
    private val mLayoutInflater: LayoutInflater = LayoutInflater.from(context)
    private val pm: PackageManager = context.packageManager

    private class ViewHolder(
        val icon: ImageView,
        val mainText: TextView,
        val secondaryText: TextView
    )

    override fun getCount(): Int {
        return appsInfo.size
    }

    override fun getItem(position: Int): ResolveInfo {
        return appsInfo[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val viewHolder: ViewHolder

        if (convertView == null) {
            view = mLayoutInflater.inflate(R.layout.app_list_item, parent, false)
            viewHolder = ViewHolder(
                view.findViewById(R.id.imageViewIcon),
                view.findViewById(R.id.textViewMain),
                view.findViewById(R.id.textViewSecond)
            )
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = convertView.tag as ViewHolder
        }

        viewHolder.icon.setImageDrawable(getItemIcon(position))
        viewHolder.mainText.text = getItemLabel(position)
        viewHolder.secondaryText.text = getItemPackage(position)

        return view
    }


    private fun getItemIcon(position: Int): Drawable? {
        val packageName = getItemPackage(position)
        return iconCache.getOrPut(packageName) {
            getItem(position).loadIcon(pm)
        }
    }

    private fun getItemLabel(position: Int): String {
        val resolveInfo = getItem(position)
        val packageName = resolveInfo.activityInfo.packageName
        return labelCache.getOrPut(packageName) {
            resolveInfo.loadLabel(pm).toString()
        }
    }

    fun getItemPackage(position: Int): String {
        return getItem(position).activityInfo.packageName.lowercase(Locale.getDefault())
    }
}