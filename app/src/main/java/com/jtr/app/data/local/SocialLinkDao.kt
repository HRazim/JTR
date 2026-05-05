package com.jtr.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jtr.app.domain.model.SocialLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SocialLinkDao {

    @Query("SELECT * FROM social_links WHERE personId = :personId ORDER BY rowid ASC")
    fun getForPerson(personId: String): Flow<List<SocialLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(link: SocialLinkEntity)

    @Query("DELETE FROM social_links WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM social_links WHERE personId = :personId")
    suspend fun deleteAllForPerson(personId: String)

    @Query("SELECT * FROM social_links")
    fun getAll(): Flow<List<SocialLinkEntity>>
}
