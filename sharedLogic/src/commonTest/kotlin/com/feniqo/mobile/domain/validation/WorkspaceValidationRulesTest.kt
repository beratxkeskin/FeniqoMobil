package com.feniqo.mobile.domain.validation

import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.WorkspaceInvitation
import com.feniqo.mobile.domain.model.WorkspaceRole
import com.feniqo.mobile.domain.model.WorkspaceType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceValidationRulesTest {

    private val userOwner = EntityId("user-owner")
    private val userEditor = EntityId("user-editor")
    private val userViewer = EntityId("user-viewer")
    private val userOther = EntityId("user-other")

    private val defaultMembers = listOf(
        userOwner to WorkspaceRole.OWNER,
        userEditor to WorkspaceRole.EDITOR,
        userViewer to WorkspaceRole.VIEWER,
    )

    // 1. Role Permission Matrix Tests
    @Test
    fun workspacePermissionPolicy_ownerHasAllPermissions() {
        WorkspacePermission.entries.forEach { permission ->
            assertTrue(
                WorkspacePermissionPolicy.can(WorkspaceRole.OWNER, permission),
                "OWNER should have permission: $permission"
            )
        }
    }

    @Test
    fun workspacePermissionPolicy_editorHasCorrectSubsetOfPermissions() {
        val allowedPermissions = setOf(
            WorkspacePermission.VIEW_WORKSPACE,
            WorkspacePermission.VIEW_MEMBERS,
            WorkspacePermission.VIEW_FINANCIAL_RECORDS,
            WorkspacePermission.CREATE_FINANCIAL_RECORD,
            WorkspacePermission.UPDATE_FINANCIAL_RECORD,
            WorkspacePermission.SOFT_DELETE_FINANCIAL_RECORD,
        )

        WorkspacePermission.entries.forEach { permission ->
            val expected = permission in allowedPermissions
            assertEquals(
                expected,
                WorkspacePermissionPolicy.can(WorkspaceRole.EDITOR, permission),
                "EDITOR permission check failed for: $permission"
            )
        }
    }

    @Test
    fun workspacePermissionPolicy_viewerHasOnlyViewPermissions() {
        val allowedPermissions = setOf(
            WorkspacePermission.VIEW_WORKSPACE,
            WorkspacePermission.VIEW_MEMBERS,
            WorkspacePermission.VIEW_FINANCIAL_RECORDS,
        )

        WorkspacePermission.entries.forEach { permission ->
            val expected = permission in allowedPermissions
            assertEquals(
                expected,
                WorkspacePermissionPolicy.can(WorkspaceRole.VIEWER, permission),
                "VIEWER permission check failed for: $permission"
            )
        }
    }

    // 2. Name & Description Validation Tests
    @Test
    fun validateName_validAndTrimmed() {
        val result = WorkspaceValidationRules.validateName("  Aile Bütçesi  ")
        assertTrue(result is WorkspaceValidationResult.Valid)
        assertEquals("Aile Bütçesi", result.value)
    }

    @Test
    fun validateName_blankOrWhitespace_returnsInvalid() {
        val emptyResult = WorkspaceValidationRules.validateName("")
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.NAME_BLANK), emptyResult)

        val whitespaceResult = WorkspaceValidationRules.validateName("   \t  \n  ")
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.NAME_BLANK), whitespaceResult)
    }

    @Test
    fun validateName_longNameWithoutArbitraryCap_succeeds() {
        val longName = "A".repeat(250)
        val result = WorkspaceValidationRules.validateName("  $longName  ")
        assertTrue(result is WorkspaceValidationResult.Valid)
        assertEquals(longName, result.value)
    }

    @Test
    fun validateDescription_blankNormalizedToNull_orTrimmed() {
        val nullDesc = WorkspaceValidationRules.validateDescription(null)
        assertTrue(nullDesc is WorkspaceValidationResult.Valid)
        assertNull(nullDesc.value)

        val blankDesc = WorkspaceValidationRules.validateDescription("   ")
        assertTrue(blankDesc is WorkspaceValidationResult.Valid)
        assertNull(blankDesc.value)

        val validDesc = WorkspaceValidationRules.validateDescription("  Ev harcamaları için ortak alan  ")
        assertTrue(validDesc is WorkspaceValidationResult.Valid)
        assertEquals("Ev harcamaları için ortak alan", validDesc.value)
    }

    @Test
    fun validateDescription_longDescriptionWithoutArbitraryCap_succeeds() {
        val longDesc = "A".repeat(1000)
        val result = WorkspaceValidationRules.validateDescription("  $longDesc  ")
        assertTrue(result is WorkspaceValidationResult.Valid)
        assertEquals(longDesc, result.value)
    }

    // 3. Create & Update Command Validation Tests
    @Test
    fun validateCreateWorkspace_validInputs_returnsCommand() {
        val result = WorkspaceValidationRules.validateCreateWorkspace(
            name = "  Ev Bütçesi  ",
            type = WorkspaceType.SHARED,
            currency = Currency.TRY,
            description = "  Açıklama  ",
        )
        assertTrue(result is WorkspaceValidationResult.Valid)
        assertEquals("Ev Bütçesi", result.value.name)
        assertEquals(WorkspaceType.SHARED, result.value.type)
        assertEquals(Currency.TRY, result.value.currency)
        assertEquals("Açıklama", result.value.description)
    }

    @Test
    fun validateUpdateWorkspace_actorNotMember_returnsActorNotMember() {
        val nonMemberUpdate = WorkspaceValidationRules.validateUpdateWorkspace(
            id = EntityId("ws-1"),
            actorUserId = userOther,
            name = "Yeni Ad",
            type = WorkspaceType.SHARED,
            currency = Currency.EUR,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER), nonMemberUpdate)
    }

    @Test
    fun validateUpdateWorkspace_actorNotPermitted_returnsInvalid() {
        val editorUpdate = WorkspaceValidationRules.validateUpdateWorkspace(
            id = EntityId("ws-1"),
            actorUserId = userEditor,
            name = "Yeni Ad",
            type = WorkspaceType.SHARED,
            currency = Currency.EUR,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_PERMITTED), editorUpdate)

        val viewerUpdate = WorkspaceValidationRules.validateUpdateWorkspace(
            id = EntityId("ws-1"),
            actorUserId = userViewer,
            name = "Yeni Ad",
            type = WorkspaceType.SHARED,
            currency = Currency.EUR,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_PERMITTED), viewerUpdate)
    }

    @Test
    fun validateUpdateWorkspace_ownerPermitted_returnsCommand() {
        val ownerUpdate = WorkspaceValidationRules.validateUpdateWorkspace(
            id = EntityId("ws-1"),
            actorUserId = userOwner,
            name = "  Yeni Ad  ",
            type = WorkspaceType.SHARED,
            currency = Currency.USD,
            description = "  Yeni açıklama  ",
            currentMembers = defaultMembers,
        )
        assertTrue(ownerUpdate is WorkspaceValidationResult.Valid)
        assertEquals("Yeni Ad", ownerUpdate.value.name)
        assertEquals(Currency.USD, ownerUpdate.value.currency)
        assertEquals("Yeni açıklama", ownerUpdate.value.description)
    }

    // 4. Invitation Validation & Invariant Tests
    @Test
    fun validateInvitationParams_validParams_returnsValid() {
        val createdAt = Instant.fromEpochMilliseconds(1000)
        val expiresAt = Instant.fromEpochMilliseconds(2000)

        val result = WorkspaceValidationRules.validateInvitationParams(
            actorUserId = userOwner,
            targetRole = WorkspaceRole.EDITOR,
            createdAt = createdAt,
            expiresAt = expiresAt,
            maxUses = 5,
            currentMembers = defaultMembers,
        )
        assertTrue(result is WorkspaceValidationResult.Valid)
    }

    @Test
    fun validateInvitationParams_actorNotMember_returnsActorNotMember() {
        val createdAt = Instant.fromEpochMilliseconds(1000)
        val expiresAt = Instant.fromEpochMilliseconds(2000)

        val nonMemberResult = WorkspaceValidationRules.validateInvitationParams(
            actorUserId = userOther,
            targetRole = WorkspaceRole.EDITOR,
            createdAt = createdAt,
            expiresAt = expiresAt,
            maxUses = 5,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER), nonMemberResult)
    }

    @Test
    fun validateInvitationParams_nonOwnerActor_returnsActorNotPermitted() {
        val createdAt = Instant.fromEpochMilliseconds(1000)
        val expiresAt = Instant.fromEpochMilliseconds(2000)

        val editorResult = WorkspaceValidationRules.validateInvitationParams(
            actorUserId = userEditor,
            targetRole = WorkspaceRole.EDITOR,
            createdAt = createdAt,
            expiresAt = expiresAt,
            maxUses = 5,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_PERMITTED), editorResult)

        val viewerResult = WorkspaceValidationRules.validateInvitationParams(
            actorUserId = userViewer,
            targetRole = WorkspaceRole.VIEWER,
            createdAt = createdAt,
            expiresAt = expiresAt,
            maxUses = 5,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_PERMITTED), viewerResult)
    }

    @Test
    fun validateInvitationParams_ownerTargetRole_returnsCannotBeOwner() {
        val createdAt = Instant.fromEpochMilliseconds(1000)
        val expiresAt = Instant.fromEpochMilliseconds(2000)

        val result = WorkspaceValidationRules.validateInvitationParams(
            actorUserId = userOwner,
            targetRole = WorkspaceRole.OWNER,
            createdAt = createdAt,
            expiresAt = expiresAt,
            maxUses = 5,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.INVITATION_ROLE_CANNOT_BE_OWNER), result)
    }

    @Test
    fun validateInvitationParams_nonPositiveMaxUses_returnsInvalid() {
        val createdAt = Instant.fromEpochMilliseconds(1000)
        val expiresAt = Instant.fromEpochMilliseconds(2000)

        val zeroUses = WorkspaceValidationRules.validateInvitationParams(
            actorUserId = userOwner,
            targetRole = WorkspaceRole.EDITOR,
            createdAt = createdAt,
            expiresAt = expiresAt,
            maxUses = 0,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.INVITATION_MAX_USES_NON_POSITIVE), zeroUses)

        val negativeUses = WorkspaceValidationRules.validateInvitationParams(
            actorUserId = userOwner,
            targetRole = WorkspaceRole.EDITOR,
            createdAt = createdAt,
            expiresAt = expiresAt,
            maxUses = -1,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.INVITATION_MAX_USES_NON_POSITIVE), negativeUses)
    }

    @Test
    fun validateInvitationParams_expiresAtBeforeOrEqualToCreatedAt_returnsInvalid() {
        val createdAt = Instant.fromEpochMilliseconds(2000)
        val expiresAtBefore = Instant.fromEpochMilliseconds(1000)
        val expiresAtEqual = Instant.fromEpochMilliseconds(2000)

        val beforeResult = WorkspaceValidationRules.validateInvitationParams(
            actorUserId = userOwner,
            targetRole = WorkspaceRole.EDITOR,
            createdAt = createdAt,
            expiresAt = expiresAtBefore,
            maxUses = 5,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.INVITATION_EXPIRES_AT_MUST_BE_AFTER_CREATED_AT), beforeResult)

        val equalResult = WorkspaceValidationRules.validateInvitationParams(
            actorUserId = userOwner,
            targetRole = WorkspaceRole.EDITOR,
            createdAt = createdAt,
            expiresAt = expiresAtEqual,
            maxUses = 5,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.INVITATION_EXPIRES_AT_MUST_BE_AFTER_CREATED_AT), equalResult)
    }

    @Test
    fun workspaceInvitationModel_invariantsEnforced() {
        val createdAt = Instant.fromEpochMilliseconds(1000)
        val expiresAt = Instant.fromEpochMilliseconds(2000)

        // Valid model
        val invitation = WorkspaceInvitation(
            id = EntityId("inv-1"),
            workspaceId = EntityId("ws-1"),
            inviterId = userOwner,
            role = WorkspaceRole.EDITOR,
            createdAt = createdAt,
            expiresAt = expiresAt,
            maxUses = 1,
            usesCount = 0,
        )
        assertEquals(WorkspaceRole.EDITOR, invitation.role)

        // OWNER role fails require
        assertFailsWith<IllegalArgumentException> {
            WorkspaceInvitation(
                id = EntityId("inv-2"),
                workspaceId = EntityId("ws-1"),
                inviterId = userOwner,
                role = WorkspaceRole.OWNER,
                createdAt = createdAt,
                expiresAt = expiresAt,
                maxUses = 1,
                usesCount = 0,
            )
        }

        // maxUses <= 0 fails require
        assertFailsWith<IllegalArgumentException> {
            WorkspaceInvitation(
                id = EntityId("inv-3"),
                workspaceId = EntityId("ws-1"),
                inviterId = userOwner,
                role = WorkspaceRole.VIEWER,
                createdAt = createdAt,
                expiresAt = expiresAt,
                maxUses = 0,
                usesCount = 0,
            )
        }

        // expiresAt <= createdAt fails require
        assertFailsWith<IllegalArgumentException> {
            WorkspaceInvitation(
                id = EntityId("inv-4"),
                workspaceId = EntityId("ws-1"),
                inviterId = userOwner,
                role = WorkspaceRole.VIEWER,
                createdAt = createdAt,
                expiresAt = createdAt,
                maxUses = 1,
                usesCount = 0,
            )
        }

        // usesCount > maxUses fails require
        assertFailsWith<IllegalArgumentException> {
            WorkspaceInvitation(
                id = EntityId("inv-5"),
                workspaceId = EntityId("ws-1"),
                inviterId = userOwner,
                role = WorkspaceRole.EDITOR,
                createdAt = createdAt,
                expiresAt = expiresAt,
                maxUses = 1,
                usesCount = 2,
            )
        }
    }

    // 5. Member Management, Self-Elevation & Last Owner Invariants
    @Test
    fun validateMemberRoleChange_actorNotMember_rejected() {
        val nonMemberChange = WorkspaceValidationRules.validateMemberRoleChange(
            actorUserId = userOther,
            targetUserId = userViewer,
            newRole = WorkspaceRole.EDITOR,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER), nonMemberChange)
    }

    @Test
    fun validateMemberRoleChange_editorOrViewerActor_rejected() {
        val editorChange = WorkspaceValidationRules.validateMemberRoleChange(
            actorUserId = userEditor,
            targetUserId = userViewer,
            newRole = WorkspaceRole.EDITOR,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_PERMITTED), editorChange)
    }

    @Test
    fun validateMemberRoleChange_selfElevationOrSelfRoleChange_rejected() {
        val selfChange = WorkspaceValidationRules.validateMemberRoleChange(
            actorUserId = userOwner,
            targetUserId = userOwner,
            newRole = WorkspaceRole.EDITOR,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_CHANGE_OWN_ROLE), selfChange)
    }

    @Test
    fun validateMemberRoleChange_assigningOwnerRole_rejectedWithTransferRequired() {
        val ownerAssign = WorkspaceValidationRules.validateMemberRoleChange(
            actorUserId = userOwner,
            targetUserId = userEditor,
            newRole = WorkspaceRole.OWNER,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.OWNER_ROLE_CHANGE_REQUIRES_TRANSFER), ownerAssign)
    }

    @Test
    fun validateMemberRoleChange_targetNotMember_rejected() {
        val notMember = WorkspaceValidationRules.validateMemberRoleChange(
            actorUserId = userOwner,
            targetUserId = userOther,
            newRole = WorkspaceRole.EDITOR,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.TARGET_MEMBER_NOT_FOUND), notMember)
    }

    @Test
    fun validateMemberRoleChange_downgradingOnlyOwner_rejected() {
        val owner2 = EntityId("user-owner-2")
        val membersWithOneOwner = listOf(
            userOwner to WorkspaceRole.OWNER,
            userEditor to WorkspaceRole.EDITOR,
            owner2 to WorkspaceRole.EDITOR,
        )
        val membersWithTwoOwners = listOf(
            userOwner to WorkspaceRole.OWNER,
            owner2 to WorkspaceRole.OWNER,
            userEditor to WorkspaceRole.EDITOR,
        )

        // Tek owner var ve o düşürülmeye çalışılıyor -> CANNOT_REMOVE_LAST_OWNER
        val downgradeSingleOwner = WorkspaceValidationRules.validateMemberRoleChange(
            actorUserId = userOwner,
            targetUserId = userOwner, // kendini değiştirmeme önceliklidir ama hedef başka bir tek owner olsaydı:
            newRole = WorkspaceRole.EDITOR,
            currentMembers = membersWithOneOwner,
        )
        // Self change önce tetiklenir
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_CHANGE_OWN_ROLE), downgradeSingleOwner)

        // İki owner varken biri diğerini EDITOR yapıyor
        val downgradeWithTwoOwners = WorkspaceValidationRules.validateMemberRoleChange(
            actorUserId = owner2,
            targetUserId = userOwner,
            newRole = WorkspaceRole.EDITOR,
            currentMembers = membersWithTwoOwners,
        )
        assertTrue(downgradeWithTwoOwners is WorkspaceValidationResult.Valid)
    }

    @Test
    fun validateMemberRoleChange_validRoleChange_succeeds() {
        val validChange = WorkspaceValidationRules.validateMemberRoleChange(
            actorUserId = userOwner,
            targetUserId = userViewer,
            newRole = WorkspaceRole.EDITOR,
            currentMembers = defaultMembers,
        )
        assertTrue(validChange is WorkspaceValidationResult.Valid)
    }

    @Test
    fun validateMemberRemoval_actorNotMember_rejected() {
        val nonMemberRemove = WorkspaceValidationRules.validateMemberRemoval(
            actorUserId = userOther,
            targetUserId = userViewer,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER), nonMemberRemove)
    }

    @Test
    fun validateMemberRemoval_nonOwnerActor_rejected() {
        val editorRemove = WorkspaceValidationRules.validateMemberRemoval(
            actorUserId = userEditor,
            targetUserId = userViewer,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_PERMITTED), editorRemove)
    }

    @Test
    fun validateMemberRemoval_targetNotMember_rejected() {
        val notMember = WorkspaceValidationRules.validateMemberRemoval(
            actorUserId = userOwner,
            targetUserId = userOther,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.TARGET_MEMBER_NOT_FOUND), notMember)
    }

    @Test
    fun validateMemberRemoval_removingOwner_rejected() {
        val owner2 = EntityId("user-owner-2")
        val membersWithTwoOwners = listOf(
            userOwner to WorkspaceRole.OWNER,
            owner2 to WorkspaceRole.OWNER,
        )
        val removeOwner = WorkspaceValidationRules.validateMemberRemoval(
            actorUserId = owner2,
            targetUserId = userOwner,
            currentMembers = membersWithTwoOwners,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_REMOVE_WORKSPACE_OWNER), removeOwner)
    }

    @Test
    fun validateMemberRemoval_removingSelf_rejected() {
        val singleOwnerMembers = listOf(
            userOwner to WorkspaceRole.OWNER,
            userEditor to WorkspaceRole.EDITOR,
        )
        val removeSelf = WorkspaceValidationRules.validateMemberRemoval(
            actorUserId = userOwner,
            targetUserId = userOwner,
            currentMembers = singleOwnerMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_REMOVE_SELF_MEMBER), removeSelf)
    }

    @Test
    fun validateMemberRemoval_validRemoval_succeeds() {
        val validRemoval = WorkspaceValidationRules.validateMemberRemoval(
            actorUserId = userOwner,
            targetUserId = userEditor,
            currentMembers = defaultMembers,
        )
        assertTrue(validRemoval is WorkspaceValidationResult.Valid)
    }

    @Test
    fun validateLeaveWorkspace_actorNotMember_rejected() {
        val result = WorkspaceValidationRules.validateLeaveWorkspace(
            userId = userOther,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER), result)
    }

    @Test
    fun validateLeaveWorkspace_lastOwner_rejected() {
        val result = WorkspaceValidationRules.validateLeaveWorkspace(
            userId = userOwner,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_LEAVE_AS_LAST_OWNER), result)
    }

    @Test
    fun validateLeaveWorkspace_ownerWithMultipleOwners_succeeds() {
        val owner2 = EntityId("user-owner-2")
        val membersWithTwoOwners = listOf(
            userOwner to WorkspaceRole.OWNER,
            owner2 to WorkspaceRole.OWNER,
        )
        val result = WorkspaceValidationRules.validateLeaveWorkspace(
            userId = userOwner,
            currentMembers = membersWithTwoOwners,
        )
        assertTrue(result is WorkspaceValidationResult.Valid)
    }

    @Test
    fun validateLeaveWorkspace_editorOrViewer_succeeds() {
        val editorLeave = WorkspaceValidationRules.validateLeaveWorkspace(
            userId = userEditor,
            currentMembers = defaultMembers,
        )
        assertTrue(editorLeave is WorkspaceValidationResult.Valid)

        val viewerLeave = WorkspaceValidationRules.validateLeaveWorkspace(
            userId = userViewer,
            currentMembers = defaultMembers,
        )
        assertTrue(viewerLeave is WorkspaceValidationResult.Valid)
    }

    // 6. Ownership Transfer Tests
    @Test
    fun validateOwnershipTransfer_actorNotMember_rejected() {
        val nonMemberTransfer = WorkspaceValidationRules.validateOwnershipTransfer(
            actorUserId = userOther,
            targetUserId = userViewer,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.ACTOR_NOT_MEMBER), nonMemberTransfer)
    }

    @Test
    fun validateOwnershipTransfer_nonOwnerActor_rejected() {
        val editorTransfer = WorkspaceValidationRules.validateOwnershipTransfer(
            actorUserId = userEditor,
            targetUserId = userViewer,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.TRANSFER_ACTOR_NOT_OWNER), editorTransfer)
    }

    @Test
    fun validateOwnershipTransfer_selfTransfer_rejected() {
        val selfTransfer = WorkspaceValidationRules.validateOwnershipTransfer(
            actorUserId = userOwner,
            targetUserId = userOwner,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.CANNOT_CHANGE_OWN_ROLE), selfTransfer)
    }

    @Test
    fun validateOwnershipTransfer_targetNotMember_rejected() {
        val nonMemberTransfer = WorkspaceValidationRules.validateOwnershipTransfer(
            actorUserId = userOwner,
            targetUserId = userOther,
            currentMembers = defaultMembers,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.TARGET_MEMBER_NOT_FOUND), nonMemberTransfer)
    }

    @Test
    fun validateOwnershipTransfer_targetAlreadyOwner_rejected() {
        val owner2 = EntityId("user-owner-2")
        val membersWithTwoOwners = listOf(
            userOwner to WorkspaceRole.OWNER,
            owner2 to WorkspaceRole.OWNER,
        )
        val transferToOwner = WorkspaceValidationRules.validateOwnershipTransfer(
            actorUserId = userOwner,
            targetUserId = owner2,
            currentMembers = membersWithTwoOwners,
        )
        assertEquals(WorkspaceValidationResult.Invalid(WorkspaceValidationError.TRANSFER_TARGET_ALREADY_OWNER), transferToOwner)
    }

    @Test
    fun validateOwnershipTransfer_validTransferToExistingMember_succeeds() {
        val validTransferToEditor = WorkspaceValidationRules.validateOwnershipTransfer(
            actorUserId = userOwner,
            targetUserId = userEditor,
            currentMembers = defaultMembers,
        )
        assertTrue(validTransferToEditor is WorkspaceValidationResult.Valid)

        val validTransferToViewer = WorkspaceValidationRules.validateOwnershipTransfer(
            actorUserId = userOwner,
            targetUserId = userViewer,
            currentMembers = defaultMembers,
        )
        assertTrue(validTransferToViewer is WorkspaceValidationResult.Valid)
    }
}
