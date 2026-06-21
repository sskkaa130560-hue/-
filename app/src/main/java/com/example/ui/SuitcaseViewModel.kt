package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Suitcase
import com.example.data.SuitcaseDatabase
import com.example.data.SuitcaseItem
import com.example.data.SuitcaseRepository
import com.example.data.SuitcaseWithStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SuitcaseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SuitcaseRepository

    // Reactive list of suitcases
    val suitcasesState: StateFlow<List<SuitcaseWithStats>>

    // Selected Suitcase ID for the detail view
    private val _selectedSuitcaseId = MutableStateFlow<Long?>(null)
    val selectedSuitcaseId: StateFlow<Long?> = _selectedSuitcaseId.asStateFlow()

    // Active suitcase's metadata
    private val _activeSuitcase = MutableStateFlow<Suitcase?>(null)
    val activeSuitcase: StateFlow<Suitcase?> = _activeSuitcase.asStateFlow()

    // Items list for selected suitcase
    val activeItemsState: StateFlow<List<SuitcaseItem>>

    private var isInitialLoading = true

    init {
        val database = SuitcaseDatabase.getDatabase(application)
        repository = SuitcaseRepository(database.suitcaseDao())

        suitcasesState = repository.allSuitcasesWithStats
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        activeItemsState = _selectedSuitcaseId
            .flatMapLatest { id ->
                if (id != null) {
                    repository.getItemsForSuitcase(id)
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Load DB state from internal suitcases.json at app startup, and collect changes to auto-save.
        viewModelScope.launch {
            val context = getApplication<Application>()
            val file = java.io.File(context.filesDir, "suitcases.json")
            if (file.exists()) {
                try {
                    val jsonStr = withContext(Dispatchers.IO) {
                        file.readText()
                    }
                    if (jsonStr.isNotEmpty()) {
                        val (loadedSuitcases, loadedItems) = parseFromJson(jsonStr)
                        repository.overwriteDatabase(loadedSuitcases, loadedItems)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isInitialLoading = false

            combine(repository.allSuitcases, repository.allItems) { suitcases, items ->
                Pair(suitcases, items)
            }.collect { (suitcases, items) ->
                if (!isInitialLoading) {
                    saveToLocalJson(suitcases, items)
                }
            }
        }
    }

    fun selectSuitcase(id: Long?) {
        _selectedSuitcaseId.value = id
        if (id != null) {
            viewModelScope.launch {
                val db = SuitcaseDatabase.getDatabase(getApplication())
                val suitcase = db.suitcaseDao().getSuitcaseById(id)
                _activeSuitcase.value = suitcase
            }
        } else {
            _activeSuitcase.value = null
        }
    }

    fun renameSuitcase(id: Long, newName: String) {
        viewModelScope.launch {
            val db = SuitcaseDatabase.getDatabase(getApplication())
            val suitcase = db.suitcaseDao().getSuitcaseById(id)
            if (suitcase != null) {
                val updated = suitcase.copy(name = newName.trim())
                repository.updateSuitcase(updated)
                if (_selectedSuitcaseId.value == id) {
                    _activeSuitcase.value = updated
                }
            }
        }
    }

    fun createSuitcase(name: String) {
        viewModelScope.launch {
            val id = repository.createSuitcase(name)
            selectSuitcase(id)
        }
    }

    fun deleteSuitcase(suitcaseWithStats: SuitcaseWithStats) {
        viewModelScope.launch {
            if (_selectedSuitcaseId.value == suitcaseWithStats.id) {
                selectSuitcase(null)
            }
            repository.deleteSuitcase(
                Suitcase(
                    id = suitcaseWithStats.id,
                    name = suitcaseWithStats.name,
                    createdAt = suitcaseWithStats.createdAt
                )
            )
        }
    }

    fun deleteActiveSuitcase() {
        val current = _activeSuitcase.value ?: return
        viewModelScope.launch {
            selectSuitcase(null)
            repository.deleteSuitcase(current)
        }
    }

    fun importFromText(name: String, text: String) {
        viewModelScope.launch {
            val id = repository.importSuitcaseFromText(name, text)
            selectSuitcase(id)
        }
    }

    fun toggleItemPacked(item: SuitcaseItem) {
        viewModelScope.launch {
            repository.updateItem(item.copy(isPacked = !item.isPacked))
        }
    }

    fun addItemToActiveSuitcase(category: String, name: String) {
        val suitcaseId = _selectedSuitcaseId.value ?: return
        viewModelScope.launch {
            val cleanCategory = category.trim().ifEmpty { "Общие вещи" }
            if (name.trim().isNotEmpty()) {
                repository.insertItem(
                    SuitcaseItem(
                        suitcaseId = suitcaseId,
                        categoryName = cleanCategory,
                        name = name.trim(),
                        isPacked = false
                    )
                )
            }
        }
    }

    fun deleteItem(item: SuitcaseItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun generateExportText(items: List<SuitcaseItem>): String {
        val grouped = items.groupBy { it.categoryName }
        val builder = java.lang.StringBuilder()
        for ((category, categoryItems) in grouped) {
            builder.append("+").append(category).append("\n")
            for (item in categoryItems) {
                builder.append("-").append(item.name).append("\n")
            }
            builder.append("\n")
        }
        return builder.toString().trim()
    }

    fun generateExportJson(items: List<SuitcaseItem>): String {
        val rootObj = JSONObject()
        val currentSuitcase = _activeSuitcase.value
        rootObj.put("name", currentSuitcase?.name ?: "Список вещей")
        
        val itemsArray = JSONArray()
        for (item in items) {
            val iObj = JSONObject()
            iObj.put("name", item.name)
            iObj.put("category", item.categoryName)
            iObj.put("isPacked", item.isPacked)
            itemsArray.put(iObj)
        }
        rootObj.put("items", itemsArray)
        return rootObj.toString(2)
    }

    private fun saveToLocalJson(suitcases: List<Suitcase>, items: List<SuitcaseItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val jsonStr = serializeToJson(suitcases, items)
                val file = java.io.File(context.filesDir, "suitcases.json")
                file.writeText(jsonStr)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun serializeToJson(suitcases: List<Suitcase>, items: List<SuitcaseItem>): String {
        val rootArray = JSONArray()
        val itemsBySuitcase = items.groupBy { it.suitcaseId }
        for (s in suitcases) {
            val sObj = JSONObject()
            sObj.put("id", s.id)
            sObj.put("name", s.name)
            sObj.put("createdAt", s.createdAt)
            
            val itemsArray = JSONArray()
            val sItems = itemsBySuitcase[s.id] ?: emptyList()
            for (item in sItems) {
                val iObj = JSONObject()
                iObj.put("id", item.id)
                iObj.put("suitcaseId", item.suitcaseId)
                iObj.put("categoryName", item.categoryName)
                iObj.put("name", item.name)
                iObj.put("isPacked", item.isPacked)
                iObj.put("displayOrder", item.displayOrder)
                itemsArray.put(iObj)
            }
            sObj.put("items", itemsArray)
            rootArray.put(sObj)
        }
        return rootArray.toString(2)
    }

    private fun parseFromJson(jsonStr: String): Pair<List<Suitcase>, List<SuitcaseItem>> {
        val suitcases = mutableListOf<Suitcase>()
        val items = mutableListOf<SuitcaseItem>()
        val rootArray = JSONArray(jsonStr)
        for (i in 0 until rootArray.length()) {
            val sObj = rootArray.getJSONObject(i)
            val s = Suitcase(
                id = sObj.getLong("id"),
                name = sObj.getString("name"),
                createdAt = sObj.getLong("createdAt")
            )
            suitcases.add(s)
            
            if (sObj.has("items")) {
                val itemsArray = sObj.getJSONArray("items")
                for (j in 0 until itemsArray.length()) {
                    val iObj = itemsArray.getJSONObject(j)
                    val item = SuitcaseItem(
                        id = iObj.getLong("id"),
                        suitcaseId = iObj.getLong("suitcaseId"),
                        categoryName = iObj.getString("categoryName"),
                        name = iObj.getString("name"),
                        isPacked = iObj.getBoolean("isPacked"),
                        displayOrder = iObj.optInt("displayOrder", 0)
                    )
                    items.add(item)
                }
            }
        }
        return Pair(suitcases, items)
    }
}
