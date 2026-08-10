package com.baxailab.cadebot.data.mock

import android.content.Context
import com.baxailab.cadebot.data.model.MenuResponse
import com.baxailab.cadebot.data.model.TableInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockMenuService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getMenu(): MenuResponse {
        val raw = context.assets.open("config/menu.json").bufferedReader().readText()
        return json.decodeFromString(raw)
    }

    fun getTables(): List<TableInfo> {
        val raw = context.assets.open("config/table_mapping.json").bufferedReader().readText()
        val parsed = json.decodeFromString<TableMappingResponse>(raw)
        return parsed.tables.map { TableInfo(it.tableId, it.tablePointId, it.displayName, it.zone) }
    }
}

@kotlinx.serialization.Serializable
private data class TableMappingResponse(val tables: List<TableEntry>)

@kotlinx.serialization.Serializable
private data class TableEntry(
    val tableId: String,
    val tablePointId: String,
    val displayName: String,
    val zone: String
)
