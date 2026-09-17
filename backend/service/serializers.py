from rest_framework import serializers

from .models import (
    Customer,
    DepartmentWork,
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

    class Meta:
        model = ServiceReport
        fields = [
            "id", "report_no", "customer", "customer_name", "customer_email",
            "machine", "machine_name", "type", "type_label", "status", "status_label",
            "priority", "priority_label", "service_date", "start_time", "end_time",
            "duration_minutes", "travel_km", "fault_description", "fault_cause",
            "work_done", "recommendations", "technician", "technician_name",
            "customer_rep", "signature", "next_maintenance", "mailed_to", "mailed_at",
            "departments", "photos", "parts", "external_id", "created_at", "updated_at",
        ]
        read_only_fields = ["report_no", "mailed_to", "mailed_at", "created_at", "updated_at"]

    def get_machine_name(self, obj):
        if not obj.machine:
            return ""
        return " ".join(p for p in (obj.machine.brand, obj.machine.name) if p) or obj.machine.model


class ServiceReportWriteSerializer(serializers.ModelSerializer):
    """Bölümler ve yedek parçalar rapor ile birlikte tek istekte kaydedilir."""

    departments = DepartmentWorkSerializer(many=True, required=False)
    parts = SparePartSerializer(many=True, required=False)

    class Meta:
        model = ServiceReport
        fields = [
            "id", "customer", "machine", "type", "status", "priority", "service_date",
            "start_time", "end_time", "travel_km", "fault_description", "fault_cause",
            "work_done", "recommendations", "technician", "technician_name",
            "customer_rep", "next_maintenance", "departments", "parts", "external_id",
        ]

    def create(self, validated_data):
        departments = validated_data.pop("departments", [])
        parts = validated_data.pop("parts", [])
        report = ServiceReport.objects.create(**validated_data)
        self._sync_children(report, departments, parts)
        return report

    def update(self, instance, validated_data):
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
        return instance

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
