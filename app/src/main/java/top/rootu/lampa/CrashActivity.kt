package top.rootu.lampa

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import androidx.activity.enableEdgeToEdge
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import top.rootu.lampa.databinding.ActivityCrashBinding
import top.rootu.lampa.databinding.ErrorLogSheetBinding
import top.rootu.lampa.helpers.Backup
import top.rootu.lampa.helpers.PermHelpers
import top.rootu.lampa.helpers.copyToClipBoard
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class CrashActivity : BaseActivity() {

    private var bottomSheetDialog: BottomSheetDialog? = null
    private lateinit var binding: ActivityCrashBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCrashBinding.inflate(layoutInflater) //(R.layout.activity_crash)
        setContentView(binding.root)
        val errorDetails = intent.getStringExtra("errorDetails")
        val isShownLogs = intent.getStringExtra("isShownLogs")
        if (isShownLogs != null) {
            errorLogsButtonVisibility(isShownLogs, errorDetails.isNullOrEmpty())
        }

        binding.btRestartApp.setOnClickListener {
            restartApp()
        }
        binding.btShowErrorLogs.setOnClickListener {
            showBottomSheetDialog(true, errorDetails)
            if (!PermHelpers.hasStoragePermissions(this)) {
                PermHelpers.verifyStoragePermissions(this)
            }
        }
        binding.btRestartApp.requestFocus()
    }

    private fun restartApp() {
        val packageManager = packageManager
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    private fun showBottomSheetDialog(
        state: Boolean,
        errorData: String? = getString(R.string.app_crash_no_logs)
    ) {
        if (state) {
            if (bottomSheetDialog != null) {
                if (bottomSheetDialog?.isShowing == true) {
                    bottomSheetDialog?.dismiss()
                }
            }
            bottomSheetDialog = BottomSheetDialog(this, R.style.TransparentDialog)
            val dialogBinding = ErrorLogSheetBinding.inflate(layoutInflater)
            bottomSheetDialog?.setContentView(dialogBinding.root)
            bottomSheetDialog?.setCanceledOnTouchOutside(false)
            bottomSheetDialog?.setCancelable(false)
            dialogBinding.closeDialog.setOnClickListener {
                showBottomSheetDialog(false)
            }
            dialogBinding.tvErrorLogs.text = errorData
            dialogBinding.saveErrorLogs.setOnClickListener {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm")
                val dtm = LocalDateTime.now().format(formatter)
                if (Backup.writeFileSafely("${dtm}.crashlog.txt", errorData.toString()))
                    App.toast("${getString(R.string.app_crash_save_to)} ${Backup.DIR}")
                showBottomSheetDialog(false)
            }
            dialogBinding.copyErrorLogs.setOnClickListener {
                copyToClipBoard(errorData.toString())
                showBottomSheetDialog(false)
                App.toast(R.string.app_crash_copied)
            }

            bottomSheetDialog?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            bottomSheetDialog?.show()
            // img buttons
            val outValue = TypedValue()
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            dialogBinding.saveErrorLogs.apply {
                isClickable = true
                isFocusable = true
                setBackgroundResource(outValue.resourceId)
            }
            dialogBinding.copyErrorLogs.apply {
                isClickable = true
                isFocusable = true
                setBackgroundResource(outValue.resourceId)
            }
            dialogBinding.closeDialog.apply {
                isClickable = true
                isFocusable = true
                setBackgroundResource(outValue.resourceId)
                requestFocus()
            }
        } else {
            if (bottomSheetDialog != null) {
                if (bottomSheetDialog?.isShowing == true) {
                    bottomSheetDialog?.dismiss()
                }
            }
        }
    }

    private fun errorLogsButtonVisibility(isShownLogs: String, errorLogsNullOrEmpty: Boolean) {
        if (errorLogsNullOrEmpty) {
            binding.btShowErrorLogs.isVisible = false
        } else {
            when (isShownLogs.lowercase()) {
                "true" -> {
                    binding.btShowErrorLogs.isVisible = true
                }

                "false" -> {
                    binding.btShowErrorLogs.isVisible = false
                }

                "null" -> {
                    checkAppIsDevelopment()
                }
            }
        }
    }

    private fun checkAppIsDevelopment() {
        // this logic is written for the to check the app is in debug mode or release mode
        val isInDebugApp = 0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE
        binding.btShowErrorLogs.isVisible = isInDebugApp
    }

}