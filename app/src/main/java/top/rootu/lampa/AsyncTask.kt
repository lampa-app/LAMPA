package top.rootu.lampa

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * An abstract class that enables proper and easy use of background threads with coroutines.
 * This class is a modern replacement for Android's deprecated AsyncTask using Kotlin coroutines.
 *
 * @param taskName The name of the task used for debugging purposes
 * @param Params The type of the parameters sent to the task upon execution
 * @param Progress The type of the progress units published during the background computation
 * @param Result The type of the result of the background computation
 */
abstract class AsyncTask<Params, Progress, Result>(private val taskName: String) {

    /**
     * Indicates the current status of the task.
     */
    enum class Status {
        PENDING, // The task has not been executed yet
        RUNNING, // The task is currently running
        FINISHED //The task has finished executing
    }

    companion object {
        /**
         * Single thread dispatcher used for sequential task execution.
         * Lazily initialized on first use.
         */
        private val singleThreadDispatcher by lazy {
            Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        }
    }

    @Volatile
    private var _status: Status = Status.PENDING

    /**
     * Returns the current status of the task.
     */
    val status: Status get() = _status

    private var parentJob: Job = Job()
    private var backgroundJob: Deferred<Result>? = null

    @Volatile
    var isCancelled = false
        private set

    /**
     * Override this method to perform a computation on a background thread.
     * @param params The parameters of the task
     * @return A result, defined by the subclass of this task
     */
    protected abstract fun doInBackground(vararg params: Params): Result

    /**
     * Runs on the main thread after [publishProgress] is invoked.
     * @param values The progress values to update the UI with
     */
    protected open fun onProgressUpdate(vararg values: Progress) {}

    /**
     * Runs on the main thread after [doInBackground] completes successfully.
     * @param result The result of the background computation if available, null otherwise
     */
    protected open fun onPostExecute(result: Result) {}

    /**
     * Runs on the main thread before [doInBackground] is executed.
     */
    protected open fun onPreExecute() {}

    /**
     * Runs on the main thread after cancellation with the result available.
     * @param result The result if available, null otherwise
     */
    protected open fun onCancelled(result: Result?) {}

    /**
     * Runs on the main thread after cancellation regardless of result availability.
     */
    protected open fun onCancelled() {}

    /**
     * Executes the task with the specified parameters in parallel with other tasks.
     * @param params The parameters of the task
     */
    fun execute(vararg params: Params) = execute(Dispatchers.IO, *params)

    /**
     * Executes the task with the specified parameters sequentially (one at a time).
     * @param params The parameters of the task
     */
    fun executeOnExecutor(vararg params: Params) = execute(singleThreadDispatcher, *params)

    /**
     * Internal implementation of task execution.
     * @param dispatcher The coroutine context to run the background task in
     * @param params The parameters of the task
     * @throws IllegalStateException if the task is already running or has been executed
     */
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private fun execute(dispatcher: CoroutineContext, vararg params: Params) {
        if (_status != Status.PENDING) {
            throw when (_status) {
                Status.RUNNING -> IllegalStateException("Task is already running")
                Status.FINISHED -> IllegalStateException("Task can be executed only once")
                else -> IllegalStateException()
            }
        }

        _status = Status.RUNNING
        isCancelled = false

        CoroutineScope(Dispatchers.Main + parentJob).launch {
            try {
                // Pre-execute phase
                // debugLog(taskName, "$taskName onPreExecute started")
                onPreExecute()
                // debugLog(taskName, "$taskName onPreExecute finished")

                // Background execution
                backgroundJob = GlobalScope.async(dispatcher) {
                    // debugLog(taskName, "$taskName doInBackground started")
                    doInBackground(*params).also {
                        // debugLog(taskName, "$taskName doInBackground finished")
                    }
                }

                // Post-execute phase
                backgroundJob?.let { deferred ->
                    val result = deferred.await()
                    if (!isCancelled) {
                        onPostExecute(result)
                    }
                }
            } catch (e: CancellationException) {
                Log.e(taskName, "$taskName was cancelled: ${e.message}")
                onCancelled(backgroundJob?.getCompleted())
                onCancelled()
            } catch (e: Exception) {
                Log.e(taskName, "$taskName encountered an error: ${e.message}")
                onCancelled(null)
                onCancelled()
            } finally {
                _status = Status.FINISHED
            }
        }
    }

    /**
     * Attempts to cancel execution of this task.
     * @param mayInterruptIfRunning true if the thread executing this task should be interrupted;
     *                              false otherwise
     */
    fun cancel(mayInterruptIfRunning: Boolean = true) {
        if (_status == Status.FINISHED) return

        isCancelled = true
        _status = Status.FINISHED

        if (mayInterruptIfRunning) {
            parentJob.cancel("Task $taskName cancelled")
            backgroundJob?.cancel()
            Log.d(taskName, "$taskName has been cancelled with interruption")
        } else {
            Log.d(taskName, "$taskName cancellation requested (no interruption)")
        }
    }

    /**
     * Publishes progress to the main thread.
     * @param progress One or more progress values to publish
     */
    protected fun publishProgress(vararg progress: Progress) {
        if (!isCancelled) {
            CoroutineScope(Dispatchers.Main).launch {
                onProgressUpdate(*progress)
            }
        }
    }
}