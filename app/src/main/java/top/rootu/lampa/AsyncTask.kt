package top.rootu.lampa

import kotlinx.coroutines.*
import top.rootu.lampa.helpers.Helpers.printLog
import java.util.concurrent.Executors

enum class Status {
    PENDING,
    RUNNING,
    FINISHED
}

abstract class AsyncTask<Params, Progress, Result>(private val taskName: String) {

    companion object {
        private const val TAG = "AsyncTask"
        private var threadPoolExecutor: CoroutineDispatcher? = null
    }

    @Volatile
    var status: Status = Status.PENDING
    private var preJob: Job? = null
    private var bgJob: Deferred<Result>? = null

    @Volatile
    var isCancelled = false

    abstract fun doInBackground(vararg params: Params?): Result
    open fun onProgressUpdate(vararg values: Progress?) {}
    open fun onPostExecute(result: Result?) {}
    open fun onPreExecute() {}
    open fun onCancelled(result: Result?) {}

    /**
     * Executes background task parallel with other background tasks in the queue using
     * default thread pool.
     */
    fun execute(vararg params: Params?) {
        execute(Dispatchers.Default, *params)
    }

    /**
     * Executes background tasks sequentially with other background tasks in the queue using
     * single thread executor @Executors.newSingleThreadExecutor().
     */
    fun executeOnExecutor(vararg params: Params?) {
        if (threadPoolExecutor == null) {
            threadPoolExecutor = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        }
        execute(threadPoolExecutor!!, *params)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun execute(dispatcher: CoroutineDispatcher, vararg params: Params?) {
        if (status != Status.PENDING) {
            when (status) {
                Status.RUNNING -> throw IllegalStateException("Cannot execute task: the task is already running.")
                Status.FINISHED -> throw IllegalStateException("Cannot execute task: the task has already been executed (a task can be executed only once).")
                else -> {}
            }
        }

        status = Status.RUNNING

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Execute onPreExecute on the main thread
                preJob = launch(Dispatchers.Main) {
                    printLog("$taskName onPreExecute started")
                    onPreExecute()
                    printLog("$taskName onPreExecute finished")
                }
                preJob!!.join()

                // Execute doInBackground on the specified dispatcher
                bgJob = async(dispatcher) {
                    printLog("$taskName doInBackground started")
                    doInBackground(*params)
                }

                // Wait for the background task to complete
                val result = bgJob!!.await()

                // Execute onPostExecute on the main thread
                if (!isCancelled) {
                    withContext(Dispatchers.Main) {
                        printLog("$taskName doInBackground finished")
                        onPostExecute(result)
                        status = Status.FINISHED
                    }
                }
            } catch (e: CancellationException) {
                printLog("$taskName was cancelled: ${e.message}")
                status = Status.FINISHED
                withContext(Dispatchers.Main) {
                    onCancelled(bgJob?.getCompleted())
                }
            } catch (e: Exception) {
                printLog("$taskName encountered an error: ${e.message}")
                status = Status.FINISHED
                withContext(Dispatchers.Main) {
                    onCancelled(null)
                }
            }
        }
    }

    fun cancel(mayInterruptIfRunning: Boolean) {
        if (preJob == null || bgJob == null) {
            printLog("$taskName has already been cancelled/finished/not yet started.")
            return
        }

        if (mayInterruptIfRunning || (!preJob!!.isActive && !bgJob!!.isActive)) {
            isCancelled = true
            status = Status.FINISHED

            if (bgJob!!.isCompleted) {
                CoroutineScope(Dispatchers.Main).launch {
                    onCancelled(bgJob!!.await())
                }
            }

            preJob?.cancel(CancellationException("PreExecute: Coroutine Task cancelled"))
            bgJob?.cancel(CancellationException("doInBackground: Coroutine Task cancelled"))
            printLog("$taskName has been cancelled.")
        }
    }

    fun publishProgress(vararg progress: Progress) {
        CoroutineScope(Dispatchers.Main).launch { //need to update main thread
            if (!isCancelled) {
                onProgressUpdate(*progress)
            }
        }
    }
}