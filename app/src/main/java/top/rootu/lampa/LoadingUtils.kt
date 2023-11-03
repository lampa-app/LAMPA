package top.rootu.lampa

import android.content.Context

class LoadingUtils {

    companion object {
        private var lottieLoader: LottieLoader? = null
        fun showDialog(
            context: Context?,
            isCancelable: Boolean
        ) {
            hideDialog()
            if (context != null) {
                try {
                    lottieLoader = LottieLoader(context)
                    lottieLoader?.let { loader ->
                        loader.setCanceledOnTouchOutside(true)
                        loader.setCancelable(isCancelable)
                        loader.show()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun hideDialog() {
            if (lottieLoader != null && lottieLoader?.isShowing!!) {
                lottieLoader = try {
                    lottieLoader?.dismiss()
                    null
                } catch (e: Exception) {
                    null
                }
            }
        }

    }

}