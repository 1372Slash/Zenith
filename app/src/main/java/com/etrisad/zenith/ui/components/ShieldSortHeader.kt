package com.etrisad.zenith.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.etrisad.zenith.ui.viewmodel.ShieldSortType

@Composable
fun ShieldSortHeader(
    title: String,
    currentSortType: ShieldSortType,
    onSortTypeChange: (ShieldSortType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SortIconButton(
                selected = currentSortType == ShieldSortType.ALPHABETICAL,
                onClick = { onSortTypeChange(ShieldSortType.ALPHABETICAL) },
                selectedIcon = Icons.Filled.SortByAlpha,
                unselectedIcon = Icons.Outlined.SortByAlpha,
                contentDescription = "Alphabetical",
                isFirst = true,
                isLast = false
            )

            SortIconButton(
                selected = currentSortType == ShieldSortType.REMAINING_TIME,
                onClick = { onSortTypeChange(ShieldSortType.REMAINING_TIME) },
                selectedIcon = Icons.Filled.Timer,
                unselectedIcon = Icons.Outlined.Timer,
                contentDescription = "Remaining Time",
                isFirst = false,
                isLast = true
            )
        }
    }
}

@Composable
private fun SortIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    contentDescription: String,
    isFirst: Boolean,
    isLast: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Expressive Motion Scaling with more bounce
    val widthScale by animateFloatAsState(
        targetValue = when {
            isPressed -> 1.5f  // More pronounced expansion
            selected -> 1.25f  // Clearly longer when active
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, // More expressive bounce
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "WidthScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer 
                      else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "BgColor"
    )
    
    val iconTint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "IconTint"
    )

    val innerRadius by animateDpAsState(
        targetValue = if (selected) 20.dp else 6.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "InnerRadius"
    )
    
    val outerRadius = 20.dp
    
    val shape = when {
        selected -> CircleShape
        isFirst -> RoundedCornerShape(
            topStart = outerRadius, 
            bottomStart = outerRadius, 
            topEnd = innerRadius, 
            bottomEnd = innerRadius
        )
        isLast -> RoundedCornerShape(
            topEnd = outerRadius, 
            bottomEnd = outerRadius, 
            topStart = innerRadius, 
            bottomStart = innerRadius
        )
        else -> RoundedCornerShape(innerRadius)
    }

    Box(
        modifier = Modifier
            .height(40.dp)
            .width(56.dp * widthScale) // Menggunakan widthScale untuk animasi memanjang
            .clip(shape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Feedback visual ditangani oleh animasi scale dan warna
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = iconTint
        )
    }
}
