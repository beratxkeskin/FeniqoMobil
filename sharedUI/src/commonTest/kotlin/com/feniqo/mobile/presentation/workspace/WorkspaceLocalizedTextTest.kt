package com.feniqo.mobile.presentation.workspace

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.currency_eur
import feniqomobil.sharedui.generated.resources.currency_gbp
import feniqomobil.sharedui.generated.resources.currency_try
import feniqomobil.sharedui.generated.resources.currency_usd
import feniqomobil.sharedui.generated.resources.workspace_create_name_error_blank
import feniqomobil.sharedui.generated.resources.workspace_create_type_personal_desc
import feniqomobil.sharedui.generated.resources.workspace_create_type_shared_desc
import feniqomobil.sharedui.generated.resources.workspace_join_error_code_blank
import feniqomobil.sharedui.generated.resources.workspace_join_error_code_invalid
import feniqomobil.sharedui.generated.resources.workspace_role_editor
import feniqomobil.sharedui.generated.resources.workspace_role_editor_desc
import feniqomobil.sharedui.generated.resources.workspace_role_owner
import feniqomobil.sharedui.generated.resources.workspace_role_viewer
import feniqomobil.sharedui.generated.resources.workspace_role_viewer_desc
import feniqomobil.sharedui.generated.resources.workspace_settlement_status_creditor
import feniqomobil.sharedui.generated.resources.workspace_settlement_status_debtor
import feniqomobil.sharedui.generated.resources.workspace_settlement_status_settled
import feniqomobil.sharedui.generated.resources.workspace_type_personal
import feniqomobil.sharedui.generated.resources.workspace_type_shared
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorkspaceLocalizedTextTest {
    @Test
    fun workspaceCreateFormFieldError_mapsExhaustively() {
        WorkspaceCreateFormFieldError.entries.forEach { error ->
            assertNotNull(error.toLocalizedResource())
        }
        assertEquals(1, WorkspaceCreateFormFieldError.entries.size)
        assertEquals(Res.string.workspace_create_name_error_blank, WorkspaceCreateFormFieldError.NAME_BLANK.toLocalizedResource())
    }

    @Test
    fun workspaceJoinFormFieldError_mapsExhaustively() {
        WorkspaceJoinFormFieldError.entries.forEach { error ->
            assertNotNull(error.toLocalizedResource())
        }
        assertEquals(2, WorkspaceJoinFormFieldError.entries.size)
        assertEquals(Res.string.workspace_join_error_code_blank, WorkspaceJoinFormFieldError.CODE_BLANK.toLocalizedResource())
        assertEquals(Res.string.workspace_join_error_code_invalid, WorkspaceJoinFormFieldError.CODE_INVALID.toLocalizedResource())
    }

    @Test
    fun workspaceRole_mapsExhaustively() {
        WorkspaceRole.entries.forEach { role ->
            assertNotNull(role.toLocalizedResource())
        }
        assertEquals(3, WorkspaceRole.entries.size)
        assertEquals(Res.string.workspace_role_owner, WorkspaceRole.OWNER.toLocalizedResource())
        assertEquals(Res.string.workspace_role_editor, WorkspaceRole.EDITOR.toLocalizedResource())
        assertEquals(Res.string.workspace_role_viewer, WorkspaceRole.VIEWER.toLocalizedResource())

        assertNull(WorkspaceRole.OWNER.toLocalizedDescriptionResource())
        assertEquals(Res.string.workspace_role_editor_desc, WorkspaceRole.EDITOR.toLocalizedDescriptionResource())
        assertEquals(Res.string.workspace_role_viewer_desc, WorkspaceRole.VIEWER.toLocalizedDescriptionResource())
    }

    @Test
    fun workspaceType_mapsExhaustively() {
        WorkspaceType.entries.forEach { type ->
            assertNotNull(type.toLocalizedResource())
            assertNotNull(type.toLocalizedDescriptionResource())
        }
        assertEquals(2, WorkspaceType.entries.size)
        assertEquals(Res.string.workspace_type_shared, WorkspaceType.SHARED.toLocalizedResource())
        assertEquals(Res.string.workspace_type_personal, WorkspaceType.PERSONAL.toLocalizedResource())
        assertEquals(Res.string.workspace_create_type_shared_desc, WorkspaceType.SHARED.toLocalizedDescriptionResource())
        assertEquals(Res.string.workspace_create_type_personal_desc, WorkspaceType.PERSONAL.toLocalizedDescriptionResource())
    }

    @Test
    fun memberBalanceStatus_mapsExhaustively() {
        MemberBalanceStatus.entries.forEach { status ->
            assertNotNull(status.toLocalizedResource())
        }
        assertEquals(3, MemberBalanceStatus.entries.size)
        assertEquals(Res.string.workspace_settlement_status_creditor, MemberBalanceStatus.CREDITOR.toLocalizedResource())
        assertEquals(Res.string.workspace_settlement_status_debtor, MemberBalanceStatus.DEBTOR.toLocalizedResource())
        assertEquals(Res.string.workspace_settlement_status_settled, MemberBalanceStatus.SETTLED.toLocalizedResource())
    }

    @Test
    fun currency_mapsExhaustively() {
        Currency.entries.forEach { currency ->
            assertNotNull(currency.toLocalizedDisplayNameResource())
        }
        assertEquals(4, Currency.entries.size)
        assertEquals(Res.string.currency_try, Currency.TRY.toLocalizedDisplayNameResource())
        assertEquals(Res.string.currency_usd, Currency.USD.toLocalizedDisplayNameResource())
        assertEquals(Res.string.currency_eur, Currency.EUR.toLocalizedDisplayNameResource())
        assertEquals(Res.string.currency_gbp, Currency.GBP.toLocalizedDisplayNameResource())
    }

    @Test
    fun resolveWorkspaceMemberInitial_returnsUppercaseFirstOrFallback() {
        assertEquals("A", resolveWorkspaceMemberInitial("Ahmet"))
        assertEquals("S", resolveWorkspaceMemberInitial("Siz"))
        assertEquals("K", resolveWorkspaceMemberInitial("kullanıcı"))
        assertEquals("U", resolveWorkspaceMemberInitial(""))
    }
}
