package io.legado.app.ui.book.read.ai.tool

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * AI 工具执行路由 — 根据 tool name 路由到对应 DAO 操作，返回精简的 JSON 字符串
 */
object ToolRouter {

    private const val MAX_BOOKS = 100
    private const val MAX_SOURCES = 100
    private const val MAX_CHAPTERS = 200
    private const val MAX_TOP_BOOKS = 10

    suspend fun execute(name: String, args: Map<*, *>): String {
        return withContext(Dispatchers.IO) {
            try {
                when (name) {
                    "get_bookshelf" -> getBookshelf(args)
                    "search_bookshelf" -> searchBookshelf(args)
                    "get_book_sources" -> getBookSources(args)
                    "get_rss_sources" -> getRssSources(args)
                    "get_reading_stats" -> getReadingStats()
                    "get_book_chapters" -> getBookChapters(args)
                    "get_book_groups" -> getBookGroups()
                    "get_source_groups" -> getSourceGroups()
                    else -> """{"error":"未知工具: $name"}"""
                }
            } catch (e: Exception) {
                """{"error":"${e.message?.replace("\"", "'") ?: "未知错误"}"}"""
            }
        }
    }

    private fun getBookshelf(args: Map<*, *>): String {
        val group = args["group"] as? String
        val books = if (group.isNullOrBlank()) {
            appDb.bookDao.all
        } else {
            val bookGroup = appDb.bookGroupDao.getByName(group)
            if (bookGroup != null) {
                appDb.bookDao.getBooksByGroup(bookGroup.groupId)
            } else {
                return """{"error":"未找到分组: $group"}"""
            }
        }
        val result = books.take(MAX_BOOKS).map { bookToSimple(it) }
        return GSON.toJson(result)
    }

    private fun searchBookshelf(args: Map<*, *>): String {
        val keyword = args["keyword"] as? String
        if (keyword.isNullOrBlank()) {
            return """{"error":"keyword 参数不能为空"}"""
        }
        val books = appDb.bookDao.all.filter {
            it.name.contains(keyword, ignoreCase = true) ||
                    it.author.contains(keyword, ignoreCase = true)
        }
        val result = books.take(MAX_BOOKS).map { bookToSimple(it) }
        return GSON.toJson(result)
    }

    private fun getBookSources(args: Map<*, *>): String {
        val enabled = args["enabled"] as? Boolean
        val group = args["group"] as? String
        val allSources = appDb.bookSourceDao.allPart
        val sources = when (enabled) {
            true -> allSources.filter { it.enabled }
            false -> allSources.filter { !it.enabled }
            else -> allSources
        }
        val filtered = if (!group.isNullOrBlank()) {
            sources.filter { it.bookSourceGroup?.contains(group) == true }
        } else {
            sources
        }
        val result = filtered.take(MAX_SOURCES).map { source ->
            mapOf(
                "name" to source.bookSourceName,
                "url" to source.bookSourceUrl,
                "enabled" to source.enabled,
                "group" to (source.bookSourceGroup ?: ""),
                "weight" to source.weight
            )
        }
        return GSON.toJson(result)
    }

    private fun getRssSources(args: Map<*, *>): String {
        val enabled = args["enabled"] as? Boolean
        val group = args["group"] as? String
        val sources = when {
            enabled == true -> appDb.rssSourceDao.all.filter { it.enabled }
            enabled == false -> appDb.rssSourceDao.all.filter { !it.enabled }
            else -> appDb.rssSourceDao.all
        }
        val filtered = if (!group.isNullOrBlank()) {
            sources.filter { it.sourceGroup?.contains(group) == true }
        } else {
            sources
        }
        val result = filtered.take(MAX_SOURCES).map { source: RssSource ->
            mapOf(
                "name" to source.sourceName,
                "url" to source.sourceUrl,
                "enabled" to source.enabled,
                "group" to (source.sourceGroup ?: "")
            )
        }
        return GSON.toJson(result)
    }

    private fun getReadingStats(): String {
        val totalBooks = appDb.bookDao.allBookCount
        val totalReadTime = appDb.readRecordDao.allTime
        val allRecords = appDb.readRecordDao.allShow

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayRecords = allRecords.filter { it.lastRead >= todayStart }
        val todayReadTime = todayRecords.sumOf { it.readTime }

        val readDays = allRecords.map {
            Calendar.getInstance().apply { timeInMillis = it.lastRead }
                .get(Calendar.DAY_OF_YEAR)
        }.distinct().size

        val topBooks = allRecords.sortedByDescending { it.readTime }
            .take(MAX_TOP_BOOKS)
            .map { mapOf("name" to it.bookName, "readTime" to it.readTime) }

        val result = mapOf(
            "totalBooks" to totalBooks,
            "totalReadTimeMinutes" to (totalReadTime / 60000),
            "todayReadTimeMinutes" to (todayReadTime / 60000),
            "readDays" to readDays,
            "topBooks" to topBooks
        )
        return GSON.toJson(result)
    }

    private fun getBookChapters(args: Map<*, *>): String {
        val bookName = args["book_name"] as? String
        if (bookName.isNullOrBlank()) {
            return """{"error":"book_name 参数不能为空"}"""
        }
        val book = appDb.bookDao.all.find { it.name == bookName }
            ?: return """{"error":"未找到书籍: $bookName"}"""
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        val result = chapters.take(MAX_CHAPTERS).map { ch ->
            mapOf("index" to (ch.index + 1), "title" to ch.title)
        }
        return GSON.toJson(result)
    }

    private fun getBookGroups(): String {
        val groups = appDb.bookGroupDao.all
        val result = groups.map { g ->
            mapOf("id" to g.groupId, "name" to g.groupName)
        }
        return GSON.toJson(result)
    }

    private fun getSourceGroups(): String {
        val groups = appDb.bookSourceDao.allGroups()
        return GSON.toJson(groups)
    }

    private fun bookToSimple(book: Book): Map<String, Any?> {
        val groupNames = try {
            appDb.bookGroupDao.getGroupNames(book.group)
        } catch (_: Exception) {
            emptyList()
        }
        return mapOf(
            "name" to book.name,
            "author" to book.author,
            "group" to groupNames,
            "durChapter" to (book.durChapterTitle ?: ""),
            "latestChapter" to (book.latestChapterTitle ?: ""),
            "canUpdate" to book.canUpdate,
            "lastReadTime" to book.durChapterTime
        )
    }
}
