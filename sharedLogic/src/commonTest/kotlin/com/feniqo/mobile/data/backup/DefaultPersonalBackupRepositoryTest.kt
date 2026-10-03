package com.feniqo.mobile.data.backup

import com.feniqo.mobile.domain.repository.PersonalBackupImportOutcome
import com.feniqo.mobile.domain.repository.PersonalBackupPreview
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultPersonalBackupRepositoryTest {
    @Test
    fun scopeExportAndImport_areMappedBehindDomainPort() = runTest {
        val repository = DefaultPersonalBackupRepository(
            exporter = object : PersonalBackupExporter {
                override suspend fun calculateScope() = BackupScope(2, 3)
                override suspend fun export() = "encoded"
            },
            importer = object : PersonalBackupImporter {
                override suspend fun import(raw: String) = BackupImportResult.Success(4, 5)
            },
        )

        assertEquals(2, repository.calculateScope().categoryCount)
        assertEquals(3, repository.calculateScope().transactionCount)
        assertEquals("encoded", repository.export())
        assertEquals(PersonalBackupImportOutcome.Success(4, 5), repository.import("raw"))
    }

    @Test
    fun preview_invalidPayload_exposesDomainFailureWithoutDataTypeLeak() {
        val repository = DefaultPersonalBackupRepository(
            exporter = object : PersonalBackupExporter {
                override suspend fun calculateScope() = BackupScope(0, 0)
                override suspend fun export() = ""
            },
            importer = object : PersonalBackupImporter {
                override suspend fun import(raw: String) = BackupImportResult.Failure("unused")
            },
        )

        val preview = assertIs<PersonalBackupPreview.Invalid>(repository.preview("not-json"))
        assertEquals("backup_invalid_json", preview.reason)
    }
}
