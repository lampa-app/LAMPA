// Copyright (c) 2015 Intel Corporation. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.xwalk.core;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import top.netfix.helpers.FileHelpers;
import top.netfix.net.TlsSocketFactory;

/**
 * MyXWalkLibraryLoader is a low level inteface to schedule decompressing, downloading, activating
 * the Crosswalk runtime. Normal user is recommended to use XWalkActivity or XWalkInitializer which
 * is simpler and more user-friendly.
 * <p>
 * The appropriate invocation order is:
 * prepareToInit() -
 * [ if the Crosswalk runtime is supposed to be compressed - startDecompress() ] -
 * startActivate() -
 * [ if the Crosswalk runtime doesn't match - download suitable version - startActivate() ] - over
 */

class MyXWalkLibraryLoader {
    private static final String DEFAULT_DOWNLOAD_FILE_NAME = "xwalk_update.apk";
    private static final String DOWNLOAD_WITHOUT_NOTIFICATION =
            "android.permission.DOWNLOAD_WITHOUT_NOTIFICATION";
    private static final String TAG = "XWalkLib";
    private static AsyncTask<Void, Integer, Integer> sActiveTask;

    public static boolean isInitializing() {
        return (sActiveTask instanceof DecompressTask
                || sActiveTask instanceof ActivateTask);
    }

    public static boolean isDownloading() {
        return (sActiveTask instanceof DownloadManagerTask
                || sActiveTask instanceof HttpDownloadTask);
    }

    /**
     * Return true if running in shared mode, false if in embedded mode.
     *
     * <p>This method must be invoked after the Crosswalk runtime has already been initialized
     * successfully.
     */
    public static boolean isSharedLibrary() {
        return XWalkCoreWrapper.getInstance().isSharedMode();
    }

    /**
     * Return true if the Crosswalk runtime has already been initialized successfully either in
     * embedded mode or shared mode, false otherwise.
     */
    public static boolean isLibraryReady() {
        return XWalkCoreWrapper.getInstance() != null;
    }

    /**
     * Return the library status defined in {@link XWalkLibraryInterface}.
     */
    public static int getLibraryStatus() {
        return XWalkCoreWrapper.getCoreStatus();
    }

    /**
     * Prepare to start initializing before all other procedures.
     *
     * <p>This method must be invoked on the UI thread.
     */
    public static void prepareToInit(Context context) {
        MyXWalkEnvironment.init(context);
        XWalkCoreWrapper.handlePreInit(context.getClass().getName());
    }

    public static void finishInit(Context context) {
        XWalkCoreWrapper.handlePostInit(context.getClass().getName());
    }

    /**
     * Start decompressing the Crosswalk runtime in background
     *
     * <p>This method must be invoked on the UI thread.
     *
     * @param listener The {@link DecompressListener} to use
     */
    public static void startDecompress(DecompressListener listener) {
        new DecompressTask(listener).execute();
    }

    /**
     * Attempt to cancel decompression
     *
     * @return false if decompression is not running or could not be cancelled, true otherwise
     */
    public static boolean cancelDecompress() {
        return sActiveTask instanceof DecompressTask
                && sActiveTask.cancel(true);
    }

    /**
     * Start activating the Crosswalk runtime in background. The activation is not cancelable.
     *
     * <p>This method must be invoked on the UI thread.
     *
     * @param listener The {@link ActivateListener} to use
     */
    public static void startActivate(ActivateListener listener) {
        new ActivateTask(listener).execute();
    }

    /**
     * Start downloading the Crosswalk runtime in background via Android DownlomadManager
     *
     * <p>This method must be invoked on the UI thread.
     *
     * @param listener The {@link DownloadListener} to use
     * @param context  The context to get DownloadManager
     * @param url      The URL of the Crosswalk runtime
     */
    public static void startDownloadManager(DownloadListener listener, Context context,
                                            String url) {
        new DownloadManagerTask(listener, context, url).execute();
    }

    /**
     * Attempt to cancel download manager
     */
    public static void cancelDownloadManager() {
        if (sActiveTask instanceof DownloadManagerTask) {
            sActiveTask.cancel(true);
        }
    }

    /**
     * Start downloading the Crosswalk runtime in background via HTTP connection
     *
     * <p>This method must be invoked on the UI thread.
     *
     * @param listener The {@link DownloadListener} to use
     * @param context  The context to get DownloadManager
     * @param url      The URL of the Crosswalk runtime
     */
    public static void startHttpDownload(DownloadListener listener, Context context, String url) {
        new HttpDownloadTask(listener, context, url).execute();
    }

    /**
     * Attempt to cancel http download
     *
     * @return False if download is not running or could not be cancelled, true otherwise
     */
    public static boolean cancelHttpDownload() {
        return sActiveTask instanceof HttpDownloadTask
                && sActiveTask.cancel(true);
    }

    /**
     * Interface used to decompress the Crosswalk runtime
     */
    public interface DecompressListener {
        /**
         * Run on the UI thread to notify decompression is started.
         *
         * <p> This method will not be invoked if the Crosswalk runtime is not compressed or has
         * already been decompressed.
         */
        void onDecompressStarted();

        /**
         * Run on the UI thread to notify decompression is cancelled.
         */
        void onDecompressCancelled();

        /**
         * Run on the UI thread to notify decompression is completed successfully.
         */
        void onDecompressCompleted();
    }

    /**
     * Interface used to activate the Crosswalk runtime
     */
    public interface ActivateListener {
        /**
         * Run on the UI thread to notify activation is started
         */
        void onActivateStarted();

        /**
         * Run on the UI thread to notify activation failed
         */
        void onActivateFailed();

        /**
         * Run on the UI thread to notify activation is completed successfully
         */
        void onActivateCompleted();
    }

    /**
     * Interface used to download the Crosswalk runtime
     */
    public interface DownloadListener {
        /**
         * Run on the UI thread to notify download is started
         */
        void onDownloadStarted();

        /**
         * Run on the UI thread to notify the download progress
         *
         * @param percentage Shows the download progress in percentage
         */
        void onDownloadUpdated(int percentage);

        /**
         * Run on the UI thread to notify download is cancelled
         */
        void onDownloadCancelled();

        /**
         * Run on the UI thread to notify download is completed successfully
         *
         * @param uri The Uri where the downloaded file is stored
         */
        void onDownloadCompleted(Uri uri);

        /**
         * Run on the UI thread to notify download failed
         *
         * @param status The download status defined in android.app.DownloadManager.
         *               The value maybe STATUS_FAILED or STATUS_PAUSED
         * @param error  The download error defined in android.app.DownloadManager.
         *               This parameter only makes sense when the status is STATUS_FAILED
         */
        void onDownloadFailed(int status, int error);
    }

    private static class DecompressTask extends AsyncTask<Void, Integer, Integer> {
        DecompressListener mListener;
        boolean mIsCompressed;
        boolean mIsDecompressed;

        DecompressTask(DecompressListener listener) {
            super();
            mListener = listener;
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "DecompressTask started");
            sActiveTask = this;

            mIsCompressed = XWalkDecompressor.isLibraryCompressed();
            if (mIsCompressed) {
                SharedPreferences sp = MyXWalkEnvironment.getSharedPreferences();
                int version = sp.getInt("version", 0);
                mIsDecompressed = version == XWalkAppVersion.API_VERSION;
            }
            if (mIsCompressed && !mIsDecompressed) mListener.onDecompressStarted();
        }

        @Override
        protected Integer doInBackground(Void... params) {
            if (!mIsCompressed || mIsDecompressed) return 0;

            if (!XWalkDecompressor.decompressLibrary()) return 1;

            SharedPreferences sp = MyXWalkEnvironment.getSharedPreferences();
            sp.edit().putInt("version", XWalkAppVersion.API_VERSION).apply();
            return 0;
        }

        @Override
        protected void onCancelled(Integer result) {
            Log.d(TAG, "DecompressTask cancelled");
            sActiveTask = null;
            mListener.onDecompressCancelled();
        }

        @Override
        protected void onPostExecute(Integer result) {
            Log.d(TAG, "DecompressTask finished, " + result);
            if (result != 0) {
                throw new RuntimeException("Decompression Failed");
            }
            sActiveTask = null;
            mListener.onDecompressCompleted();
        }
    }

    private static class ActivateTask extends AsyncTask<Void, Integer, Integer> {
        ActivateListener mListener;

        ActivateTask(ActivateListener listener) {
            super();
            mListener = listener;
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "ActivateTask started");
            sActiveTask = this;
            mListener.onActivateStarted();
        }

        @Override
        protected Integer doInBackground(Void... params) {
            if (XWalkCoreWrapper.getInstance() != null) return -1;
            return XWalkCoreWrapper.attachXWalkCore();
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == XWalkLibraryInterface.STATUS_MATCH) {
                XWalkCoreWrapper.dockXWalkCore();
            }

            Log.d(TAG, "ActivateTask finished, " + result);
            sActiveTask = null;
            if (result > XWalkLibraryInterface.STATUS_MATCH) {
                mListener.onActivateFailed();
            } else {
                mListener.onActivateCompleted();
            }
        }
    }

    private static class DownloadManagerTask extends AsyncTask<Void, Integer, Integer> {
        private static final int QUERY_INTERVAL_MS = 100;
        private static final int MAX_PAUSED_COUNT = 6000; // 10 minutes

        private final DownloadListener mListener;
        private final Context mContext;
        private final String mDownloadUrl;
        private final DownloadManager mDownloadManager;
        private long mDownloadId;

        DownloadManagerTask(DownloadListener listener, Context context, String url) {
            super();
            mListener = listener;
            mContext = context;
            mDownloadUrl = url;
            mDownloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "DownloadManagerTask started, " + mDownloadUrl);
            sActiveTask = this;

            cleanDownload();

            Request request = new Request(Uri.parse(mDownloadUrl));
            request.setDestinationInExternalFilesDir(
                    mContext, Environment.DIRECTORY_DOWNLOADS, DEFAULT_DOWNLOAD_FILE_NAME);
            if (isSilentDownload()) {
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
            }
            mDownloadId = mDownloadManager.enqueue(request);

            mListener.onDownloadStarted();
        }

        @Override
        protected Integer doInBackground(Void... params) {
            if (mDownloadUrl == null) return DownloadManager.STATUS_FAILED;

            Query query = new Query().setFilterById(mDownloadId);
            int pausedCount = 0;

            while (!isCancelled()) {
                try {
                    Thread.sleep(QUERY_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }

                Cursor cursor = mDownloadManager.query(query);
                if (cursor == null || !cursor.moveToFirst()) continue;

                int totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int downloadIdx = cursor.getColumnIndex(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int totalSize = cursor.getInt(totalIdx);
                int downloadSize = cursor.getInt(downloadIdx);
                if (totalSize > 0) publishProgress(downloadSize, totalSize);

                int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIdx);
                if (status == DownloadManager.STATUS_FAILED ||
                        status == DownloadManager.STATUS_SUCCESSFUL) {
                    return status;
                } else if (status == DownloadManager.STATUS_PAUSED) {
                    if (++pausedCount == MAX_PAUSED_COUNT) return status;
                }
            }

            return DownloadManager.STATUS_RUNNING;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            Log.d(TAG, "DownloadManagerTask updated: " + progress[0] + "/" + progress[1]);
            int percentage;
            if (progress[1] > 0) {
                percentage = (int) (progress[0] * 100.0 / progress[1]);
                mListener.onDownloadUpdated(percentage);
            }
        }

        @Override
        protected void onCancelled(Integer result) {
            mDownloadManager.remove(mDownloadId);

            Log.d(TAG, "DownloadManagerTask cancelled");
            sActiveTask = null;
            mListener.onDownloadCancelled();
        }

        @Override
        protected void onPostExecute(Integer result) {
            Log.d(TAG, "DownloadManagerTask finished, " + result);
            sActiveTask = null;

            if (result == DownloadManager.STATUS_SUCCESSFUL) {
                try {
                    Uri uri = mDownloadManager.getUriForDownloadedFile(mDownloadId);
                    Log.d(TAG, "Uri for downloaded file: " + uri);

                    if (uri != null) {
                        if (uri.getScheme().equals("content")) {
                            Query query = new Query().setFilterById(mDownloadId);
                            Cursor cursor = mDownloadManager.query(query);
                            if (cursor != null && cursor.moveToFirst()) {
                                int index = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                                uri = Uri.parse(cursor.getString(index));
                            }
                        }
                        mListener.onDownloadCompleted(uri);
                    }
                } catch (IllegalStateException ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    mListener.onDownloadFailed(result, DownloadManager.ERROR_UNKNOWN);
                }
            } else {
                int error = DownloadManager.ERROR_UNKNOWN;
                if (result == DownloadManager.STATUS_FAILED) {
                    Query query = new Query().setFilterById(mDownloadId);
                    Cursor cursor = mDownloadManager.query(query);
                    if (cursor != null && cursor.moveToFirst()) {
                        int reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                        error = cursor.getInt(reasonIdx);
                    }
                }
                mListener.onDownloadFailed(result, error);
            }
        }

        private boolean isSilentDownload() {
            try {
                PackageManager packageManager = mContext.getPackageManager();
                PackageInfo packageInfo = packageManager.getPackageInfo(
                        mContext.getPackageName(), PackageManager.GET_PERMISSIONS);
                return Arrays.asList(packageInfo.requestedPermissions).contains(
                        DOWNLOAD_WITHOUT_NOTIFICATION);
            } catch (NameNotFoundException | NullPointerException ignored) {
            }
            return false;
        }

        private void cleanDownload() {
            String savedFile = DEFAULT_DOWNLOAD_FILE_NAME;

            // cleanup old downloads
            File downloadDir = FileHelpers.getDownloadDir(mContext); // getCacheDir(mContext)
            File downloadFile = new File(downloadDir, savedFile);
            if (downloadFile.isFile() && downloadFile.canWrite()) downloadFile.delete();

            DownloadManager.Query query = new DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);
            Cursor cursor = mDownloadManager.query(query);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    @SuppressLint("Range") long id = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
                    @SuppressLint("Range") String localFilename = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                    if (localFilename.contains(savedFile))
                        mDownloadManager.remove(id);
                }
            }
        }
    }

    // This is used only in download mode where we want to save the downloaded file to application
    // private storage and it's also intended to solve the exception found in XWALK-5951
    private static class HttpDownloadTask extends AsyncTask<Void, Integer, Integer> {
        private static final int UPDATE_INTERVAL_MS = 500;
        private static final int DOWNLOAD_SUCCESS = 0;
        private static final int DOWNLOAD_FAILED = -1;

        private final DownloadListener mListener;
        private final Context mContext;
        private final File XWALK_DOWNLOAD_DIR;
        private final String mDownloadUrl;
        private File mDownloadedFile;
        private long mProgressUpdateTime;

        HttpDownloadTask(DownloadListener listener, Context context, String url) {
            super();
            mListener = listener;
            mContext = context;
            mDownloadUrl = url;
            XWALK_DOWNLOAD_DIR = FileHelpers.getDownloadDir(mContext);
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "HttpDownloadTask started, " + mDownloadUrl);
            sActiveTask = this;

            mDownloadedFile = new File(XWALK_DOWNLOAD_DIR, DEFAULT_DOWNLOAD_FILE_NAME);
            mDownloadedFile.mkdirs();
            mDownloadedFile.deleteOnExit();

            mListener.onDownloadStarted();
        }

        @Override
        protected Integer doInBackground(Void... params) {
            if (mDownloadUrl == null) return DOWNLOAD_FAILED;
            if (mDownloadedFile.exists()) mDownloadedFile.delete();

            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(mDownloadUrl);
                SSLSocketFactory sf = new TlsSocketFactory();
                HttpsURLConnection.setDefaultSSLSocketFactory(sf);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage());
                    return DOWNLOAD_FAILED;
                }

                int fileLength = connection.getContentLength();

                input = connection.getInputStream();
                output = new FileOutputStream(mDownloadedFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    if (isCancelled()) return DOWNLOAD_FAILED;
                    total += count;
                    output.write(data, 0, count);

                    long time = SystemClock.uptimeMillis();
                    if (time - mProgressUpdateTime > UPDATE_INTERVAL_MS) {
                        mProgressUpdateTime = time;
                        publishProgress((int) total, fileLength);
                    }
                }
                output.flush();
            } catch (Exception e) {
                // TODO: modified
                // return DOWNLOAD_FAILED;
                if (mDownloadedFile.isFile()) mDownloadedFile.delete();
                throw new IllegalStateException(e);
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (IOException ignored) {
                }

                if (connection != null) connection.disconnect();
            }
            return DOWNLOAD_SUCCESS;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            Log.d(TAG, "HttpDownloadTask updated: " + progress[0] + "/" + progress[1]);
            int percentage = 0;
            if (progress[1] > 0) percentage = (int) (progress[0] * 100.0 / progress[1]);
            mListener.onDownloadUpdated(percentage);
        }

        @Override
        protected void onCancelled(Integer result) {
            Log.d(TAG, "HttpDownloadTask cancelled");
            sActiveTask = null;
            mListener.onDownloadCancelled();
        }

        @Override
        protected void onPostExecute(Integer result) {
            Log.d(TAG, "HttpDownloadTask finished, " + result);
            sActiveTask = null;

            if (result == DOWNLOAD_SUCCESS) {
                mListener.onDownloadCompleted(Uri.fromFile(mDownloadedFile));
            } else {
                // Error codes is not used in download mode.
                mListener.onDownloadFailed(DOWNLOAD_FAILED, 0);
            }
        }
    }
}
