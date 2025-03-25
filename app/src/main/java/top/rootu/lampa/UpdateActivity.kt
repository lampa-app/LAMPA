package top.rootu.lampa

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.rootu.lampa.helpers.hideSystemUI
import top.rootu.lampa.helpers.Updater


class UpdateActivity : BaseActivity() {

//    private val requestPermissionLauncher =
//        registerForActivityResult(
//            ActivityResultContracts.RequestPermission()
//        ) { isGranted: Boolean ->
//            if (isGranted) {
//                recreate()
//            }
//        }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_update)
        hideSystemUI()
//        val perms = arrayOf(
//            Manifest.permission.WRITE_EXTERNAL_STORAGE,
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        )
//        perms.forEach { perm ->
//            when {
//                ContextCompat.checkSelfPermission(
//                    this,
//                    perm
//                ) == PackageManager.PERMISSION_GRANTED -> {
//                    // granted
//                }
//                ActivityCompat.shouldShowRequestPermissionRationale(this, perm) -> {
//                    requestPermissionLauncher.launch(perm)
//                }
//                // not granted
//                else -> {
//                    requestPermissionLauncher.launch(perm)
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
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