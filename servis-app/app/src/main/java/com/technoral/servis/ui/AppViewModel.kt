package com.technoral.servis.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.technoral.servis.data.AppSettings
import com.technoral.servis.data.Currency
import com.technoral.servis.data.Customer
import com.technoral.servis.data.Expense
import com.technoral.servis.data.Finance
import com.technoral.servis.data.LedgerEntry
import com.technoral.servis.data.LedgerKind
import com.technoral.servis.data.LedgerType
import com.technoral.servis.data.MonthlySummary
import com.technoral.servis.data.RateService
import com.technoral.servis.data.Machine
import com.technoral.servis.data.Repository
import com.technoral.servis.data.ServicePhoto
import com.technoral.servis.data.ServiceReport
import com.technoral.servis.data.ServiceStatus
import com.technoral.servis.mail.MailResult
import com.technoral.servis.mail.MailSender
import com.technoral.servis.mail.MailTemplates
import com.technoral.servis.pdf.ExpensePdf
import com.technoral.servis.pdf.FinancePdf
import com.technoral.servis.pdf.ReportPdf
import com.technoral.servis.util.compressInPlace
import com.technoral.servis.util.fileStamp
import com.technoral.servis.util.importImage
import com.technoral.servis.util.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)

    val customers: StateFlow<List<Customer>> = repo.customers
    val machines: StateFlow<List<Machine>> = repo.machines
    val reports: StateFlow<List<ServiceReport>> = repo.reports
    val ledger: StateFlow<List<LedgerEntry>> = repo.ledger
    val expenses: StateFlow<List<Expense>> = repo.expenses
    val settings: StateFlow<AppSettings> = repo.settings
    val ready: StateFlow<Boolean> = repo.ready

    private val _draft = MutableStateFlow<ServiceReport?>(null)
    val draft: StateFlow<ServiceReport?> = _draft.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _ratesLoading = MutableStateFlow(false)
    val ratesLoading: StateFlow<Boolean> = _ratesLoading.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun notify(message: String) {
        _toast.value = message
    }

    fun clearToast() {
        _toast.value = null
    }

    // ------------------------------------------------------------ kayıtlar

    fun saveCustomer(customer: Customer) = repo.saveCustomer(customer)
    fun deleteCustomer(id: String) = repo.deleteCustomer(id)
    fun customer(id: String?) = repo.customer(id)

    fun saveMachine(machine: Machine) = repo.saveMachine(machine)
    fun deleteMachine(id: String) = repo.deleteMachine(id)
    fun machine(id: String?) = repo.machine(id)
    fun machinesOf(customerId: String?) = repo.machinesOf(customerId)

    fun report(id: String?) = repo.report(id)
    fun reportsOfMachine(machineId: String) = repo.reportsOfMachine(machineId)
    fun reportsOfCustomer(customerId: String) = repo.reportsOfCustomer(customerId)
    fun deleteReport(id: String) = repo.deleteReport(id)

    fun saveSettings(settings: AppSettings) = repo.saveSettings(settings)

    // ------------------------------------------------------------ döviz kuru

    /**
     * Güncel kuru internetten alıp ayarlara yazar. [force] false ise kur taze
     * olduğunda (12 saat) ağa çıkılmaz.
     */
    fun refreshRates(force: Boolean = false, onDone: ((Boolean) -> Unit)? = null) {
        val current = settings.value
        if (!force && !RateService.isStale(current)) {
            onDone?.invoke(true)
            return
        }
        if (_ratesLoading.value) return

        viewModelScope.launch {
            _ratesLoading.value = true
            val result = RateService.fetch()
            _ratesLoading.value = false

            result.onSuccess { quote ->
                repo.saveSettings(
                    settings.value.copy(
                        usdRate = quote.usd,
                        eurRate = quote.eur,
                        ratesUpdatedAt = quote.fetchedAt,
                        rateSource = quote.source,
                        rateDateLabel = quote.dateLabel,
                    )
                )
                if (force) {
                    notify(
                        "Kur güncellendi: 1 USD = ${money(quote.usd)} • 1 EUR = ${money(quote.eur)}"
                    )
                }
                onDone?.invoke(true)
            }.onFailure { error ->
                if (force) notify(error.message ?: "Kur alınamadı.")
                onDone?.invoke(false)
            }
        }
    }

    /**
     * Seçilen para birimi için kuru döndürür; ayarlarda yoksa internetten
     * çekmeyi dener ve sonucu [onRate] ile bildirir.
     */
    fun ensureRate(currency: Currency, onRate: (Double?) -> Unit) {
        if (currency == Currency.TRY) {
            onRate(1.0)
            return
        }
        val existing = settings.value.rateFor(currency)
        if (existing > 0 && !RateService.isStale(settings.value)) {
            onRate(existing)
            return
        }
        refreshRates(force = existing <= 0) { ok ->
            val value = settings.value.rateFor(currency)
            onRate(if (ok && value > 0) value else existing.takeIf { it > 0 })
        }
    }

    /** Kuru girilmemiş (TL karşılığı yanlış) döviz kayıtları. */
    fun recordsMissingRate(): Pair<List<LedgerEntry>, List<Expense>> =
        ledger.value.filter { it.rateMissing } to expenses.value.filter { it.rateMissing }

    /**
     * Kuru eksik kayıtlara güncel kuru uygular. Geçmiş işlem için o günkü kur
     * daha doğru olurdu; bu yüzden kullanıcı tek tek de düzenleyebilir.
     */
    fun fixMissingRates(): Int {
        val current = settings.value
        var fixed = 0
        ledger.value.filter { it.rateMissing }.forEach { entry ->
            val rate = current.rateFor(entry.currency)
            if (rate > 0) {
                repo.saveLedgerEntry(entry.copy(rate = rate))
                fixed++
            }
        }
        expenses.value.filter { it.rateMissing }.forEach { expense ->
            val rate = current.rateFor(expense.currency)
            if (rate > 0) {
                repo.saveExpense(expense.copy(rate = rate))
                fixed++
            }
        }
        return fixed
    }

    // ---------------------------------------------------------------- cari

    fun saveLedgerEntry(entry: LedgerEntry) = repo.saveLedgerEntry(entry)
    fun deleteLedgerEntry(id: String) = repo.deleteLedgerEntry(id)
    fun ledgerOfCustomer(customerId: String) = repo.ledgerOfCustomer(customerId)
    fun chargeOfReport(reportId: String) = repo.chargeOfReport(reportId)

    /** Ayarlardaki gecikme toleransı (gün). */
    val graceDays: Int get() = settings.value.overdueGraceDays

    fun accountOf(customerId: String) = Finance.accountOf(customerId, ledger.value, graceDays)
    fun accounts() = Finance.accounts(customers.value, ledger.value, graceDays)
    fun overdueDebts() = Finance.overdueDebts(customers.value, ledger.value, graceDays)
    fun totalReceivable() = Finance.totalReceivableTry(customers.value, ledger.value)

    fun monthlySummary(year: Int, month: Int): MonthlySummary =
        Finance.summary(year, month, ledger.value, expenses.value, reports.value)

    fun lastMonths(count: Int) = Finance.lastMonths(count, ledger.value, expenses.value, reports.value)

    /** Rapor formundaki servis bedeli: rapora bağlı borç hareketini oluşturur/günceller. */
    fun setReportCharge(
        report: ServiceReport,
        amount: Double,
        currency: Currency,
        rate: Double,
        dueDate: Long?,
        description: String,
    ): Boolean {
        val existing = repo.chargeOfReport(report.id)
        if (amount <= 0.0) {
            existing?.let { repo.deleteLedgerEntry(it.id) }
            return true
        }
        // Döviz tutarını kursuz kaydetmek TL karşılığını yanlış gösterir;
        // 1,0'a düşmek yerine kaydı reddediyoruz.
        val effectiveRate = if (currency == Currency.TRY) 1.0 else rate
        if (effectiveRate <= 0.0) {
            notify("${currency.code} tutarı için kur gerekli. Kuru güncelleyin ya da elle girin.")
            return false
        }
        val entry = (existing ?: LedgerEntry(reportId = report.id, type = LedgerType.BORC)).copy(
            customerId = report.customerId,
            reportId = report.id,
            type = LedgerType.BORC,
            kind = LedgerKind.SERVIS,
            date = report.serviceDate,
            amount = amount,
            currency = currency,
            rate = effectiveRate,
            description = description.ifBlank { "Servis bedeli ${report.reportNo}" },
            dueDate = dueDate,
        )
        repo.saveLedgerEntry(entry)
        return true
    }

    /** Rapora ait, müşteriye yansıtılacak masrafların TL toplamı. */
    fun billableExpenseTotal(reportId: String): Double =
        repo.expensesOfReport(reportId).filter { it.billable }.sumOf { it.tryAmount }

    /** Servis bedeli + yansıtılan masraf: müşterinin bu servis için toplam borcu. */
    fun customerTotalOfReport(reportId: String): Double =
        (repo.chargeOfReport(reportId)?.tryAmount ?: 0.0) + billableExpenseTotal(reportId)

    /**
     * Yansıtılacak masrafları müşterinin carisine tek bir borç kalemi olarak işler.
     * Masraf eklendikçe/silindikçe tutar yeniden hesaplanır; yansıtılacak masraf
     * kalmazsa kalem silinir.
     */
    fun syncExpenseReflection(reportId: String) {
        val report = repo.report(reportId) ?: _draft.value?.takeIf { it.id == reportId } ?: return
        val existing = repo.expenseChargeOfReport(reportId)
        val total = billableExpenseTotal(reportId)

        if (total <= 0.005 || report.customerId.isBlank()) {
            existing?.let { repo.deleteLedgerEntry(it.id) }
            return
        }

        val count = repo.expensesOfReport(reportId).count { it.billable }
        val entry = (existing ?: LedgerEntry(reportId = reportId, type = LedgerType.BORC)).copy(
            customerId = report.customerId,
            reportId = reportId,
            type = LedgerType.BORC,
            kind = LedgerKind.MASRAF,
            date = report.serviceDate,
            amount = total,
            currency = Currency.TRY,
            rate = 1.0,
            description = "Yansıtılan masraflar ($count kalem) — ${report.reportNo}",
            dueDate = repo.chargeOfReport(reportId)?.dueDate,
        )
        repo.saveLedgerEntry(entry)
    }

    // -------------------------------------------------------------- masraf

    fun saveExpense(expense: Expense) {
        repo.saveExpense(expense)
        expense.reportId?.let { syncExpenseReflection(it) }
    }

    fun deleteExpense(id: String) {
        val reportId = expenses.value.firstOrNull { it.id == id }?.reportId
        repo.deleteExpense(id)
        reportId?.let { syncExpenseReflection(it) }
    }

    fun expensesOfReport(reportId: String) = repo.expensesOfReport(reportId)

    /** Masraf fişi fotoğrafını uygulama klasörüne kopyalar. */
    fun importReceipt(uri: Uri, onDone: (String?) -> Unit) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            importImage(getApplication<Application>(), uri, repo.receiptsDir)
        }
        onDone(file?.absolutePath)
    }

    fun receiptsDir(): File = repo.receiptsDir

    // -------------------------------------------------------------- taslak

    fun startNewReport(machineId: String? = null, customerId: String? = null) {
        val s = settings.value
        val machine = machineId?.let { repo.machine(it) }
        _draft.value = ServiceReport(
            reportNo = repo.nextReportNo(),
            technician = s.technicianName,
            customerId = customerId ?: machine?.customerId ?: "",
            machineId = machineId ?: "",
            startTime = System.currentTimeMillis(),
        )
    }

    fun editReport(id: String) {
        _draft.value = repo.report(id)
    }

    fun updateDraft(transform: (ServiceReport) -> ServiceReport) {
        _draft.value = _draft.value?.let(transform)
    }

    fun discardDraft() {
        val current = _draft.value
        _draft.value = null
        // Hiç kaydedilmemiş bir rapordan çıkılıyorsa ona bağlı masraf ve
        // cari kayıtları da geride kalmasın.
        if (current != null && repo.report(current.id) == null) {
            repo.chargeOfReport(current.id)?.let { repo.deleteLedgerEntry(it.id) }
            repo.expenseChargeOfReport(current.id)?.let { repo.deleteLedgerEntry(it.id) }
            repo.expensesOfReport(current.id).forEach { repo.deleteExpense(it.id) }
        }
    }

    /** Taslağı kalıcı hale getirir ve rapor kimliğini döndürür. */
    fun commitDraft(status: ServiceStatus? = null): String? {
        val current = _draft.value ?: return null
        val finished = current.copy(
            status = status ?: if (current.status == ServiceStatus.TASLAK) ServiceStatus.ACIK else current.status,
            technician = current.technician.ifBlank { settings.value.technicianName },
        )
        repo.saveReport(finished)
        _draft.value = null
        return finished.id
    }

    /** Raporu kaydeder ve servis bedelini cari hareket olarak işler. */
    fun commitDraftWithCharge(
        amount: Double,
        currency: Currency,
        rate: Double,
        dueDate: Long?,
        status: ServiceStatus? = null,
    ): String? {
        val current = _draft.value ?: return null
        // Kursuz döviz bedeli kaydı bozar; rapor kaydedilmeden uyarılır.
        if (amount > 0 && currency != Currency.TRY && rate <= 0) {
            notify("${currency.code} tutarı için kur gerekli. Kuru güncelleyin ya da elle girin.")
            return null
        }
        val id = commitDraft(status) ?: return null
        repo.report(id)?.let { saved ->
            setReportCharge(saved, amount, currency, rate, dueDate, "Servis bedeli ${saved.reportNo}")
        } ?: setReportCharge(current, amount, currency, rate, dueDate, "Servis bedeli ${current.reportNo}")
        syncExpenseReflection(id)
        return id
    }

    fun saveDraftSilently() {
        _draft.value?.let { repo.saveReport(it) }
    }

    // ----------------------------------------------------------- fotoğraf

    fun addPhotoFromGallery(uri: Uri) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            importImage(getApplication<Application>(), uri, repo.photosDir)
        }
        if (file == null) {
            notify("Fotoğraf eklenemedi.")
        } else {
            updateDraft { it.copy(photos = it.photos + ServicePhoto(path = file.absolutePath)) }
        }
    }

    fun addPhotoFromCamera(file: File) = viewModelScope.launch {
        withContext(Dispatchers.IO) { compressInPlace(file) }
        updateDraft { it.copy(photos = it.photos + ServicePhoto(path = file.absolutePath)) }
    }

    fun cameraDir(): File = repo.photosDir

    fun updatePhoto(photo: ServicePhoto) = updateDraft { report ->
        report.copy(photos = report.photos.map { if (it.id == photo.id) photo else it })
    }

    fun removePhoto(photo: ServicePhoto) = updateDraft { report ->
        runCatching { File(photo.path).delete() }
        report.copy(photos = report.photos.filterNot { it.id == photo.id })
    }

    /** İmza çizimini PNG olarak kaydeder. */
    fun saveSignature(bitmap: Bitmap) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) {
            val target = File(repo.signaturesDir, "IMZA_${fileStamp()}.png")
            FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            target
        }
        updateDraft { it.copy(signaturePath = file.absolutePath) }
    }

    fun clearSignature() = updateDraft { it.copy(signaturePath = null) }

    // ---------------------------------------------------------------- PDF

    suspend fun buildPdf(report: ServiceReport): File? = withContext(Dispatchers.IO) {
        runCatching {
            val safeNo = report.reportNo.ifBlank { "rapor" }.replace(Regex("[^A-Za-z0-9-_]"), "_")
            val target = File(repo.documentsDir, "$safeNo.pdf")
            val charge = repo.chargeOfReport(report.id)
            ReportPdf.build(
                target = target,
                report = report,
                customer = repo.customer(report.customerId),
                machine = repo.machine(report.machineId),
                settings = settings.value,
                chargeText = if (settings.value.showChargeOnPdf && charge != null)
                    money(charge.amount, charge.currency.symbol) else null,
            )
        }.getOrNull()
    }

    /** Aylık finans raporunu PDF'e döker. */
    suspend fun buildFinancePdf(summary: MonthlySummary): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(repo.documentsDir, "finans_${summary.year}_${summary.month + 1}.pdf")
            FinancePdf.build(
                target = target,
                summary = summary,
                settings = settings.value,
                customerName = { id -> repo.customer(id)?.name ?: "-" },
                overdue = overdueDebts(),
                openBalances = customers.value.map { it to Finance.accountOf(it.id, ledger.value).balanceTry },
            )
        }.getOrNull()
    }

    fun sendFinanceMail(
        summary: MonthlySummary,
        to: String,
        note: String,
        onResult: (MailResult) -> Unit,
    ) = viewModelScope.launch {
        _busy.value = "Rapor gönderiliyor…"
        val s = settings.value
        val pdf = buildFinancePdf(summary)
        val result = MailSender.send(
            settings = s.mail,
            to = to,
            subject = "${s.company.name.ifBlank { "Servis" }} • ${summary.label} Finans Raporu",
            bodyHtml = MailTemplates.financeBody(s, summary, note),
            attachments = listOfNotNull(pdf),
        )
        _busy.value = null
        onResult(result)
    }

    /** Masraf dökümü: kalem listesi + tüm fiş fotoğrafları tek PDF'te. */
    suspend fun buildExpensePdf(report: ServiceReport): File? = withContext(Dispatchers.IO) {
        runCatching {
            val safeNo = report.reportNo.ifBlank { "rapor" }.replace(Regex("[^A-Za-z0-9-_]"), "_")
            val target = File(repo.documentsDir, "${safeNo}_masraf.pdf")
            ExpensePdf.build(
                target = target,
                report = report,
                customer = repo.customer(report.customerId),
                machine = repo.machine(report.machineId),
                expenses = repo.expensesOfReport(report.id).sortedBy { it.date },
                charge = repo.chargeOfReport(report.id),
                settings = settings.value,
            )
        }.getOrNull()
    }

    // --------------------------------------------------------------- mail

    fun defaultRecipients(report: ServiceReport): String {
        val customerMail = repo.customer(report.customerId)?.email.orEmpty()
        val configured = settings.value.mail.defaultTo
        return listOf(customerMail, configured).filter { it.isNotBlank() }.distinct().joinToString(", ")
    }

    fun sendReportMail(
        report: ServiceReport,
        to: String,
        cc: String,
        extraNote: String,
        includePhotos: Boolean,
        includeExpenses: Boolean = false,
        onResult: (MailResult) -> Unit,
    ) = viewModelScope.launch {
        _busy.value = "Mail gönderiliyor…"
        val s = settings.value
        val customer = repo.customer(report.customerId)
        val machine = repo.machine(report.machineId)
        val pdf = buildPdf(report)

        val expensePdf = if (includeExpenses) buildExpensePdf(report) else null

        val attachments = buildList {
            pdf?.let { add(it) }
            expensePdf?.let { add(it) }
            if (includePhotos) {
                report.photos.map { File(it.path) }.filter { it.exists() }.take(12).forEach { add(it) }
            }
        }

        var html = MailTemplates.body(s, report, customer, machine)
        if (extraNote.isNotBlank()) {
            val note = extraNote
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "<br>")
            html = html.replace(
                "<p style=\"margin:0 0 12px;font-size:14px;line-height:1.6;color:#334155\">Merhaba,</p>",
                "<p style=\"margin:0 0 12px;font-size:14px;line-height:1.6;color:#334155\">Merhaba,</p>" +
                    "<div style=\"margin:0 0 16px;padding:12px 14px;background:#f1f5f9;border-left:3px solid #0f4c75;" +
                    "font-size:14px;line-height:1.6;color:#334155\">$note</div>",
            )
        }

        val result = MailSender.send(
            settings = s.mail,
            to = to,
            cc = cc,
            subject = MailTemplates.subject(s, report, customer, machine),
            bodyHtml = html,
            attachments = attachments,
        )
        if (result.success) {
            repo.saveReport(report.copy(mailedTo = to, mailedAt = System.currentTimeMillis()))
        }
        _busy.value = null
        onResult(result)
    }

    fun sendTestMail(to: String, onResult: (MailResult) -> Unit) = viewModelScope.launch {
        _busy.value = "Test maili gönderiliyor…"
        val s = settings.value
        val result = MailSender.send(
            settings = s.mail,
            to = to,
            subject = "TeknoServis • Mail ayarı testi",
            bodyHtml = MailTemplates.testBody(s),
        )
        _busy.value = null
        onResult(result)
    }

    // ----------------------------------------------------------- yedekleme

    suspend fun exportBackup(): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(repo.backupDir, "teknoservis_yedek_${fileStamp()}.json")
            target.writeText(repo.exportJson())
            target
        }.getOrNull()
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        }
        if (text.isNullOrBlank()) {
            notify("Yedek dosyası okunamadı.")
            return@launch
        }
        val result = runCatching { repo.importJson(text) }.getOrNull()
        if (result == null) {
            notify("Yedek dosyası geçersiz.")
        } else {
            notify("Geri yüklendi: ${result.first} müşteri, ${result.second} makine, ${result.third} rapor.")
        }
    }

    fun documentsDir(): File = repo.documentsDir
}
