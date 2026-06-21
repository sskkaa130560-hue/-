package com.example.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SuitcaseRepository(private val suitcaseDao: SuitcaseDao) {

    val allSuitcases: Flow<List<Suitcase>> = suitcaseDao.getAllSuitcases()
    val allSuitcasesWithStats: Flow<List<SuitcaseWithStats>> = suitcaseDao.getAllSuitcasesWithStats()
    val allItems: Flow<List<SuitcaseItem>> = suitcaseDao.getAllItemsFlow()

    fun getItemsForSuitcase(suitcaseId: Long): Flow<List<SuitcaseItem>> {
        return suitcaseDao.getItemsForSuitcase(suitcaseId)
    }

    suspend fun createSuitcase(name: String): Long {
        return suitcaseDao.insertSuitcase(Suitcase(name = name))
    }

    suspend fun updateSuitcase(suitcase: Suitcase) {
        suitcaseDao.updateSuitcase(suitcase)
    }

    suspend fun deleteSuitcase(suitcase: Suitcase) {
        suitcaseDao.deleteSuitcase(suitcase)
    }

    suspend fun insertItem(item: SuitcaseItem): Long {
        return suitcaseDao.insertItem(item)
    }

    suspend fun updateItem(item: SuitcaseItem) {
        suitcaseDao.updateItem(item)
    }

    suspend fun deleteItem(item: SuitcaseItem) {
        suitcaseDao.deleteItem(item)
    }

    /**
     * Parses the given text input and bulk imports items into a newly created suitcase.
     * Returns the ID of the newly created suitcase.
     */
    suspend fun importSuitcaseFromText(suitcaseName: String, text: String): Long {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                if (trimmed.startsWith("{")) {
                    val obj = JSONObject(trimmed)
                    val name = if (obj.has("name")) obj.getString("name") else suitcaseName.trim().ifEmpty {
                        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                        "Импорт из JSON $dateStr"
                    }
                    val suitcaseId = suitcaseDao.insertSuitcase(Suitcase(name = name))
                    val itemsToInsert = mutableListOf<SuitcaseItem>()
                    if (obj.has("items")) {
                        val arr = obj.getJSONArray("items")
                        for (i in 0 until arr.length()) {
                            val itemObj = arr.getJSONObject(i)
                            itemsToInsert.add(
                                SuitcaseItem(
                                    suitcaseId = suitcaseId,
                                    categoryName = if (itemObj.has("category")) itemObj.getString("category") else if (itemObj.has("categoryName")) itemObj.getString("categoryName") else "Общие вещи",
                                    name = itemObj.getString("name"),
                                    isPacked = if (itemObj.has("isPacked")) itemObj.getBoolean("isPacked") else false,
                                    displayOrder = i
                                )
                            )
                        }
                    }
                    if (itemsToInsert.isNotEmpty()) {
                        suitcaseDao.insertItems(itemsToInsert)
                    }
                    return suitcaseId
                } else {
                    val arr = JSONArray(trimmed)
                    val name = suitcaseName.trim().ifEmpty {
                        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
                        "Импорт из JSON $dateStr"
                    }
                    val suitcaseId = suitcaseDao.insertSuitcase(Suitcase(name = name))
                    val itemsToInsert = mutableListOf<SuitcaseItem>()
                    for (i in 0 until arr.length()) {
                        val itemObj = arr.getJSONObject(i)
                        itemsToInsert.add(
                            SuitcaseItem(
                                suitcaseId = suitcaseId,
                                categoryName = if (itemObj.has("category")) itemObj.getString("category") else if (itemObj.has("categoryName")) itemObj.getString("categoryName") else "Общие вещи",
                                name = itemObj.getString("name"),
                                isPacked = if (itemObj.has("isPacked")) itemObj.getBoolean("isPacked") else false,
                                displayOrder = i
                            )
                        )
                    }
                    if (itemsToInsert.isNotEmpty()) {
                        suitcaseDao.insertItems(itemsToInsert)
                    }
                    return suitcaseId
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val parsedCategories = parseTravelList(text)
        
        // Ensure we fall back to a reasonable suitcase name if blank
        val name = suitcaseName.trim().ifEmpty {
            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            "Импорт чемодана $dateStr"
        }

        val suitcaseId = suitcaseDao.insertSuitcase(Suitcase(name = name))
        
        val itemsToInsert = mutableListOf<SuitcaseItem>()
        var displayOrderValue = 0
        for (categoryPair in parsedCategories) {
            val categoryName = categoryPair.first
            val itemNames = categoryPair.second
            for (itemName in itemNames) {
                itemsToInsert.add(
                    SuitcaseItem(
                        suitcaseId = suitcaseId,
                        categoryName = categoryName,
                        name = itemName,
                        isPacked = false,
                        displayOrder = displayOrderValue++
                    )
                )
            }
        }

        if (itemsToInsert.isNotEmpty()) {
            suitcaseDao.insertItems(itemsToInsert)
        }

        return suitcaseId
    }

    /**
     * Helper parser for the text format:
     * +Category Name
     * -item1, item2...
     */
    private fun parseTravelList(text: String): List<Pair<String, List<String>>> {
        val categories = mutableListOf<Pair<String, List<String>>>()
        var activeCategory: String? = null
        val activeItems = mutableListOf<String>()

        val lines = text.split("\n")
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("+")) {
                if (activeCategory != null) {
                    categories.add(Pair(activeCategory, activeItems.toList()))
                    activeItems.clear()
                }
                activeCategory = line.substring(1).trim()
            } else if (line.startsWith("-")) {
                val content = line.substring(1).trim()
                if (content.isNotEmpty()) {
                    val parts = content.split(",")
                    for (part in parts) {
                        var itemValue = part.trim()
                        if (itemValue.endsWith(".")) {
                            itemValue = itemValue.substring(0, itemValue.length - 1).trim()
                        }
                        if (itemValue.isNotEmpty()) {
                            activeItems.add(itemValue)
                        }
                    }
                }
            }
        }

        if (activeCategory != null) {
            categories.add(Pair(activeCategory, activeItems.toList()))
        } else if (activeItems.isNotEmpty()) {
            categories.add(Pair("Общие вещи", activeItems.toList()))
        }

        return categories
    }

    suspend fun overwriteDatabase(suitcases: List<Suitcase>, items: List<SuitcaseItem>) {
        suitcaseDao.overwriteDatabase(suitcases, items)
    }
}
