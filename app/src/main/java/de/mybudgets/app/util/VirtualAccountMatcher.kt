package de.mybudgets.app.util

import de.mybudgets.app.data.model.Account

object VirtualAccountMatcher {
    fun match(description: String, virtualAccounts: List<Account>): Account? {
        val compiled = virtualAccounts
            .filter { it.isVirtual && it.autoAssignPattern.isNotBlank() }
            .mapNotNull { account ->
                runCatching { account to Regex(account.autoAssignPattern, RegexOption.IGNORE_CASE) }.getOrNull()
            }
            .sortedByDescending { (account, _) -> account.autoAssignPattern.length }
        for ((account, regex) in compiled) {
            if (regex.containsMatchIn(description)) {
                return account
            }
        }
        return null
    }
}
