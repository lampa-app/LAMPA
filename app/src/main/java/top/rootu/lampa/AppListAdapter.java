package top.rootu.lampa;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class AppListAdapter extends BaseAdapter {
    private final LayoutInflater mLayoutInflater;
    private final List<ResolveInfo> appsInfo;
    private final PackageManager pm;

    AppListAdapter(Context context, List<ResolveInfo> appList) {
        mLayoutInflater = LayoutInflater.from(context);
        appsInfo = appList;
        pm = context.getPackageManager();
    }

    @Override
    public int getCount() {
        return appsInfo.size();
    }

    @Override
    public ResolveInfo getItem(int position) {
        return appsInfo.get(position);
    }

    public String getItemPackage(int position) {
        return getItem(position).activityInfo.packageName.toLowerCase(Locale.getDefault());
    }

    public String getItemLabel(int position) {
        CharSequence loadLabel = getItem(position).loadLabel(pm);
        String label = "";
        if (loadLabel == null || (label = loadLabel.toString()).isEmpty()) {
            label = getItemPackage(position);
        }
        return label;
    }

    public Drawable getItemIcon(int position) {
        return getItem(position).loadIcon(pm);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View cv, ViewGroup parent) {
        if (cv == null)
            cv = mLayoutInflater.inflate(R.layout.app_list_item, null);
        ImageView image = cv.findViewById(R.id.imageViewIcon);
        TextView textViewMain = cv.findViewById(R.id.textViewMain);
        TextView textViewSecond = cv.findViewById(R.id.textViewSecond);

        image.setImageDrawable(getItemIcon(position));
        textViewMain.setText(getItemLabel(position));
        textViewSecond.setText(getItemPackage(position));

        return cv;
    }
}
