package com.gama.suite.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.ui.graphics.vector.ImageVector

/** Traduce el nombre lógico de ícono que envía el servidor a un ícono de Material. */
fun iconoModulo(nombre: String): ImageVector = when (nombre) {
    "admin" -> Icons.Outlined.AdminPanelSettings
    "forum" -> Icons.Outlined.Forum
    "campaign" -> Icons.Outlined.Campaign
    "factory" -> Icons.Outlined.Factory
    "build" -> Icons.Outlined.Build
    "verified" -> Icons.Outlined.Verified
    "trending_up" -> Icons.AutoMirrored.Outlined.TrendingUp
    "local_shipping" -> Icons.Outlined.LocalShipping
    "inventory" -> Icons.Outlined.Inventory
    "point_of_sale" -> Icons.Outlined.PointOfSale
    "payments" -> Icons.Outlined.Payments
    "health_and_safety" -> Icons.Outlined.HealthAndSafety
    "groups" -> Icons.Outlined.Groups
    "bolt" -> Icons.Outlined.Bolt
    "engineering" -> Icons.Outlined.Engineering
    "precision_manufacturing" -> Icons.Outlined.PrecisionManufacturing
    "devices" -> Icons.Outlined.Devices
    "smart_toy" -> Icons.Outlined.SmartToy
    else -> Icons.Outlined.Apps // módulo nuevo que esta versión de la app aún no conoce
}
