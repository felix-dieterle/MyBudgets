package de.mybudgets.app.data.api.dto

data class SyncResponse(
    val serverTime: Long = 0,
    val accounts: List<AccountDto> = emptyList(),
    val transactions: List<TransactionDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList()
)

data class CategoryDto(
    val id: Long = 0,
    val name: String = "",
    val parentCategoryId: Long? = null,
    val color: Int = 0,
    val icon: String = "ic_category",
    val pattern: String = "",
    val level: Int = 1,
    val isDefault: Boolean = false
)
