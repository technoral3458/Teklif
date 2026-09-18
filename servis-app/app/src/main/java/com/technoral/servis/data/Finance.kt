package com.technoral.servis.data

import java.util.Calendar

/**
 * Cari hesaplamalar. Tahsilatlar, en eski borçtan başlayarak (FIFO) kapatılır;
 * böylece hangi alacağın hâlâ açık olduğu ve vadesinin geçip geçmediği çıkarılabilir.
 */

/**
 * Açık kalan bir borç kalemi.
 *
 * [graceDays] ayarlardan gelen tolerans: vade gününde hemen uyarı vermemek için
 * bu kadar gün beklenir.
 */
data class OpenDebt(
    val entry: LedgerEntry,
    val openTry: Double,
    val graceDays: Int = 0,
) {
    /** Açık tutarın kendi para birimindeki karşılığı. */
    val openAmount: Double get() = if (entry.rate > 0) openTry / entry.rate else openTry

    private val deadline: Long?
        get() = entry.promisedDate ?: entry.dueDate

    val isOverdue: Boolean
        get() = daysLate > graceDays

    /** Vade mi yoksa verilen söz mü geçti? */
    val brokenPromise: Boolean
        get() = isOverdue && entry.promisedDate != null

    val daysLate: Long
        get() {
            val due = deadline ?: return 0
            return ((todayStart() - due) / 86_400_000L).coerceAtLeast(0)
        }
}

data class CustomerAccount(
    val customerId: String,
    val debitTry: Double,
    val creditTry: Double,
    val openDebts: List<OpenDebt>,
    val lastActivity: Long?,
) {
    val balanceTry: Double get() = debitTry - creditTry
    val overdueTry: Double get() = openDebts.filter { it.isOverdue }.sumOf { it.openTry }
    val hasOverdue: Boolean get() = openDebts.any { it.isOverdue }
    val brokenPromises: List<OpenDebt> get() = openDebts.filter { it.brokenPromise }
}

data class MonthlySummary(
    val year: Int,
    val month: Int,
    val incomeTry: Double,
    val collectedTry: Double,
    val expenseTry: Double,
    val billableExpenseTry: Double,
    val serviceCount: Int,
    val fuelLiters: Double,
    val expenseByCategory: List<Pair<ExpenseCategory, Double>>,
    val incomeByCurrency: List<Pair<Currency, Double>>,
    val topCustomers: List<Triple<String, Double, Double>>, // müşteri kimliği, hakediş, tahsilat
) {
    /** Dönem kârı: hakediş - masraf. */
    val netTry: Double get() = incomeTry - expenseTry

    /** Kasa hareketi: tahsil edilen - harcanan. */
    val cashFlowTry: Double get() = collectedTry - expenseTry

    val label: String get() = "${monthNames[month]} $year"
}

val monthNames = listOf(
    "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
)

fun todayStart(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun monthRange(year: Int, month: Int): LongRange {
    val start = Calendar.getInstance().apply {
        set(year, month, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
    return start.timeInMillis until end.timeInMillis
}

fun currentYearMonth(): Pair<Int, Int> {
    val c = Calendar.getInstance()
    return c.get(Calendar.YEAR) to c.get(Calendar.MONTH)
}

object Finance {

    /**
     * Bir müşterinin cari durumunu çıkarır. Tahsilat ve iadeler tarih sırasıyla
     * en eski borçtan başlayarak düşülür; kalan açık borçlar vade kontrolüne girer.
     */
    fun accountOf(
        customerId: String,
        entries: List<LedgerEntry>,
        graceDays: Int = 0,
    ): CustomerAccount {
        val own = entries.filter { it.customerId == customerId }.sortedBy { it.date }
        val debits = own.filter { it.type == LedgerType.BORC }
        val credits = own.filter { it.type != LedgerType.BORC }

        val debitTry = debits.sumOf { it.tryAmount }
        val creditTry = credits.sumOf { it.tryAmount }

        var remaining = creditTry
        val openDebts = mutableListOf<OpenDebt>()
        debits.forEach { debt ->
            val paid = minOf(remaining, debt.tryAmount)
            remaining -= paid
            val open = debt.tryAmount - paid
            if (open > 0.005) openDebts.add(OpenDebt(debt, open, graceDays))
        }

        return CustomerAccount(
            customerId = customerId,
            debitTry = debitTry,
            creditTry = creditTry,
            openDebts = openDebts,
            lastActivity = own.maxOfOrNull { it.date },
        )
    }

    fun accounts(
        customers: List<Customer>,
        entries: List<LedgerEntry>,
        graceDays: Int = 0,
    ): List<CustomerAccount> = customers.map { accountOf(it.id, entries, graceDays) }

    /** Tüm müşterilerdeki vadesi geçmiş açık alacaklar, en gecikmişten başlayarak. */
    fun overdueDebts(
        customers: List<Customer>,
        entries: List<LedgerEntry>,
        graceDays: Int = 0,
    ): List<Pair<String, OpenDebt>> =
        customers.flatMap { customer ->
            accountOf(customer.id, entries, graceDays).openDebts
                .filter { it.isOverdue }
                .map { customer.id to it }
        }.sortedByDescending { it.second.daysLate }

    fun totalReceivableTry(customers: List<Customer>, entries: List<LedgerEntry>): Double =
        customers.sumOf { accountOf(it.id, entries).balanceTry.coerceAtLeast(0.0) }

    /** Yalnızca gecikmiş alacakların toplamı. */
    fun overdueTotalTry(
        customers: List<Customer>,
        entries: List<LedgerEntry>,
        graceDays: Int = 0,
    ): Double = overdueDebts(customers, entries, graceDays).sumOf { it.second.openTry }

    fun summary(
        year: Int,
        month: Int,
        entries: List<LedgerEntry>,
        expenses: List<Expense>,
        reports: List<ServiceReport>,
    ): MonthlySummary {
        val range = monthRange(year, month)
        val periodEntries = entries.filter { it.date in range }
        val periodExpenses = expenses.filter { it.date in range }

        val income = periodEntries.filter { it.type == LedgerType.BORC }
        val collected = periodEntries.filter { it.type == LedgerType.TAHSILAT }

        val byCustomer = mutableMapOf<String, Pair<Double, Double>>()
        income.forEach { entry ->
            val current = byCustomer[entry.customerId] ?: (0.0 to 0.0)
            byCustomer[entry.customerId] = (current.first + entry.tryAmount) to current.second
        }
        collected.forEach { entry ->
            val current = byCustomer[entry.customerId] ?: (0.0 to 0.0)
            byCustomer[entry.customerId] = current.first to (current.second + entry.tryAmount)
        }

        return MonthlySummary(
            year = year,
            month = month,
            incomeTry = income.sumOf { it.tryAmount },
            collectedTry = collected.sumOf { it.tryAmount },
            expenseTry = periodExpenses.sumOf { it.tryAmount },
            billableExpenseTry = periodExpenses.filter { it.billable }.sumOf { it.tryAmount },
            serviceCount = reports.count { it.serviceDate in range },
            fuelLiters = periodExpenses.filter { it.category == ExpenseCategory.YAKIT }.sumOf { it.quantity },
            expenseByCategory = periodExpenses
                .groupBy { it.category }
                .map { (category, list) -> category to list.sumOf { it.tryAmount } }
                .sortedByDescending { it.second },
            incomeByCurrency = income
                .groupBy { it.currency }
                .map { (currency, list) -> currency to list.sumOf { it.amount } }
                .sortedByDescending { it.first.ordinal },
            topCustomers = byCustomer.entries
                .sortedByDescending { it.value.first }
                .take(8)
                .map { Triple(it.key, it.value.first, it.value.second) },
        )
    }

    /** Son 12 ayın hakediş/masraf serisi — özet ekranındaki mini grafik için. */
    fun lastMonths(
        count: Int,
        entries: List<LedgerEntry>,
        expenses: List<Expense>,
        reports: List<ServiceReport>,
    ): List<MonthlySummary> {
        val c = Calendar.getInstance()
        return (0 until count).map { offset ->
            val cursor = (c.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
            summary(
                cursor.get(Calendar.YEAR), cursor.get(Calendar.MONTH),
                entries, expenses, reports,
            )
        }.reversed()
    }
}
