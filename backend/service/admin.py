from django.contrib import admin

from .models import (
    Customer,
    DepartmentWork,
    Expense,
    FinanceSettings,
    LedgerEntry,
    Machine,
    MailSettings,
    ServicePhoto,
    ServiceReport,
    SparePart,
)


class MachineInline(admin.TabularInline):
    model = Machine
    extra = 0
    fields = ("name", "brand", "model", "serial_no", "location")


@admin.register(Customer)
class CustomerAdmin(admin.ModelAdmin):
    list_display = ("name", "contact_name", "phone", "email", "city")
    ordering = ["name"]
    search_fields = ("name", "contact_name", "phone", "email", "city")
    inlines = [MachineInline]


@admin.register(Machine)
class MachineAdmin(admin.ModelAdmin):
    list_display = ("__str__", "serial_no", "location", "warranty_end")
    list_filter = ("brand", "customer")
    search_fields = ("name", "brand", "model", "serial_no", "customer__name")


class DepartmentWorkInline(admin.TabularInline):
    model = DepartmentWork
    extra = 0


class SparePartInline(admin.TabularInline):
    model = SparePart
    extra = 0


class ExpenseInline(admin.TabularInline):
    model = Expense
    extra = 0
    fields = ("category", "date", "amount", "currency", "rate", "description", "billable")


class ServicePhotoInline(admin.TabularInline):
    model = ServicePhoto
    extra = 0
    fields = ("image", "tag", "caption")


@admin.register(ServiceReport)
class ServiceReportAdmin(admin.ModelAdmin):
    list_display = ("report_no", "customer", "machine", "type", "status", "service_date", "technician_name")
    list_filter = ("status", "type", "priority", "service_date")
    search_fields = ("report_no", "customer__name", "machine__serial_no", "fault_description", "work_done")
    date_hierarchy = "service_date"
    readonly_fields = ("report_no", "mailed_to", "mailed_at", "created_at", "updated_at")
    inlines = [DepartmentWorkInline, SparePartInline, ServicePhotoInline, ExpenseInline]


@admin.register(LedgerEntry)
class LedgerEntryAdmin(admin.ModelAdmin):
    list_display = ("date", "customer", "type", "amount", "currency", "due_date", "promised_date")
    list_filter = ("type", "currency", "date")
    search_fields = ("customer__name", "description", "document_no")
    date_hierarchy = "date"
    autocomplete_fields = ["customer"]


@admin.register(Expense)
class ExpenseAdmin(admin.ModelAdmin):
    list_display = ("date", "category", "amount", "currency", "customer", "report", "billable")
    list_filter = ("category", "currency", "billable", "date")
    search_fields = ("description", "customer__name", "report__report_no")
    date_hierarchy = "date"


@admin.register(FinanceSettings)
class FinanceSettingsAdmin(admin.ModelAdmin):
    list_display = ("usd_rate", "eur_rate", "rates_updated_at", "show_charge_on_pdf")

    def has_add_permission(self, request):
        return not FinanceSettings.objects.exists()


@admin.register(MailSettings)
class MailSettingsAdmin(admin.ModelAdmin):
    list_display = ("host", "port", "security", "from_address", "is_configured")
    fieldsets = (
        ("SMTP Sunucusu", {"fields": ("host", "port", "security", "username", "password")}),
        ("Gönderen", {"fields": ("from_address", "from_name")}),
        ("Varsayılanlar", {"fields": ("default_to", "default_cc", "subject_template", "attach_photos")}),
        ("Firma Künyesi", {
            "fields": ("company_name", "company_address", "company_phone", "company_email", "company_web")
        }),
    )

    def has_add_permission(self, request):
        # Tek kayıt tutulur
        return not MailSettings.objects.exists()
