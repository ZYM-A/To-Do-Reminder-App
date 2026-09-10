package com.richang.todo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.richang.todo.data.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as TodoApplication).store
    val tasks = store.tasks
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun refresh() { viewModelScope.launch {
        try { store.reload() }
        catch (e: Exception) { if (e is CancellationException) throw e; _error.value = "读取任务失败，请重试" }
        finally { _loading.value = false }
    } }
    private fun mutate(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { block() }
            catch (e: Exception) { if (e is CancellationException) throw e; _error.value = e.message ?: "操作失败，请重试" }
            finally { _busy.value = false }
        }
    }
    fun save(task: Task, onSaved: () -> Unit) = mutate { store.save(task); onSaved() }
    fun toggle(task: Task) = mutate { store.toggle(task.id) }
    fun delete(task: Task, onDeleted: () -> Unit) = mutate { store.delete(task.id); onDeleted() }
    fun clearError() { _error.value = null }
}
