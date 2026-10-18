package com.fongmi.android.tv.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.fongmi.android.tv.bean.Config
import com.fongmi.android.tv.bean.Device
import com.fongmi.android.tv.bean.History
import com.fongmi.android.tv.bean.Keep
import com.fongmi.android.tv.bean.Live
import com.fongmi.android.tv.bean.Site
import com.fongmi.android.tv.bean.Track
import com.fongmi.android.tv.db.dao.ConfigDao
import com.fongmi.android.tv.db.dao.DeviceDao
import com.fongmi.android.tv.db.dao.HistoryDao
import com.fongmi.android.tv.db.dao.KeepDao
import com.fongmi.android.tv.db.dao.LiveDao
import com.fongmi.android.tv.db.dao.SiteDao
import com.fongmi.android.tv.db.dao.TrackDao
import com.fongmi.desktop.compat.DesktopPaths

/**
 * 桌面端数据库实现（替代 Room 代码生成）：
 * - 实体与 DAO 直接复用安卓端挂载源码（db/dao 包），此处以 androidx.sqlite bundled 驱动手写 SQL 实现
 * - 表结构与安卓端 Room schema 对齐（表名/列名/主键/唯一索引一致），便于后续数据迁移
 * - 全部访问经同一连接串行化（桌面端单进程使用，与安卓 allowMainThreadQueries 语义等价）
 */
class AppDatabase private constructor(private val db: SQLiteConnection) {

    companion object {
        const val VERSION: Int = 35
        const val NAME: String = "tv"
        const val SYMBOL: String = "@@@"

        private const val TABLES = "Keep,Site,Live,Track,Config,Device,History"

        @Volatile
        private var instance: AppDatabase? = null

        @JvmStatic
        @Synchronized
        fun get(): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: create().also { instance = it }
            }
        }

        private fun create(): AppDatabase {
            val file = DesktopPaths.databaseFile()
            val connection = BundledSQLiteDriver().open(file.absolutePath)
            return AppDatabase(connection).apply { initTables() }
        }
    }

    private val keepDao = KeepDaoImpl()
    private val siteDao = SiteDaoImpl()
    private val liveDao = LiveDaoImpl()
    private val trackDao = TrackDaoImpl()
    private val configDao = ConfigDaoImpl()
    private val deviceDao = DeviceDaoImpl()
    private val historyDao = HistoryDaoImpl()

    fun getKeepDao(): KeepDao = keepDao

    fun getSiteDao(): SiteDao = siteDao

    fun getLiveDao(): LiveDao = liveDao

    fun getTrackDao(): TrackDao = trackDao

    fun getConfigDao(): ConfigDao = configDao

    fun getDeviceDao(): DeviceDao = deviceDao

    fun getHistoryDao(): HistoryDao = historyDao

    @Synchronized
    fun clearAllTables() {
        TABLES.split(",").forEach { exec("DELETE FROM `$it`") }
    }

    @Synchronized
    fun close() {
        runCatching { db.close() }
    }

    private fun initTables() {
        exec("CREATE TABLE IF NOT EXISTS `Keep` (`key` TEXT NOT NULL, `siteName` TEXT, `vodName` TEXT, `vodPic` TEXT, `createTime` INTEGER NOT NULL, `type` INTEGER NOT NULL, `cid` INTEGER NOT NULL, PRIMARY KEY(`key`))")
        exec("CREATE TABLE IF NOT EXISTS `Site` (`key` TEXT NOT NULL, `searchable` INTEGER, `changeable` INTEGER, PRIMARY KEY(`key`))")
        exec("CREATE TABLE IF NOT EXISTS `Live` (`name` TEXT NOT NULL, `keep` TEXT, `boot` INTEGER NOT NULL, `pass` INTEGER NOT NULL, PRIMARY KEY(`name`))")
        exec("CREATE TABLE IF NOT EXISTS `Track` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `type` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `format` TEXT, `selected` INTEGER NOT NULL)")
        exec("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_type` ON `Track` (`key`, `type`)")
        exec("CREATE TABLE IF NOT EXISTS `Config` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `type` INTEGER NOT NULL, `time` INTEGER NOT NULL, `url` TEXT, `json` TEXT, `name` TEXT, `logo` TEXT, `home` TEXT, `parse` TEXT)")
        exec("CREATE UNIQUE INDEX IF NOT EXISTS `index_Config_url_type` ON `Config` (`url`, `type`)")
        exec("CREATE TABLE IF NOT EXISTS `Device` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `uuid` TEXT, `name` TEXT, `ip` TEXT, `type` INTEGER NOT NULL)")
        exec("CREATE UNIQUE INDEX IF NOT EXISTS `index_Device_uuid_name` ON `Device` (`uuid`, `name`)")
        exec("CREATE TABLE IF NOT EXISTS `History` (`key` TEXT NOT NULL, `vodPic` TEXT, `vodName` TEXT, `vodFlag` TEXT, `vodRemarks` TEXT, `episodeUrl` TEXT, `revSort` INTEGER NOT NULL, `revPlay` INTEGER NOT NULL, `createTime` INTEGER NOT NULL, `opening` INTEGER NOT NULL, `ending` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `speed` REAL NOT NULL, `scale` INTEGER NOT NULL, `cid` INTEGER NOT NULL, PRIMARY KEY(`key`))")
    }

    private fun exec(sql: String) {
        db.prepare(sql).use { it.step() }
    }

    private fun insertWithIgnore(sql: String, bind: (SQLiteStatement) -> Unit): Long {
        db.prepare(sql).use { statement ->
            bind(statement)
            statement.step()
        }
        db.prepare("SELECT changes()").use { statement ->
            if (statement.step() && statement.getLong(0) != 0L) return lastRowId()
        }
        return -1L
    }

    private fun lastRowId(): Long {
        db.prepare("SELECT last_insert_rowid()").use { statement ->
            return if (statement.step()) statement.getLong(0) else -1L
        }
    }

    private fun <T> query(sql: String, bind: ((SQLiteStatement) -> Unit)?, map: (SQLiteStatement) -> T): MutableList<T> {
        val result = ArrayList<T>()
        db.prepare(sql).use { statement ->
            bind?.invoke(statement)
            while (statement.step()) result.add(map(statement))
        }
        return result
    }

    private fun <T> queryOne(sql: String, bind: ((SQLiteStatement) -> Unit)?, map: (SQLiteStatement) -> T): T? {
        db.prepare(sql).use { statement ->
            bind?.invoke(statement)
            return if (statement.step()) map(statement) else null
        }
    }

    private fun text(stmt: SQLiteStatement, index: Int): String? = if (stmt.isNull(index)) null else stmt.getText(index)

    private fun bindTextOrNull(stmt: SQLiteStatement, index: Int, value: String?) {
        if (value == null) stmt.bindNull(index) else stmt.bindText(index, value)
    }

    private fun bindIntOrNull(stmt: SQLiteStatement, index: Int, value: Int?) {
        if (value == null) stmt.bindNull(index) else stmt.bindLong(index, value.toLong())
    }

    // ==================== Keep ====================

    private inner class KeepDaoImpl : KeepDao() {

        override fun insert(item: Keep?): Long = insertWithIgnore(
            "INSERT OR IGNORE INTO `Keep` (`key`,`siteName`,`vodName`,`vodPic`,`createTime`,`type`,`cid`) VALUES (?,?,?,?,?,?,?)"
        ) { stmt ->
            stmt.bindText(1, item?.getKey() ?: "")
            bindTextOrNull(stmt, 2, item?.getSiteName())
            bindTextOrNull(stmt, 3, item?.getVodName())
            bindTextOrNull(stmt, 4, item?.getVodPic())
            stmt.bindLong(5, item?.getCreateTime() ?: 0L)
            stmt.bindLong(6, (item?.getType() ?: 0).toLong())
            stmt.bindLong(7, (item?.getCid() ?: 0).toLong())
        }

        override fun insert(items: MutableList<Keep>?): MutableList<Long> {
            val ids = ArrayList<Long>()
            items?.forEach { ids.add(insert(it)) }
            return ids
        }

        override fun update(item: Keep?) {
            db.prepare("UPDATE OR REPLACE `Keep` SET `siteName`=?,`vodName`=?,`vodPic`=?,`createTime`=?,`type`=?,`cid`=? WHERE `key`=?").use { stmt ->
                bindTextOrNull(stmt, 1, item?.getSiteName())
                bindTextOrNull(stmt, 2, item?.getVodName())
                bindTextOrNull(stmt, 3, item?.getVodPic())
                stmt.bindLong(4, item?.getCreateTime() ?: 0L)
                stmt.bindLong(5, (item?.getType() ?: 0).toLong())
                stmt.bindLong(6, (item?.getCid() ?: 0).toLong())
                stmt.bindText(7, item?.getKey() ?: "")
                stmt.step()
            }
        }

        override fun update(items: MutableList<Keep>?) {
            items?.forEach { update(it) }
        }

        override fun findAll(): MutableList<Keep> = query("SELECT * FROM `Keep`", null) { mapKeep(it) }

        override fun getVod(): MutableList<Keep> = query("SELECT * FROM `Keep` WHERE type = 0 ORDER BY createTime DESC", null) { mapKeep(it) }

        override fun getLive(): MutableList<Keep> = query("SELECT * FROM `Keep` WHERE type = 1 ORDER BY createTime DESC", null) { mapKeep(it) }

        override fun find(cid: Int, key: String?): Keep? = queryOne(
            "SELECT * FROM `Keep` WHERE type = 0 AND cid = ? AND `key` = ?",
            { stmt -> stmt.bindLong(1, cid.toLong()); stmt.bindText(2, key ?: "") },
            { mapKeep(it) }
        )

        override fun find(key: String?): Keep? = queryOne(
            "SELECT * FROM `Keep` WHERE type = 1 AND `key` = ?",
            { stmt -> stmt.bindText(1, key ?: "") },
            { mapKeep(it) }
        )

        override fun delete(key: String?) {
            db.prepare("DELETE FROM `Keep` WHERE type = 1 AND `key` = ?").use { stmt ->
                stmt.bindText(1, key ?: "")
                stmt.step()
            }
        }

        override fun delete(cid: Int, key: String?) {
            db.prepare("DELETE FROM `Keep` WHERE type = 0 AND cid = ? AND `key` = ?").use { stmt ->
                stmt.bindLong(1, cid.toLong())
                stmt.bindText(2, key ?: "")
                stmt.step()
            }
        }

        override fun delete(cid: Int) {
            db.prepare("DELETE FROM `Keep` WHERE type = 0 AND cid = ?").use { stmt ->
                stmt.bindLong(1, cid.toLong())
                stmt.step()
            }
        }

        override fun delete() {
            exec("DELETE FROM `Keep` WHERE type = 0")
        }

        private fun mapKeep(stmt: SQLiteStatement): Keep {
            val item = Keep()
            item.setKey(stmt.getText(0))
            item.setSiteName(text(stmt, 1))
            item.setVodName(text(stmt, 2))
            item.setVodPic(text(stmt, 3))
            item.setCreateTime(stmt.getLong(4))
            item.setType(stmt.getLong(5).toInt())
            item.setCid(stmt.getLong(6).toInt())
            return item
        }
    }

    // ==================== Site ====================

    private inner class SiteDaoImpl : SiteDao() {

        override fun insert(item: Site?): Long = insertWithIgnore(
            "INSERT OR IGNORE INTO `Site` (`key`,`searchable`,`changeable`) VALUES (?,?,?)"
        ) { stmt ->
            stmt.bindText(1, item?.getKey() ?: "")
            bindIntOrNull(stmt, 2, item?.getSearchable())
            bindIntOrNull(stmt, 3, item?.getChangeable())
        }

        override fun insert(items: MutableList<Site>?): MutableList<Long> {
            val ids = ArrayList<Long>()
            items?.forEach { ids.add(insert(it)) }
            return ids
        }

        override fun update(item: Site?) {
            db.prepare("UPDATE OR REPLACE `Site` SET `searchable`=?,`changeable`=? WHERE `key`=?").use { stmt ->
                bindIntOrNull(stmt, 1, item?.getSearchable())
                bindIntOrNull(stmt, 2, item?.getChangeable())
                stmt.bindText(3, item?.getKey() ?: "")
                stmt.step()
            }
        }

        override fun update(items: MutableList<Site>?) {
            items?.forEach { update(it) }
        }

        override fun findAll(): MutableList<Site> = query("SELECT * FROM `Site`", null) { mapSite(it) }

        private fun mapSite(stmt: SQLiteStatement): Site {
            val item = Site()
            item.setKey(stmt.getText(0))
            item.setSearchable(if (stmt.isNull(1)) null else stmt.getLong(1).toInt())
            item.setChangeable(if (stmt.isNull(2)) null else stmt.getLong(2).toInt())
            return item
        }
    }

    // ==================== Live ====================

    private inner class LiveDaoImpl : LiveDao() {

        override fun insert(item: Live?): Long = insertWithIgnore(
            "INSERT OR IGNORE INTO `Live` (`name`,`keep`,`boot`,`pass`) VALUES (?,?,?,?)"
        ) { stmt ->
            stmt.bindText(1, item?.getName() ?: "")
            bindTextOrNull(stmt, 2, item?.getKeep())
            stmt.bindLong(3, if (item?.isBoot() == true) 1L else 0L)
            stmt.bindLong(4, if (item?.isPass() == true) 1L else 0L)
        }

        override fun insert(items: MutableList<Live>?): MutableList<Long> {
            val ids = ArrayList<Long>()
            items?.forEach { ids.add(insert(it)) }
            return ids
        }

        override fun update(item: Live?) {
            db.prepare("UPDATE OR REPLACE `Live` SET `keep`=?,`boot`=?,`pass`=? WHERE `name`=?").use { stmt ->
                bindTextOrNull(stmt, 1, item?.getKeep())
                stmt.bindLong(2, if (item?.isBoot() == true) 1L else 0L)
                stmt.bindLong(3, if (item?.isPass() == true) 1L else 0L)
                stmt.bindText(4, item?.getName() ?: "")
                stmt.step()
            }
        }

        override fun update(items: MutableList<Live>?) {
            items?.forEach { update(it) }
        }

        override fun findAll(): MutableList<Live> = query("SELECT * FROM `Live`", null) { mapLive(it) }

        override fun find(name: String?): Live? = queryOne(
            "SELECT * FROM `Live` WHERE name = ?", { stmt -> stmt.bindText(1, name ?: "") }, { mapLive(it) }
        )

        private fun mapLive(stmt: SQLiteStatement): Live {
            val item = Live(stmt.getText(0), "")
            item.setKeep(text(stmt, 1))
            item.setBoot(stmt.getLong(2) != 0L)
            item.setPass(stmt.getLong(3) != 0L)
            return item
        }
    }

    // ==================== Track ====================

    private inner class TrackDaoImpl : TrackDao() {

        override fun insert(item: Track?): Long = insertWithIgnore(
            "INSERT OR REPLACE INTO `Track` (`id`,`type`,`key`,`name`,`format`,`selected`) VALUES (nullif(?, 0),?,?,?,?,?)"
        ) { stmt ->
            stmt.bindLong(1, (item?.getId() ?: 0).toLong())
            stmt.bindLong(2, (item?.getType() ?: 0).toLong())
            bindTextOrNull(stmt, 3, item?.getKey())
            bindTextOrNull(stmt, 4, item?.getName())
            bindTextOrNull(stmt, 5, item?.getFormat())
            stmt.bindLong(6, if (item?.isSelected() == true) 1L else 0L)
        }

        override fun insert(items: MutableList<Track>?): MutableList<Long> {
            val ids = ArrayList<Long>()
            items?.forEach { ids.add(insert(it)) }
            return ids
        }

        override fun update(item: Track?) {
            db.prepare("UPDATE OR REPLACE `Track` SET `type`=?,`key`=?,`name`=?,`format`=?,`selected`=? WHERE `id`=?").use { stmt ->
                stmt.bindLong(1, (item?.getType() ?: 0).toLong())
                bindTextOrNull(stmt, 2, item?.getKey())
                bindTextOrNull(stmt, 3, item?.getName())
                bindTextOrNull(stmt, 4, item?.getFormat())
                stmt.bindLong(5, if (item?.isSelected() == true) 1L else 0L)
                stmt.bindLong(6, (item?.getId() ?: 0).toLong())
                stmt.step()
            }
        }

        override fun update(items: MutableList<Track>?) {
            items?.forEach { update(it) }
        }

        override fun find(key: String?): MutableList<Track> = query(
            "SELECT * FROM `Track` WHERE `key` = ?", { stmt -> stmt.bindText(1, key ?: "") }, { mapTrack(it) }
        )

        override fun delete(key: String?) {
            db.prepare("DELETE FROM `Track` WHERE `key` = ?").use { stmt ->
                stmt.bindText(1, key ?: "")
                stmt.step()
            }
        }

        private fun mapTrack(stmt: SQLiteStatement): Track {
            val item = Track(stmt.getLong(1).toInt(), text(stmt, 3) ?: "", text(stmt, 4) ?: "")
            item.setId(stmt.getLong(0).toInt())
            item.setKey(text(stmt, 2))
            item.setSelected(stmt.getLong(5) != 0L)
            return item
        }
    }

    // ==================== Config ====================

    private inner class ConfigDaoImpl : ConfigDao() {

        override fun insert(item: Config?): Long = insertWithIgnore(
            "INSERT OR IGNORE INTO `Config` (`id`,`type`,`time`,`url`,`json`,`name`,`logo`,`home`,`parse`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"
        ) { stmt ->
            stmt.bindLong(1, (item?.getId() ?: 0).toLong())
            stmt.bindLong(2, (item?.getType() ?: 0).toLong())
            stmt.bindLong(3, item?.getTime() ?: 0L)
            bindTextOrNull(stmt, 4, item?.getUrl())
            bindTextOrNull(stmt, 5, item?.getJson())
            bindTextOrNull(stmt, 6, item?.getName())
            bindTextOrNull(stmt, 7, item?.getLogo())
            bindTextOrNull(stmt, 8, item?.getHome())
            bindTextOrNull(stmt, 9, item?.getParse())
        }

        override fun insert(items: MutableList<Config>?): MutableList<Long> {
            val ids = ArrayList<Long>()
            items?.forEach { ids.add(insert(it)) }
            return ids
        }

        override fun update(item: Config?) {
            db.prepare("UPDATE OR REPLACE `Config` SET `type`=?,`time`=?,`url`=?,`json`=?,`name`=?,`logo`=?,`home`=?,`parse`=? WHERE `id`=?").use { stmt ->
                stmt.bindLong(1, (item?.getType() ?: 0).toLong())
                stmt.bindLong(2, item?.getTime() ?: 0L)
                bindTextOrNull(stmt, 3, item?.getUrl())
                bindTextOrNull(stmt, 4, item?.getJson())
                bindTextOrNull(stmt, 5, item?.getName())
                bindTextOrNull(stmt, 6, item?.getLogo())
                bindTextOrNull(stmt, 7, item?.getHome())
                bindTextOrNull(stmt, 8, item?.getParse())
                stmt.bindLong(9, (item?.getId() ?: 0).toLong())
                stmt.step()
            }
        }

        override fun update(items: MutableList<Config>?) {
            items?.forEach { update(it) }
        }

        override fun findAll(): MutableList<Config> = query("SELECT * FROM `Config`", null) { mapConfig(it) }

        override fun findByType(type: Int): MutableList<Config> = query(
            "SELECT * FROM `Config` WHERE type = ? ORDER BY time DESC", { stmt -> stmt.bindLong(1, type.toLong()) }, { mapConfig(it) }
        )

        override fun findUrlByType(type: Int): MutableList<Config> = query(
            "SELECT id, name, url, type, time FROM `Config` WHERE type = ? ORDER BY time DESC", { stmt -> stmt.bindLong(1, type.toLong()) }, { mapConfig(it) }
        )

        override fun findById(id: Int): Config? = queryOne(
            "SELECT * FROM `Config` WHERE id = ?", { stmt -> stmt.bindLong(1, id.toLong()) }, { mapConfig(it) }
        )

        override fun findOne(type: Int): Config? = queryOne(
            "SELECT * FROM `Config` WHERE type = ? ORDER BY time DESC LIMIT 1", { stmt -> stmt.bindLong(1, type.toLong()) }, { mapConfig(it) }
        )

        override fun find(url: String?, type: Int): Config? = queryOne(
            "SELECT * FROM `Config` WHERE url = ? AND type = ?", { stmt -> stmt.bindText(1, url ?: ""); stmt.bindLong(2, type.toLong()) }, { mapConfig(it) }
        )

        override fun delete(url: String?, type: Int) {
            db.prepare("DELETE FROM `Config` WHERE url = ? AND type = ?").use { stmt ->
                stmt.bindText(1, url ?: "")
                stmt.bindLong(2, type.toLong())
                stmt.step()
            }
        }

        override fun delete(url: String?) {
            db.prepare("DELETE FROM `Config` WHERE url = ?").use { stmt ->
                stmt.bindText(1, url ?: "")
                stmt.step()
            }
        }

        private fun mapConfig(stmt: SQLiteStatement): Config {
            val item = Config()
            item.setId(stmt.getLong(0).toInt())
            item.setType(stmt.getLong(1).toInt())
            item.setTime(stmt.getLong(2))
            item.setUrl(text(stmt, 3))
            item.setJson(text(stmt, 4))
            item.setName(text(stmt, 5))
            item.setLogo(text(stmt, 6))
            item.setHome(text(stmt, 7))
            item.setParse(text(stmt, 8))
            return item
        }
    }

    // ==================== Device ====================

    private inner class DeviceDaoImpl : DeviceDao() {

        override fun insert(item: Device?): Long = insertWithIgnore(
            "INSERT OR IGNORE INTO `Device` (`id`,`uuid`,`name`,`ip`,`type`) VALUES (nullif(?, 0),?,?,?,?)"
        ) { stmt ->
            stmt.bindLong(1, (item?.getId() ?: 0).toLong())
            bindTextOrNull(stmt, 2, item?.getUuid())
            bindTextOrNull(stmt, 3, item?.getName())
            bindTextOrNull(stmt, 4, item?.getIp())
            stmt.bindLong(5, (item?.getType() ?: 0).toLong())
        }

        override fun insert(items: MutableList<Device>?): MutableList<Long> {
            val ids = ArrayList<Long>()
            items?.forEach { ids.add(insert(it)) }
            return ids
        }

        override fun update(item: Device?) {
            db.prepare("UPDATE OR REPLACE `Device` SET `uuid`=?,`name`=?,`ip`=?,`type`=? WHERE `id`=?").use { stmt ->
                bindTextOrNull(stmt, 1, item?.getUuid())
                bindTextOrNull(stmt, 2, item?.getName())
                bindTextOrNull(stmt, 3, item?.getIp())
                stmt.bindLong(4, (item?.getType() ?: 0).toLong())
                stmt.bindLong(5, (item?.getId() ?: 0).toLong())
                stmt.step()
            }
        }

        override fun update(items: MutableList<Device>?) {
            items?.forEach { update(it) }
        }

        override fun findAll(): MutableList<Device> = query("SELECT * FROM `Device`", null) { mapDevice(it) }

        override fun delete() {
            exec("DELETE FROM `Device`")
        }

        private fun mapDevice(stmt: SQLiteStatement): Device {
            val item = Device()
            if (!stmt.isNull(0)) item.setId(stmt.getLong(0).toInt())
            item.setUuid(text(stmt, 1))
            item.setName(text(stmt, 2))
            item.setIp(text(stmt, 3))
            item.setType(stmt.getLong(4).toInt())
            return item
        }
    }

    // ==================== History ====================

    private inner class HistoryDaoImpl : HistoryDao() {

        override fun insert(item: History?): Long = insertWithIgnore(
            "INSERT OR IGNORE INTO `History` (`key`,`vodPic`,`vodName`,`vodFlag`,`vodRemarks`,`episodeUrl`,`revSort`,`revPlay`,`createTime`,`opening`,`ending`,`position`,`duration`,`speed`,`scale`,`cid`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ) { stmt ->
            stmt.bindText(1, item?.getKey() ?: "")
            bindHistoryUpdate(stmt, item)
        }

        override fun insert(items: MutableList<History>?): MutableList<Long> {
            val ids = ArrayList<Long>()
            items?.forEach { ids.add(insert(it)) }
            return ids
        }

        override fun update(item: History?) {
            db.prepare("UPDATE OR REPLACE `History` SET `vodPic`=?,`vodName`=?,`vodFlag`=?,`vodRemarks`=?,`episodeUrl`=?,`revSort`=?,`revPlay`=?,`createTime`=?,`opening`=?,`ending`=?,`position`=?,`duration`=?,`speed`=?,`scale`=?,`cid`=? WHERE `key`=?").use { stmt ->
                bindHistoryUpdate(stmt, item)
                stmt.bindText(16, item?.getKey() ?: "")
                stmt.step()
            }
        }

        override fun update(items: MutableList<History>?) {
            items?.forEach { update(it) }
        }

        override fun findAll(): MutableList<History> = query("SELECT * FROM `History`", null) { mapHistory(it) }

        override fun find(cid: Int, createTime: Long): MutableList<History> = query(
            "SELECT * FROM `History` WHERE cid = ? AND createTime >= ? ORDER BY createTime DESC LIMIT 60",
            { stmt -> stmt.bindLong(1, cid.toLong()); stmt.bindLong(2, createTime) }, { mapHistory(it) }
        )

        override fun find(cid: Int, key: String?): History? = queryOne(
            "SELECT * FROM `History` WHERE cid = ? AND `key` = ?",
            { stmt -> stmt.bindLong(1, cid.toLong()); stmt.bindText(2, key ?: "") }, { mapHistory(it) }
        )

        override fun findByName(cid: Int, vodName: String?): MutableList<History> = query(
            "SELECT * FROM `History` WHERE cid = ? AND vodName = ? ORDER BY createTime DESC",
            { stmt -> stmt.bindLong(1, cid.toLong()); stmt.bindText(2, vodName ?: "") }, { mapHistory(it) }
        )

        override fun delete(cid: Int, key: String?) {
            db.prepare("DELETE FROM `History` WHERE cid = ? AND `key` = ?").use { stmt ->
                stmt.bindLong(1, cid.toLong())
                stmt.bindText(2, key ?: "")
                stmt.step()
            }
        }

        override fun delete(cid: Int) {
            db.prepare("DELETE FROM `History` WHERE cid = ?").use { stmt ->
                stmt.bindLong(1, cid.toLong())
                stmt.step()
            }
        }

        override fun delete() {
            exec("DELETE FROM `History`")
        }

        private fun bindHistoryUpdate(stmt: SQLiteStatement, item: History?) {
            bindTextOrNull(stmt, 1, item?.getVodPic())
            bindTextOrNull(stmt, 2, item?.getVodName())
            bindTextOrNull(stmt, 3, item?.getVodFlag())
            bindTextOrNull(stmt, 4, item?.getVodRemarks())
            bindTextOrNull(stmt, 5, item?.getEpisodeUrl())
            stmt.bindLong(6, if (item?.isRevSort() == true) 1L else 0L)
            stmt.bindLong(7, if (item?.isRevPlay() == true) 1L else 0L)
            stmt.bindLong(8, item?.getCreateTime() ?: 0L)
            stmt.bindLong(9, item?.getOpening() ?: 0L)
            stmt.bindLong(10, item?.getEnding() ?: 0L)
            stmt.bindLong(11, item?.getPosition() ?: 0L)
            stmt.bindLong(12, item?.getDuration() ?: 0L)
            stmt.bindDouble(13, (item?.getSpeed() ?: 0f).toDouble())
            stmt.bindLong(14, (item?.getScale() ?: 0).toLong())
            stmt.bindLong(15, (item?.getCid() ?: 0).toLong())
        }

        private fun mapHistory(stmt: SQLiteStatement): History {
            val item = History()
            item.setKey(stmt.getText(0))
            item.setVodPic(text(stmt, 1))
            item.setVodName(text(stmt, 2))
            item.setVodFlag(text(stmt, 3))
            item.setVodRemarks(text(stmt, 4))
            item.setEpisodeUrl(text(stmt, 5))
            item.setRevSort(stmt.getLong(6) != 0L)
            item.setRevPlay(stmt.getLong(7) != 0L)
            item.setCreateTime(stmt.getLong(8))
            item.setOpening(stmt.getLong(9))
            item.setEnding(stmt.getLong(10))
            item.setPosition(stmt.getLong(11))
            item.setDuration(stmt.getLong(12))
            item.setSpeed(stmt.getDouble(13).toFloat())
            item.setScale(stmt.getLong(14).toInt())
            item.setCid(stmt.getLong(15).toInt())
            return item
        }
    }
}
