package com.example.ui.components

enum class AllocationTargetType {
    ACCOUNT, CATEGORY, GOAL, DEBT
}

data class AllocationItemData(
    val type: AllocationTargetType,
    val targetId: Long,
    var amount: Double
)
