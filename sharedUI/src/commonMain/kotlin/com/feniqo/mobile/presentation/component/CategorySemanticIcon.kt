package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Semantik kategori anahtarlarını platformdan bağımsız Compose vektörlerine çözümler. */
object CategorySemanticIconResolver {
    fun resolve(key: String?): ImageVector = when (key?.trim()?.lowercase()) {
        "food_dining" -> Icons.Outlined.Restaurant
        "groceries" -> Icons.Outlined.ShoppingCart
        "housing" -> Icons.Outlined.Home
        "utilities" -> Icons.AutoMirrored.Outlined.ReceiptLong
        "transportation" -> Icons.Outlined.DirectionsCar
        "fuel" -> Icons.Outlined.LocalGasStation
        "healthcare" -> Icons.Outlined.Favorite
        "personal_care" -> Icons.Outlined.FaceRetouchingNatural
        "shopping" -> Icons.Outlined.ShoppingBag
        "entertainment" -> Icons.Outlined.ConfirmationNumber
        "subscriptions" -> Icons.Outlined.Autorenew
        "education" -> Icons.AutoMirrored.Outlined.MenuBook
        "travel" -> Icons.Outlined.Flight
        "family_pets" -> Icons.Outlined.Pets
        "financial_expenses" -> Icons.Outlined.Percent
        "taxes_fees" -> Icons.Outlined.AccountBalance
        "gifts_donations", "gift_income" -> Icons.Outlined.CardGiftcard
        "other_expense" -> Icons.Outlined.MoreHoriz
        "salary" -> Icons.Outlined.WorkOutline
        "freelance" -> Icons.Outlined.Laptop
        "business_income" -> Icons.Outlined.Storefront
        "investment_income" -> Icons.AutoMirrored.Outlined.TrendingUp
        "rental_income" -> Icons.Outlined.Key
        "refund_reimbursement" -> Icons.AutoMirrored.Outlined.Undo
        "scholarship_support" -> Icons.Outlined.School
        "other_income" -> Icons.Outlined.Add
        else -> Icons.Outlined.Category
    }
}

/** Kategori rengini yüzde 12 tonal zeminde, erişilebilir vektör ikonla sunar. */
@Composable
fun CategoryTonalIcon(
    iconKey: String?,
    color: Color,
    modifier: Modifier = Modifier,
    containerSize: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .background(color.copy(alpha = 0.12f), CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = CategorySemanticIconResolver.resolve(iconKey),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(containerSize * 0.55f),
        )
    }
}
