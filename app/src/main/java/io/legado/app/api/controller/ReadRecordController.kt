package io.legado.app.api.controller

import io.legado.app.api.ReturnData
import io.legado.app.data.appDb

object ReadRecordController {

    /**
     * 获取所有书籍的阅读记录汇总（按书名分组，合并多设备时长）
     */
    fun getReadRecords(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val searchKey = parameters["searchKey"]?.firstOrNull()
        val records = if (searchKey.isNullOrBlank()) {
            appDb.readRecordDao.allShow
        } else {
            appDb.readRecordDao.search(searchKey)
        }
        return returnData.setData(records)
    }

    /**
     * 获取总阅读时长（毫秒），或指定书籍的阅读时长
     */
    fun getReadTime(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val bookName = parameters["bookName"]?.firstOrNull()
        val time = if (bookName.isNullOrBlank()) {
            appDb.readRecordDao.allTime
        } else {
            appDb.readRecordDao.getReadTime(bookName) ?: 0L
        }
        return returnData.setData(time)
    }

    /**
     * 获取详细阅读记录，可按书名筛选
     */
    fun getDetailedReadRecords(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val bookName = parameters["bookName"]?.firstOrNull()
        val records = if (bookName.isNullOrBlank()) {
            appDb.detailedReadRecordDao.all()
        } else {
            appDb.detailedReadRecordDao.getByBookName(bookName)
        }
        return returnData.setData(records)
    }

}
