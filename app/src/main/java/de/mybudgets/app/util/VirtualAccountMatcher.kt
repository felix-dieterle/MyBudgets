package de.mybudgets.app.util

import de.mybudgets.app.data.model.Account

object VirtualAccountMatcher {
    fun match(description: String, virtualAccounts: List<Account>): Account? {
        val sorted = virtualAccounts
            .filter { it.isVirtual && it.autoAssignPattern.isNotBlank() }
            .sortedByDescending { it.autoAssignPattern.length }
        for (account in sorted) {
            try {
                if (Regex(account.autoAssignPattern, RegexOption.IGNORE_CASE).containsMatchIn(description)) {
                    return account
                }
            } catch (_: Exception) {
                // invalid regex -> ignore
            }
        }
        return null
    }
}
