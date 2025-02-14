package top.rootu.lampa

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.rootu.lampa.helpers.Helpers.hideSystemUI
import top.rootu.lampa.helpers.Updater


class UpdateActivity : AppCompatActivity() {
//    Not required for getExternalFilesDir(null)
//    private val requestPermissionLauncher =
//        registerForActivityResult(
//            ActivityResultContracts.RequestPermission()
//        ) { isGranted: Boolean ->
//            if (isGranted) {
//                recreate()
//            }
//        }
//
//    @RequiresApi(Build.VERSION_CODES.R)
//    val storagePermissionResultLauncher =
//        registerForActivityResult(StartActivityForResult(),
//            ActivityResultCallback<ActivityResult?> {
//                if (Environment.isExternalStorageManager()) {
//                    recreate() // Permission granted. Now resume workflow.
//                } else {
//                    //App.toast(R.string.app_requires_manage_storage_perm)
//                    //finish()
//                }
//            })

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_update)
        hideSystemUI()

//    Not required for getExternalFilesDir(null)
//        val perms = if (VERSION.SDK_INT >= Build.VERSION_CODES.R)
//            arrayOf(
//                Manifest.permission.MANAGE_EXTERNAL_STORAGE
//            )
//        else arrayOf(
//            Manifest.permission.WRITE_EXTERNAL_STORAGE,
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        )
//        if (VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//            if (!Environment.isExternalStorageManager()) {
//                App.toast(R.string.app_requires_manage_storage_perm)
//                val intent = Intent(
//                    ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
//                    Uri.parse("package:" + BuildConfig.APPLICATION_ID)
//                )
//                storagePermissionResultLauncher.launch(intent)
//            }
//        } else {
//            perms.forEach { perm ->
//                when {
//                    ContextCompat.checkSelfPermission(
//                        this,
//                        perm
//                    ) == PackageManager.PERMISSION_GRANTED -> {
//                        // granted
//                    }
//
//                    ActivityCompat.shouldShowRequestPermissionRationale(this, perm) -> {
//                        requestPermissionLauncher.launch(perm)
//                    }
//                    // not granted
//                    else -> {
//                        requestPermissionLauncher.launch(perm)
//                    }
//                }
//            }
//        }

        findViewById<ProgressBar>(R.id.pbUpdate).visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            if (!Updater.check())
                finish()
        }
        findViewById<ProgressBar>(R.id.pbUpdate).visibility = View.GONE

        findViewById<TextView>(R.id.tvUpdateTitle)?.text =
            String.format(getString(R.string.update_app_found), getString(R.string.app_name))

        findViewById<TextView>(R.id.tvCurrentVersion)?.text =
            ("${getString(R.string.update_cur_version)}: ${BuildConfig.VERSION_NAME}")
        findViewById<TextView>(R.id.tvNewVersion)?.text =
            ("${getString(R.string.update_new_version)}: ${Updater.getVersion()}")
        findViewById<TextView>(R.id.tvOverview)?.text = Updater.getOverview()

        findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnUpdate)?.setOnClickListener {
            it.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                if (update())
                    finish()
                withContext(Dispatchers.Main) {
                    it.isEnabled = true
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private suspend fun update(): Boolean {
        val progressBar = findViewById<ProgressBar>(R.id.pbUpdate)
        withContext(Dispatchers.Main) {
            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = false
            findViewById<TextView>(R.id.tvUpdateInfo).setText(R.string.update_loading)
        }
        try {
            Updater.installNewVersion { prc ->
                Handler(Looper.getMainLooper()).post {
                    if (VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        progressBar.setProgress(prc, true)
                    else
                        progressBar.progress = prc
                    findViewById<TextView>(R.id.tvUpdatePrc).text = "$prc%"
                }
            }
            delay(1000)
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                progressBar.isIndeterminate = true
                findViewById<TextView>(R.id.tvUpdateInfo).text = ""
                findViewById<TextView>(R.id.tvUpdatePrc).text = ""
            }
            return true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                val msg = "Error: " + (e.message ?: "")
                findViewById<TextView>(R.id.tvUpdateInfo).text = msg
                progressBar.visibility = View.GONE
            }
        }
        return false
    }
}