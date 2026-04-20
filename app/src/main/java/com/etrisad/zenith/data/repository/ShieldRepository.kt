package com.etrisad.zenith.data.repository

import com.etrisad.zenith.data.local.dao.ScheduleDao
import com.etrisad.zenith.data.local.dao.ShieldDao
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.ShieldEntity
import kotlinx.coroutines.flow.Flow

class ShieldRepository(
    private val shieldDao: ShieldDao,
    private val scheduleDao: ScheduleDao
) {
    val allShields: Flow<List<ShieldEntity>> = shieldDao.getAllShields()
    val allSchedules: Flow<List<ScheduleEntity>> = scheduleDao.getAllSchedules()

    suspend fun getShieldByPackageName(packageName: String): ShieldEntity? {
        return shieldDao.getShieldByPackageName(packageName)
    }

    suspend fun insertShield(shield: ShieldEntity) {
        shieldDao.insertShield(shield)
    }

    suspend fun updateShield(shield: ShieldEntity) {
        shieldDao.updateShield(shield)
    }

    suspend fun deleteShield(shield: ShieldEntity) {
        shieldDao.deleteShield(shield)
    }

    fun isAppShielded(packageName: String): Flow<Boolean> {
        return shieldDao.isAppShielded(packageName)
    }

    // Schedule methods
    suspend fun insertSchedule(schedule: ScheduleEntity) {
        scheduleDao.insertSchedule(schedule)
    }

    suspend fun updateSchedule(schedule: ScheduleEntity) {
        scheduleDao.updateSchedule(schedule)
    }

    suspend fun deleteSchedule(schedule: ScheduleEntity) {
        scheduleDao.deleteSchedule(schedule)
    }

    suspend fun getActiveSchedules(): List<ScheduleEntity> {
        return scheduleDao.getActiveSchedules()
    }
}
