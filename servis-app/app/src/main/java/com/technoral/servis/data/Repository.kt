package com.technoral.servis.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Tüm veriyi cihazda tutan tek depo. İnternet olmadan da çalışır; kayıtlar
 * uygulama klasöründeki JSON dosyalarına yazılır.
 */
class Repository private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val dir: File get() = appContext.filesDir
    private val customersFile get() = File(dir, "musteriler.json")
    private val machinesFile get() = File(dir, "makineler.json")
    private val reportsFile get() = File(dir, "raporlar.json")
    private val settingsFile get() = File(dir, "ayarlar.json")
    private val ledgerFile get() = File(dir, "cari.json")
    private val expensesFile get() = File(dir, "masraflar.json")

    val photosDir: File get() = File(dir, "fotograflar").apply { mkdirs() }
    val signaturesDir: File get() = File(dir, "imzalar").apply { mkdirs() }
    val documentsDir: File get() = File(dir, "belgeler").apply { mkdirs() }
    val backupDir: File get() = File(dir, "yedek").apply { mkdirs() }
    val receiptsDir: File get() = File(dir, "fisler").apply { mkdirs() }

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _machines = MutableStateFlow<List<Machine>>(emptyList())
    val machines: StateFlow<List<Machine>> = _machines.asStateFlow()

    private val _reports = MutableStateFlow<List<ServiceReport>>(emptyList())
    val reports: StateFlow<List<ServiceReport>> = _reports.asStateFlow()

    private val _ledger = MutableStateFlow<List<LedgerEntry>>(emptyList())
    val ledger: StateFlow<List<LedgerEntry>> = _ledger.asStateFlow()

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch { load() }
    }

    private fun readArray(file: File): JSONArray? = runCatching {
        if (!file.exists()) null else JSONArray(file.readText())
    }.getOrNull()

    private suspend fun load() {
        _customers.value = readArray(customersFile)?.mapObjects { customerFromJson(it) } ?: emptyList()
        _machines.value = readArray(machinesFile)?.mapObjects { machineFromJson(it) } ?: emptyList()
        _reports.value = readArray(reportsFile)?.mapObjects { serviceReportFromJson(it) } ?: emptyList()
        _ledger.value = (readArray(ledgerFile)?.mapObjects { ledgerEntryFromJson(it) } ?: emptyList())
            .sortedByDescending { it.date }
        _expenses.value = (readArray(expensesFile)?.mapObjects { expenseFromJson(it) } ?: emptyList())
            .sortedByDescending { it.date }
        _settings.value = runCatching {
            if (settingsFile.exists()) appSettingsFromJson(JSONObject(settingsFile.readText())) else AppSettings()
        }.getOrDefault(AppSettings())
        _ready.value = true
    }

    /**
     * Önce .tmp dosyasına yazıp üzerine taşır. Taşıma tek adımda yapıldığı için
     * yazma sırasında uygulama kapanırsa eski dosya bozulmadan kalır.
     */
    private suspend fun writeAtomic(file: File, content: String) = writeLock.withLock {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(content)
        val moved = runCatching {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.isSuccess
        if (!moved) {
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }

    private fun persistCustomers() = scope.launch {
        writeAtomic(customersFile, _customers.value.toJsonArray { it.toJson() }.toString())
    }

    private fun persistMachines() = scope.launch {
        writeAtomic(machinesFile, _machines.value.toJsonArray { it.toJson() }.toString())
    }

    private fun persistReports() = scope.launch {
        writeAtomic(reportsFile, _reports.value.toJsonArray { it.toJson() }.toString())
    }

    private fun persistLedger() = scope.launch {
        writeAtomic(ledgerFile, _ledger.value.toJsonArray { it.toJson() }.toString())
    }

    private fun persistExpenses() = scope.launch {
        writeAtomic(expensesFile, _expenses.value.toJsonArray { it.toJson() }.toString())
    }

    private fun persistSettings() = scope.launch {
        writeAtomic(settingsFile, _settings.value.toJson().toString())
    }

    // ------------------------------------------------------------ Müşteri

    fun saveCustomer(customer: Customer) {
        val list = _customers.value.toMutableList()
        val idx = list.indexOfFirst { it.id == customer.id }
        if (idx >= 0) list[idx] = customer else list.add(customer)
        _customers.value = list.sortedBy { it.name.lowercase(Locale("tr", "TR")) }
        persistCustomers()
    }

    fun deleteCustomer(id: String) {
        _customers.value = _customers.value.filterNot { it.id == id }
        persistCustomers()
    }

    fun customer(id: String?): Customer? = _customers.value.firstOrNull { it.id == id }

    // ------------------------------------------------------------ Makine

    fun saveMachine(machine: Machine) {
        val list = _machines.value.toMutableList()
        val idx = list.indexOfFirst { it.id == machine.id }
        if (idx >= 0) list[idx] = machine else list.add(machine)
        _machines.value = list.sortedBy { it.displayName.lowercase(Locale("tr", "TR")) }
        persistMachines()
    }

    fun deleteMachine(id: String) {
        _machines.value = _machines.value.filterNot { it.id == id }
        persistMachines()
    }

    fun machine(id: String?): Machine? = _machines.value.firstOrNull { it.id == id }

    fun machinesOf(customerId: String?): List<Machine> =
        if (customerId.isNullOrBlank()) _machines.value
        else _machines.value.filter { it.customerId == customerId }

    // ------------------------------------------------------------ Rapor

    fun saveReport(report: ServiceReport) {
        val stamped = report.copy(
            updatedAt = System.currentTimeMillis(),
            reportNo = report.reportNo.ifBlank { nextReportNo() },
        )
        val list = _reports.value.toMutableList()
        val idx = list.indexOfFirst { it.id == stamped.id }
        if (idx >= 0) list[idx] = stamped else list.add(stamped)
        _reports.value = list.sortedByDescending { it.serviceDate }
        persistReports()
    }

    fun deleteReport(id: String) {
        val report = report(id)
        report?.photos?.forEach { runCatching { File(it.path).delete() } }
        report?.signaturePath?.let { runCatching { File(it).delete() } }
        _reports.value = _reports.value.filterNot { it.id == id }
        persistReports()

        if (_ledger.value.any { it.reportId == id }) {
            _ledger.value = _ledger.value.filterNot { it.reportId == id }
            persistLedger()
        }
        val reportExpenses = _expenses.value.filter { it.reportId == id }
        if (reportExpenses.isNotEmpty()) {
            reportExpenses.forEach { e -> e.receiptPath?.let { runCatching { File(it).delete() } } }
            _expenses.value = _expenses.value.filterNot { it.reportId == id }
            persistExpenses()
        }
    }

    fun report(id: String?): ServiceReport? = _reports.value.firstOrNull { it.id == id }

    fun reportsOfMachine(machineId: String): List<ServiceReport> =
        _reports.value.filter { it.machineId == machineId }.sortedByDescending { it.serviceDate }

    fun reportsOfCustomer(customerId: String): List<ServiceReport> =
        _reports.value.filter { it.customerId == customerId }.sortedByDescending { it.serviceDate }

    /** SRV-202609-0007 biçiminde sıradaki rapor numarası. */
    fun nextReportNo(): String {
        val prefix = _settings.value.reportPrefix.ifBlank { "SRV" }
        val period = SimpleDateFormat("yyyyMM", Locale("tr", "TR")).format(System.currentTimeMillis())
        val head = "$prefix-$period-"
        val last = _reports.value
            .mapNotNull { it.reportNo.takeIf { no -> no.startsWith(head) } }
            .mapNotNull { it.removePrefix(head).toIntOrNull() }
            .maxOrNull() ?: 0
        return head + String.format(Locale.US, "%04d", last + 1)
    }

    // -------------------------------------------------------------- Cari

    fun saveLedgerEntry(entry: LedgerEntry) {
        val stamped = entry.copy(updatedAt = System.currentTimeMillis())
        val list = _ledger.value.toMutableList()
        val idx = list.indexOfFirst { it.id == stamped.id }
        if (idx >= 0) list[idx] = stamped else list.add(stamped)
        _ledger.value = list.sortedByDescending { it.date }
        persistLedger()
    }

    fun deleteLedgerEntry(id: String) {
        _ledger.value = _ledger.value.filterNot { it.id == id }
        persistLedger()
    }

    fun ledgerEntry(id: String?): LedgerEntry? = _ledger.value.firstOrNull { it.id == id }

    fun ledgerOfCustomer(customerId: String): List<LedgerEntry> =
        _ledger.value.filter { it.customerId == customerId }.sortedByDescending { it.date }

    fun ledgerOfReport(reportId: String): List<LedgerEntry> =
        _ledger.value.filter { it.reportId == reportId }

    /** Servis raporunun ücreti: rapora bağlı tek bir borç hareketi olarak tutulur. */
    fun chargeOfReport(reportId: String): LedgerEntry? =
        _ledger.value.firstOrNull {
            it.reportId == reportId && it.type == LedgerType.BORC && it.kind == LedgerKind.SERVIS
        }

    /** Rapora ait masrafların müşteriye yansıtıldığı borç kalemi. */
    fun expenseChargeOfReport(reportId: String): LedgerEntry? =
        _ledger.value.firstOrNull {
            it.reportId == reportId && it.type == LedgerType.BORC && it.kind == LedgerKind.MASRAF
        }

    // ------------------------------------------------------------ Masraf

    fun saveExpense(expense: Expense) {
        val list = _expenses.value.toMutableList()
        val idx = list.indexOfFirst { it.id == expense.id }
        if (idx >= 0) list[idx] = expense else list.add(expense)
        _expenses.value = list.sortedByDescending { it.date }
        persistExpenses()
    }

    fun deleteExpense(id: String) {
        _expenses.value.firstOrNull { it.id == id }?.receiptPath?.let { runCatching { File(it).delete() } }
        _expenses.value = _expenses.value.filterNot { it.id == id }
        persistExpenses()
    }

    fun expensesOfReport(reportId: String): List<Expense> =
        _expenses.value.filter { it.reportId == reportId }

    // ------------------------------------------------------------ Ayarlar

    fun saveSettings(settings: AppSettings) {
        _settings.value = settings
        persistSettings()
    }

    // ------------------------------------------------------------ Yedekleme

    /** Tüm veriyi tek bir JSON metnine çevirir (mail/paylaş ile dışarı aktarmak için). */
    fun exportJson(includeSecrets: Boolean = false): String {
        val root = JSONObject()
        root.put("uygulama", "TeknoServis")
        root.put("surum", 1)
        root.put("tarih", System.currentTimeMillis())
        root.put("musteriler", _customers.value.toJsonArray { it.toJson() })
        root.put("makineler", _machines.value.toJsonArray { it.toJson() })
        root.put("raporlar", _reports.value.toJsonArray { it.toJson() })
        root.put("cari", _ledger.value.toJsonArray { it.toJson() })
        root.put("masraflar", _expenses.value.toJsonArray { it.toJson() })
        val s = if (includeSecrets) _settings.value else _settings.value.copy(
            mail = _settings.value.mail.copy(password = "")
        )
        root.put("ayarlar", s.toJson())
        return root.toString(2)
    }

    /** Yedeği geri yükler. Aynı kimliğe sahip kayıtlar güncellenir, yenileri eklenir. */
    fun importJson(text: String): Triple<Int, Int, Int> {
        val root = JSONObject(text)
        var c = 0; var m = 0; var r = 0

        root.optJSONArray("musteriler")?.mapObjects { customerFromJson(it) }?.let { imported ->
            val map = _customers.value.associateBy { it.id }.toMutableMap()
            imported.forEach { map[it.id] = it; c++ }
            _customers.value = map.values.sortedBy { it.name.lowercase(Locale("tr", "TR")) }
            persistCustomers()
        }
        root.optJSONArray("makineler")?.mapObjects { machineFromJson(it) }?.let { imported ->
            val map = _machines.value.associateBy { it.id }.toMutableMap()
            imported.forEach { map[it.id] = it; m++ }
            _machines.value = map.values.sortedBy { it.displayName.lowercase(Locale("tr", "TR")) }
            persistMachines()
        }
        root.optJSONArray("raporlar")?.mapObjects { serviceReportFromJson(it) }?.let { imported ->
            val map = _reports.value.associateBy { it.id }.toMutableMap()
            imported.forEach { map[it.id] = it; r++ }
            _reports.value = map.values.sortedByDescending { it.serviceDate }
            persistReports()
        }
        root.optJSONArray("cari")?.mapObjects { ledgerEntryFromJson(it) }?.let { imported ->
            val map = _ledger.value.associateBy { it.id }.toMutableMap()
            imported.forEach { map[it.id] = it }
            _ledger.value = map.values.sortedByDescending { it.date }
            persistLedger()
        }
        root.optJSONArray("masraflar")?.mapObjects { expenseFromJson(it) }?.let { imported ->
            val map = _expenses.value.associateBy { it.id }.toMutableMap()
            imported.forEach { map[it.id] = it }
            _expenses.value = map.values.sortedByDescending { it.date }
            persistExpenses()
        }
        return Triple(c, m, r)
    }

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}
