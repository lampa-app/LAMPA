package top.rootu.lampa.helpers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object Coroutines {
    private val scopeList = hashMapOf<String, Pair<Job, CoroutineScope>>()
    private val jobList = hashMapOf<String, MutableList<Job>>()

    private fun getScope(name: String, dispatcher: CoroutineDispatcher = Dispatchers.IO): CoroutineScope {
        synchronized(scopeList) {
            val scope = scopeList.get(name)
            scope?.let { return it.second }
        }

        val job = Job()
        val sc = CoroutineScope(dispatcher + job)
        synchronized(scopeList) { scopeList[name] = Pair(job, sc) }
        jobList[name] = mutableListOf()
        return sc
    }

    fun running(name: String): Boolean {
        //Log.d("*****", "joblist[$name]: ${jobList[name]}")
        var active = false
        val jobList = jobList[name]
        jobList?.forEach { job ->
            active = job.isActive
        }
        return active
    }

    fun launch(name: String, fn: suspend () -> Unit) {
        synchronized(scopeList) {
            val scope = getScope(name)
            val jb = scope.launch { fn() }
            jobList[name]?.add(jb)
        }
    }

    fun remove(name: String) {
        cancel(name)
        synchronized(scopeList) {
            scopeList.remove(name)
        }
    }

    fun cancel(name: String) {
        synchronized(scopeList) {
            val scope = scopeList.get(name)
            scope?.let {
                it.first.cancelChildren()
                jobList[name]?.clear()
            }
        }
    }

    fun join(name: String) {
        runBlocking {
            //TODO may be crash
            jobList[name]?.joinAll()
        }
    }

}