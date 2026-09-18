package com.technoral.servis.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Kayıtlar cihazda JSON dosyalarında tutulur. Harici bir veritabanı katmanı yerine
 * org.json kullanmak, yedekleme/dışa aktarmayı da tek satıra indiriyor.
 */

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null

private fun JSONObject.str(key: String, def: String = ""): String =
    if (has(key) && !isNull(key)) getString(key) else def

private inline fun <reified T : Enum<T>> JSONObject.enum(key: String, def: T): T {
    val raw = optStringOrNull(key) ?: return def
    return runCatching { enumValueOf<T>(raw) }.getOrDefault(def)
}

fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
    (0 until length()).map { block(getJSONObject(it)) }

fun <T> List<T>.toJsonArray(block: (T) -> JSONObject): JSONArray {
    val arr = JSONArray()
    forEach { arr.put(block(it)) }
    return arr
}

// ---------------------------------------------------------------- Customer

fun Customer.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("contactName", contactName)
    put("phone", phone)
    put("email", email)
    put("address", address)
    put("city", city)
    put("notes", notes)
    put("createdAt", createdAt)
}

fun customerFromJson(o: JSONObject) = Customer(
    id = o.str("id", newId()),
    name = o.str("name"),
    contactName = o.str("contactName"),
    phone = o.str("phone"),
    email = o.str("email"),
    address = o.str("address"),
    city = o.str("city"),
    notes = o.str("notes"),
    createdAt = o.optLongOrNull("createdAt") ?: System.currentTimeMillis(),
)

// ----------------------------------------------------------------- Machine

fun Machine.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("customerId", customerId)
    put("name", name)
    put("brand", brand)
    put("model", model)
    put("serialNo", serialNo)
    put("year", year)
    put("location", location)
    put("installDate", installDate ?: JSONObject.NULL)
    put("warrantyEnd", warrantyEnd ?: JSONObject.NULL)
    put("notes", notes)
    put("createdAt", createdAt)
}

fun machineFromJson(o: JSONObject) = Machine(
    id = o.str("id", newId()),
    customerId = o.str("customerId"),
    name = o.str("name"),
    brand = o.str("brand"),
    model = o.str("model"),
    serialNo = o.str("serialNo"),
    year = o.str("year"),
    location = o.str("location"),
    installDate = o.optLongOrNull("installDate"),
    warrantyEnd = o.optLongOrNull("warrantyEnd"),
    notes = o.str("notes"),
    createdAt = o.optLongOrNull("createdAt") ?: System.currentTimeMillis(),
)

// ----------------------------------------------------------------- Report

fun DepartmentWork.toJson(): JSONObject = JSONObject().apply {
    put("department", department.name)
    put("work", work)
}

fun departmentWorkFromJson(o: JSONObject) = DepartmentWork(
    department = o.enum("department", Department.DIGER),
    work = o.str("work"),
)

fun ServicePhoto.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("path", path)
    put("caption", caption)
    put("tag", tag.name)
    put("createdAt", createdAt)
}

fun servicePhotoFromJson(o: JSONObject) = ServicePhoto(
    id = o.str("id", newId()),
    path = o.str("path"),
    caption = o.str("caption"),
    tag = o.enum("tag", PhotoTag.DIGER),
    createdAt = o.optLongOrNull("createdAt") ?: System.currentTimeMillis(),
)

fun SparePart.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("code", code)
    put("quantity", quantity)
    put("unit", unit)
    put("status", status.name)
    put("note", note)
}

fun sparePartFromJson(o: JSONObject) = SparePart(
    id = o.str("id", newId()),
    name = o.str("name"),
    code = o.str("code"),
    quantity = if (o.has("quantity")) o.optDouble("quantity", 1.0) else 1.0,
    unit = o.str("unit", "adet"),
    status = o.enum("status", PartStatus.TAKILDI),
    note = o.str("note"),
)

fun ServiceReport.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("reportNo", reportNo)
    put("customerId", customerId)
    put("machineId", machineId)
    put("type", type.name)
    put("status", status.name)
    put("priority", priority.name)
    put("serviceDate", serviceDate)
    put("startTime", startTime ?: JSONObject.NULL)
    put("endTime", endTime ?: JSONObject.NULL)
    put("travelKm", travelKm)
    put("departments", departments.toJsonArray { it.toJson() })
    put("faultDescription", faultDescription)
    put("faultCause", faultCause)
    put("workDone", workDone)
    put("recommendations", recommendations)
    put("photos", photos.toJsonArray { it.toJson() })
    put("parts", parts.toJsonArray { it.toJson() })
    put("technician", technician)
    put("customerRep", customerRep)
    put("signaturePath", signaturePath ?: JSONObject.NULL)
    put("nextMaintenance", nextMaintenance ?: JSONObject.NULL)
    put("mailedTo", mailedTo)
    put("mailedAt", mailedAt ?: JSONObject.NULL)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

fun serviceReportFromJson(o: JSONObject) = ServiceReport(
    id = o.str("id", newId()),
    reportNo = o.str("reportNo"),
    customerId = o.str("customerId"),
    machineId = o.str("machineId"),
    type = o.enum("type", ServiceType.ARIZA),
    status = o.enum("status", ServiceStatus.TASLAK),
    priority = o.enum("priority", Priority.NORMAL),
    serviceDate = o.optLongOrNull("serviceDate") ?: System.currentTimeMillis(),
    startTime = o.optLongOrNull("startTime"),
    endTime = o.optLongOrNull("endTime"),
    travelKm = o.optDouble("travelKm", 0.0),
    departments = o.optJSONArray("departments")?.mapObjects { departmentWorkFromJson(it) } ?: emptyList(),
    faultDescription = o.str("faultDescription"),
    faultCause = o.str("faultCause"),
    workDone = o.str("workDone"),
    recommendations = o.str("recommendations"),
    photos = o.optJSONArray("photos")?.mapObjects { servicePhotoFromJson(it) } ?: emptyList(),
    parts = o.optJSONArray("parts")?.mapObjects { sparePartFromJson(it) } ?: emptyList(),
    technician = o.str("technician"),
    customerRep = o.str("customerRep"),
    signaturePath = o.optStringOrNull("signaturePath"),
    nextMaintenance = o.optLongOrNull("nextMaintenance"),
    mailedTo = o.str("mailedTo"),
    mailedAt = o.optLongOrNull("mailedAt"),
    createdAt = o.optLongOrNull("createdAt") ?: System.currentTimeMillis(),
    updatedAt = o.optLongOrNull("updatedAt") ?: System.currentTimeMillis(),
)

// ---------------------------------------------------------------- Settings

fun AppSettings.toJson(): JSONObject = JSONObject().apply {
    put("company", JSONObject().apply {
        put("name", company.name)
        put("address", company.address)
        put("phone", company.phone)
        put("email", company.email)
        put("web", company.web)
        put("taxInfo", company.taxInfo)
        put("logoPath", company.logoPath ?: JSONObject.NULL)
    })
    put("mail", JSONObject().apply {
        put("host", mail.host)
        put("port", mail.port)
        put("security", mail.security)
        put("username", mail.username)
        put("password", mail.password)
        put("fromAddress", mail.fromAddress)
        put("fromName", mail.fromName)
        put("defaultTo", mail.defaultTo)
        put("defaultCc", mail.defaultCc)
        put("attachPhotos", mail.attachPhotos)
        put("subjectTemplate", mail.subjectTemplate)
    })
    put("technicianName", technicianName)
    put("technicianPhone", technicianPhone)
    put("darkTheme", darkTheme ?: JSONObject.NULL)
    put("reportPrefix", reportPrefix)
    put("serverUrl", serverUrl)
    put("setupDone", setupDone)
    put("usdRate", usdRate)
    put("eurRate", eurRate)
    put("ratesUpdatedAt", ratesUpdatedAt ?: JSONObject.NULL)
    put("rateSource", rateSource)
    put("rateDateLabel", rateDateLabel)
    put("showChargeOnPdf", showChargeOnPdf)
    put("expensesBillableByDefault", expensesBillableByDefault)
    put("overdueGraceDays", overdueGraceDays)
}

fun appSettingsFromJson(o: JSONObject): AppSettings {
    val c = o.optJSONObject("company") ?: JSONObject()
    val m = o.optJSONObject("mail") ?: JSONObject()
    return AppSettings(
        company = CompanyInfo(
            name = c.str("name"),
            address = c.str("address"),
            phone = c.str("phone"),
            email = c.str("email"),
            web = c.str("web"),
            taxInfo = c.str("taxInfo"),
            logoPath = c.optStringOrNull("logoPath"),
        ),
        mail = MailSettings(
            host = m.str("host"),
            port = m.optInt("port", 587),
            security = m.str("security", "STARTTLS"),
            username = m.str("username"),
            password = m.str("password"),
            fromAddress = m.str("fromAddress"),
            fromName = m.str("fromName"),
            defaultTo = m.str("defaultTo"),
            defaultCc = m.str("defaultCc"),
            attachPhotos = m.optBoolean("attachPhotos", true),
            subjectTemplate = m.str("subjectTemplate", "Servis Raporu {rapor_no} - {musteri}"),
        ),
        technicianName = o.str("technicianName"),
        technicianPhone = o.str("technicianPhone"),
        darkTheme = if (o.has("darkTheme") && !o.isNull("darkTheme")) o.getBoolean("darkTheme") else null,
        reportPrefix = o.str("reportPrefix", "SRV"),
        serverUrl = o.str("serverUrl"),
        setupDone = o.optBoolean("setupDone", false),
        usdRate = o.optDouble("usdRate", 0.0),
        eurRate = o.optDouble("eurRate", 0.0),
        ratesUpdatedAt = o.optLongOrNull("ratesUpdatedAt"),
        rateSource = o.str("rateSource"),
        rateDateLabel = o.str("rateDateLabel"),
        showChargeOnPdf = o.optBoolean("showChargeOnPdf", false),
        expensesBillableByDefault = o.optBoolean("expensesBillableByDefault", true),
        overdueGraceDays = o.optInt("overdueGraceDays", 0),
    )
}

// ------------------------------------------------------------ Cari / Masraf

fun LedgerEntry.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("customerId", customerId)
    put("reportId", reportId ?: JSONObject.NULL)
    put("type", type.name)
    put("kind", kind.name)
    put("date", date)
    put("amount", amount)
    put("currency", currency.code)
    put("rate", rate)
    put("description", description)
    put("documentNo", documentNo)
    put("dueDate", dueDate ?: JSONObject.NULL)
    put("promisedDate", promisedDate ?: JSONObject.NULL)
    put("paymentMethod", paymentMethod.name)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

fun ledgerEntryFromJson(o: JSONObject): LedgerEntry {
    val reportId = o.optStringOrNull("reportId")
    val type = o.enum("type", LedgerType.BORC)
    // Eski kayıtlarda "kind" yoktu: rapora bağlı borçlar servis bedelidir.
    val kind = if (o.has("kind") && !o.isNull("kind")) {
        o.enum("kind", LedgerKind.DIGER)
    } else if (reportId != null && type == LedgerType.BORC) {
        LedgerKind.SERVIS
    } else {
        LedgerKind.DIGER
    }
    return ledgerEntry(o, reportId, type, kind)
}

private fun ledgerEntry(
    o: JSONObject,
    reportId: String?,
    type: LedgerType,
    kind: LedgerKind,
) = LedgerEntry(
    id = o.str("id", newId()),
    customerId = o.str("customerId"),
    reportId = reportId,
    type = type,
    kind = kind,
    date = o.optLongOrNull("date") ?: System.currentTimeMillis(),
    amount = o.optDouble("amount", 0.0),
    currency = Currency.of(o.optStringOrNull("currency")),
    rate = o.optDouble("rate", 1.0).takeIf { it > 0 } ?: 1.0,
    description = o.str("description"),
    documentNo = o.str("documentNo"),
    dueDate = o.optLongOrNull("dueDate"),
    promisedDate = o.optLongOrNull("promisedDate"),
    paymentMethod = o.enum("paymentMethod", PaymentMethod.NAKIT),
    createdAt = o.optLongOrNull("createdAt") ?: System.currentTimeMillis(),
    updatedAt = o.optLongOrNull("updatedAt") ?: System.currentTimeMillis(),
)

fun Expense.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("reportId", reportId ?: JSONObject.NULL)
    put("customerId", customerId ?: JSONObject.NULL)
    put("category", category.name)
    put("date", date)
    put("amount", amount)
    put("currency", currency.code)
    put("rate", rate)
    put("description", description)
    put("quantity", quantity)
    put("billable", billable)
    put("receiptPath", receiptPath ?: JSONObject.NULL)
    put("createdAt", createdAt)
}

fun expenseFromJson(o: JSONObject) = Expense(
    id = o.str("id", newId()),
    reportId = o.optStringOrNull("reportId"),
    customerId = o.optStringOrNull("customerId"),
    category = o.enum("category", ExpenseCategory.DIGER),
    date = o.optLongOrNull("date") ?: System.currentTimeMillis(),
    amount = o.optDouble("amount", 0.0),
    currency = Currency.of(o.optStringOrNull("currency")),
    rate = o.optDouble("rate", 1.0).takeIf { it > 0 } ?: 1.0,
    description = o.str("description"),
    quantity = o.optDouble("quantity", 0.0),
    billable = o.optBoolean("billable", true),
    receiptPath = o.optStringOrNull("receiptPath"),
    createdAt = o.optLongOrNull("createdAt") ?: System.currentTimeMillis(),
)
