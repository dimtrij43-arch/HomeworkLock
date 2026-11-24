package com.hwlock

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

// ---------- Room ----------
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val description: String,
    val links: String = "",            // ссылки, по одной в строке
    val done: Boolean = false,
    val date: String,                  // ISO yyyy-MM-dd
    val externalId: String? = null,    // id из МЭШ (дедупликация)
    val archived: Boolean = false,
    val archivedAt: Long = 0
)

@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey val pkg: String,
    val label: String,
    val limitMinutes: Int = 30         // 0 = «Без таймера»
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE archived=0 ORDER BY done, id") fun active(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE archived=1 ORDER BY archivedAt DESC, id DESC") fun archive(): Flow<List<TaskEntity>>
    @Query("SELECT COUNT(*) FROM tasks WHERE archived=0 AND done=0") suspend fun pending(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(t: TaskEntity): Long
    @Delete suspend fun delete(t: TaskEntity)
    @Query("UPDATE tasks SET done=:d WHERE id=:id") suspend fun setDone(id: Long, d: Boolean)
    @Query("UPDATE tasks SET archived=1, archivedAt=:now WHERE archived=0 AND date<:today") suspend fun archiveOld(today: String, now: Long)
    @Query("DELETE FROM tasks WHERE archived=1 AND archivedAt<:before") suspend fun purge(before: Long)
    @Query("SELECT COUNT(*) FROM tasks WHERE externalId=:e AND archived=0") suspend fun countExt(e: String): Int
}

@Dao
interface AppDao {
    @Query("SELECT * FROM apps ORDER BY label") fun all(): Flow<List<AppEntity>>
    @Query("SELECT * FROM apps WHERE pkg=:p") suspend fun get(p: String): AppEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(a: AppEntity)
    @Query("DELETE FROM apps WHERE pkg=:p") suspend fun remove(p: String)
    @Query("UPDATE apps SET limitMinutes=:m WHERE pkg=:p") suspend fun setLimit(p: String, m: Int)
}

@Database(entities = [TaskEntity::class, AppEntity::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun apps(): AppDao
}

// ---------- DataStore ----------
private val Context.ds by preferencesDataStore("settings")

data class Settings(
    val name: String = "", val email: String = "", val onboarded: Boolean = false,
    val weekend: Set<Int> = setOf(7),  // 1=Пн … 7=Вс
    val vacation: Boolean = false,
    val emergencyDay: String = "", val emergencyUntil: Long = 0, val meshToken: String = ""
)

class Prefs(private val ctx: Context) {
    private val NAME = stringPreferencesKey("name")
    private val EMAIL = stringPreferencesKey("email")
    private val ONB = booleanPreferencesKey("onboarded")
    private val WEEKEND = stringSetPreferencesKey("weekend")
    private val VAC = booleanPreferencesKey("vacation")
    private val ED = stringPreferencesKey("em_day")
    private val EU = longPreferencesKey("em_until")
    private val TOKEN = stringPreferencesKey("mesh_token")

    val flow: Flow<Settings> = ctx.ds.data.map { p ->
        Settings(
            p[NAME] ?: "", p[EMAIL] ?: "", p[ONB] ?: false,
            p[WEEKEND]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(7),
            p[VAC] ?: false, p[ED] ?: "", p[EU] ?: 0L, p[TOKEN] ?: ""
        )
    }

    suspend fun setProfile(n: String, e: String) = ctx.ds.edit { it[NAME] = n; it[EMAIL] = e }
    suspend fun setOnboarded() = ctx.ds.edit { it[ONB] = true }
    suspend fun setWeekend(s: Set<Int>) = ctx.ds.edit { it[WEEKEND] = s.map { d -> d.toString() }.toSet() }
    suspend fun setVacation(v: Boolean) = ctx.ds.edit { it[VAC] = v }
    suspend fun setToken(t: String) = ctx.ds.edit { it[TOKEN] = t }

    /** Экстренный доступ: 1 раз в сутки, 5 минут. */
    suspend fun useEmergency(): Boolean {
        var ok = false
        ctx.ds.edit {
            val today = LocalDate.now().toString()
            if (it[ED] != today) { it[ED] = today; it[EU] = System.currentTimeMillis() + 5 * 60_000L; ok = true }
        }
        return ok
    }
}
