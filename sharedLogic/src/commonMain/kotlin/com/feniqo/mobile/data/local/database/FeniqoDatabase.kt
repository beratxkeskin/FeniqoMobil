package com.feniqo.mobile.data.local.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.feniqo.mobile.data.local.dao.BudgetDao
import com.feniqo.mobile.data.local.dao.CategoryDao
import com.feniqo.mobile.data.local.dao.ProfileDao
import com.feniqo.mobile.data.local.dao.LocalMutationDao
import com.feniqo.mobile.data.local.dao.SyncOperationDao
import com.feniqo.mobile.data.local.dao.TagDao
import com.feniqo.mobile.data.local.dao.TransactionDao
import com.feniqo.mobile.data.local.dao.WorkspaceDao
import com.feniqo.mobile.data.local.entity.BudgetEntity
import com.feniqo.mobile.data.local.entity.CategoryEntity
import com.feniqo.mobile.data.local.entity.TagEntity
import com.feniqo.mobile.data.local.entity.SyncOperationEntity
import com.feniqo.mobile.data.local.entity.TransactionEntity
import com.feniqo.mobile.data.local.entity.TransactionTagCrossRef
import com.feniqo.mobile.data.local.entity.UserProfileEntity
import com.feniqo.mobile.data.local.entity.WorkspaceEntity
import com.feniqo.mobile.data.local.entity.WorkspaceMemberEntity

@Database(
    entities = [
        UserProfileEntity::class,
        WorkspaceEntity::class,
        WorkspaceMemberEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        TagEntity::class,
        TransactionTagCrossRef::class,
        SyncOperationEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(FeniqoDatabaseConstructor::class)
abstract class FeniqoDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun tagDao(): TagDao
    abstract fun syncOperationDao(): SyncOperationDao
    abstract fun localMutationDao(): LocalMutationDao
}

/** Room KSP her hedef için actual veritabanı kurucusunu üretir. */
@Suppress("KotlinNoActualForExpect")
expect object FeniqoDatabaseConstructor : RoomDatabaseConstructor<FeniqoDatabase> {
    override fun initialize(): FeniqoDatabase
}
