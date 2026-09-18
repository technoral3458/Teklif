from decimal import Decimal

from rest_framework import serializers

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


class CustomerSerializer(serializers.ModelSerializer):
    machine_count = serializers.IntegerField(source="machines.count", read_only=True)
    report_count = serializers.IntegerField(source="reports.count", read_only=True)

    class Meta:
        model = Customer
        fields = [
            "id", "name", "contact_name", "phone", "email", "address", "city",
            "notes", "external_id", "machine_count", "report_count", "created_at",
        ]
        read_only_fields = ["created_at"]


class MachineSerializer(serializers.ModelSerializer):
    customer_name = serializers.CharField(source="customer.name", read_only=True)
    display_name = serializers.SerializerMethodField()
    report_count = serializers.IntegerField(source="reports.count", read_only=True)

    class Meta:
        model = Machine
        fields = [
            "id", "customer", "customer_name", "display_name", "name", "brand", "model",
            "serial_no", "year", "location", "install_date", "warranty_end", "notes",
            "external_id", "report_count", "created_at",
        ]
        read_only_fields = ["created_at"]

    def get_display_name(self, obj):
        return " ".join(p for p in (obj.brand, obj.name) if p) or obj.model or obj.serial_no


class DepartmentWorkSerializer(serializers.ModelSerializer):
    department_label = serializers.CharField(source="get_department_display", read_only=True)

    class Meta:
        model = DepartmentWork
        fields = ["id", "department", "department_label", "work"]


class ServicePhotoSerializer(serializers.ModelSerializer):
    tag_label = serializers.CharField(source="get_tag_display", read_only=True)

    class Meta:
        model = ServicePhoto
        fields = ["id", "image", "caption", "tag", "tag_label", "created_at"]
        read_only_fields = ["created_at"]


class SparePartSerializer(serializers.ModelSerializer):
    status_label = serializers.CharField(source="get_status_display", read_only=True)

    class Meta:
        model = SparePart
        fields = ["id", "name", "code", "quantity", "unit", "status", "status_label", "note"]


class ServiceReportSerializer(serializers.ModelSerializer):
    customer_name = serializers.CharField(source="customer.name", read_only=True)
    customer_email = serializers.CharField(source="customer.email", read_only=True)
    machine_name = serializers.SerializerMethodField()
    type_label = serializers.CharField(source="get_type_display", read_only=True)
    status_label = serializers.CharField(source="get_status_display", read_only=True)
    priority_label = serializers.CharField(source="get_priority_display", read_only=True)
    duration_minutes = serializers.IntegerField(read_only=True)

    departments = DepartmentWorkSerializer(many=True, read_only=True)
    photos = ServicePhotoSerializer(many=True, read_only=True)
    parts = SparePartSerializer(many=True, read_only=True)
    charge = serializers.SerializerMethodField()
    expense_total = serializers.SerializerMethodField()

    class Meta:
        model = ServiceReport
        fields = [
            "id", "report_no", "customer", "customer_name", "customer_email",
            "machine", "machine_name", "type", "type_label", "status", "status_label",
            "priority", "priority_label", "service_date", "start_time", "end_time",
            "duration_minutes", "travel_km", "fault_description", "fault_cause",
            "work_done", "recommendations", "technician", "technician_name",
            "customer_rep", "signature", "next_maintenance", "mailed_to", "mailed_at",
            "departments", "photos", "parts", "charge", "expense_total",
            "external_id", "created_at", "updated_at",
        ]
        read_only_fields = ["report_no", "mailed_to", "mailed_at", "created_at", "updated_at"]

    def get_machine_name(self, obj):
        if not obj.machine:
            return ""
        return " ".join(p for p in (obj.machine.brand, obj.machine.name) if p) or obj.machine.model

    def get_charge(self, obj):
        entry = obj.ledger_entries.filter(type="BORC").first()
        if entry is None:
            return None
        return {
            "id": entry.id,
            "amount": entry.amount,
            "currency": entry.currency,
            "rate": entry.rate,
            "try_amount": entry.try_amount,
            "due_date": entry.due_date,
        }

    def get_expense_total(self, obj):
        return sum((e.try_amount for e in obj.expenses.all()), Decimal("0"))


class ServiceReportWriteSerializer(serializers.ModelSerializer):
    """Bölümler, yedek parçalar ve servis bedeli rapor ile birlikte kaydedilir."""

    departments = DepartmentWorkSerializer(many=True, required=False)
    parts = SparePartSerializer(many=True, required=False)

    # Servis bedeli, müşterinin carisine borç hareketi olarak işlenir
    charge_amount = serializers.DecimalField(
        max_digits=12, decimal_places=2, required=False, allow_null=True, write_only=True
    )
    charge_currency = serializers.CharField(required=False, allow_blank=True, write_only=True)
    charge_rate = serializers.DecimalField(
        max_digits=10, decimal_places=4, required=False, allow_null=True, write_only=True
    )
    charge_due_date = serializers.DateField(required=False, allow_null=True, write_only=True)

    class Meta:
        model = ServiceReport
        fields = [
            "id", "customer", "machine", "type", "status", "priority", "service_date",
            "start_time", "end_time", "travel_km", "fault_description", "fault_cause",
            "work_done", "recommendations", "technician", "technician_name",
            "customer_rep", "next_maintenance", "departments", "parts", "external_id",
            "charge_amount", "charge_currency", "charge_rate", "charge_due_date",
        ]

    def create(self, validated_data):
        charge = self._pop_charge(validated_data)
        departments = validated_data.pop("departments", [])
        parts = validated_data.pop("parts", [])
        report = ServiceReport.objects.create(**validated_data)
        self._sync_children(report, departments, parts)
        self._sync_charge(report, charge)
        return report

    def update(self, instance, validated_data):
        charge = self._pop_charge(validated_data)
        departments = validated_data.pop("departments", None)
        parts = validated_data.pop("parts", None)
        for field, value in validated_data.items():
            setattr(instance, field, value)
        instance.save()
        if departments is not None:
            instance.departments.all().delete()
        if parts is not None:
            instance.parts.all().delete()
        self._sync_children(instance, departments or [], parts or [])
        self._sync_charge(instance, charge)
        return instance

    def _pop_charge(self, validated_data):
        keys = ("charge_amount", "charge_currency", "charge_rate", "charge_due_date")
        if not any(key in validated_data for key in keys):
            return None
        return {key: validated_data.pop(key, None) for key in keys}

    def _sync_charge(self, report, charge):
        """Servis bedelini rapora bağlı tek bir borç hareketi olarak tutar."""
        if charge is None:
            return
        amount = charge.get("charge_amount")
        entry = report.ledger_entries.filter(type="BORC").first()

        if not amount or amount <= 0:
            if entry:
                entry.delete()
            return

        currency = charge.get("charge_currency") or "TRY"
        rate = Decimal("1") if currency == "TRY" else (charge.get("charge_rate") or Decimal("0"))
        if rate <= 0:
            raise serializers.ValidationError(
                {"charge_rate": "Döviz cinsinden servis bedeli için kur girilmelidir."}
            )

        values = {
            "customer": report.customer,
            "type": "BORC",
            "date": report.service_date,
            "amount": amount,
            "currency": currency,
            "rate": rate,
            "due_date": charge.get("charge_due_date"),
            "description": f"Servis bedeli {report.report_no}",
        }
        if entry:
            for field, value in values.items():
                setattr(entry, field, value)
            entry.save()
        else:
            LedgerEntry.objects.create(report=report, **values)

    def _sync_children(self, report, departments, parts):
        for item in departments:
            item.pop("id", None)
            DepartmentWork.objects.create(report=report, **item)
        for item in parts:
            item.pop("id", None)
            SparePart.objects.create(report=report, **item)

    def to_representation(self, instance):
        return ServiceReportSerializer(instance, context=self.context).data


class MailSettingsSerializer(serializers.ModelSerializer):
    is_configured = serializers.BooleanField(read_only=True)
    password = serializers.CharField(
        write_only=True, required=False, allow_blank=True, style={"input_type": "password"}
    )
    has_password = serializers.SerializerMethodField()

    class Meta:
        model = MailSettings
        fields = [
            "id", "host", "port", "security", "username", "password", "has_password",
            "from_address", "from_name", "default_to", "default_cc", "subject_template",
            "attach_photos", "company_name", "company_address", "company_phone",
            "company_email", "company_web", "is_configured", "updated_at",
        ]
        read_only_fields = ["updated_at"]

    def get_has_password(self, obj):
        return bool(obj.password)

    def update(self, instance, validated_data):
        # Boş parola gönderildiğinde mevcut parola korunur
        if not validated_data.get("password"):
            validated_data.pop("password", None)
        return super().update(instance, validated_data)


class LedgerEntrySerializer(serializers.ModelSerializer):
    customer_name = serializers.CharField(source="customer.name", read_only=True)
    type_label = serializers.CharField(source="get_type_display", read_only=True)
    method_label = serializers.CharField(source="get_payment_method_display", read_only=True)
    try_amount = serializers.DecimalField(max_digits=14, decimal_places=2, read_only=True)
    report_no = serializers.CharField(source="report.report_no", read_only=True, default="")

    class Meta:
        model = LedgerEntry
        fields = [
            "id", "customer", "customer_name", "report", "report_no", "type", "type_label",
            "date", "amount", "currency", "rate", "try_amount", "description", "document_no",
            "due_date", "promised_date", "payment_method", "method_label",
            "external_id", "created_at",
        ]
        read_only_fields = ["created_at"]

    def validate(self, attrs):
        rate = attrs.get("rate", getattr(self.instance, "rate", 1))
        currency = attrs.get("currency", getattr(self.instance, "currency", "TRY"))
        if currency == "TRY":
            attrs["rate"] = 1
        elif not rate or rate <= 0:
            raise serializers.ValidationError(
                {"rate": "Döviz işlemleri için geçerli bir kur girilmelidir."}
            )
        return attrs


class ExpenseSerializer(serializers.ModelSerializer):
    category_label = serializers.CharField(source="get_category_display", read_only=True)
    customer_name = serializers.CharField(source="customer.name", read_only=True, default="")
    report_no = serializers.CharField(source="report.report_no", read_only=True, default="")
    try_amount = serializers.DecimalField(max_digits=14, decimal_places=2, read_only=True)

    class Meta:
        model = Expense
        fields = [
            "id", "report", "report_no", "customer", "customer_name", "category", "category_label",
            "date", "amount", "currency", "rate", "try_amount", "description", "quantity",
            "billable", "receipt", "external_id", "created_at",
        ]
        read_only_fields = ["created_at"]

    def validate(self, attrs):
        rate = attrs.get("rate", getattr(self.instance, "rate", 1))
        currency = attrs.get("currency", getattr(self.instance, "currency", "TRY"))
        if currency == "TRY":
            attrs["rate"] = 1
        elif not rate or rate <= 0:
            raise serializers.ValidationError(
                {"rate": "Döviz işlemleri için geçerli bir kur girilmelidir."}
            )
        return attrs


class FinanceSettingsSerializer(serializers.ModelSerializer):
    class Meta:
        model = FinanceSettings
        fields = [
            "id", "usd_rate", "eur_rate", "rates_updated_at",
            "show_charge_on_pdf", "overdue_grace_days",
        ]
        read_only_fields = ["rates_updated_at"]
