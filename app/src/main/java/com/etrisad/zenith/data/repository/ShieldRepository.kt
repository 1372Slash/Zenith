package com.etrisad.zenith.data.repository

import com.etrisad.zenith.data.local.dao.ShieldDao
import com.etrisad.zenith.data.local.entity.ShieldEntity
import kotlinx.coroutines.flow.Flow

class ShieldRepository(private val shieldDao: ShieldDao) {
    val allShields: Flow<List<ShieldEntity>> = shieldDao.getAllShields()

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
}
