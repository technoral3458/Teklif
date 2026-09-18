from decimal import Decimal

from django.db.models import Count, Q
from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.decorators import api_view, parser_classes, permission_classes
from rest_framework.parsers import FormParser, JSONParser, MultiPartParser
from rest_framework.response import Response

from . import finance, rates
from .mailer import send_finance_mail, send_report_mail, send_test_mail
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
from .pdf import build_finance_pdf, build_report_pdf
from .serializers import (
    CustomerSerializer,
    ExpenseSerializer,
    FinanceSettingsSerializer,
    LedgerEntrySerializer,
    MachineSerializer,
    MailSettingsSerializer,
    ServicePhotoSerializer,
    ServiceReportSerializer,
    ServiceReportWriteSerializer,
)


class CanManageService(permissions.BasePermission):
    """Servis kayıtlarını yönetici, satış ekibi ve servis teknisyenleri düzenleyebilir."""

    def has_permission(self, request, view):
        user = request.user
        if not user.is_authenticated:
            return False
        if request.method in permissions.SAFE_METHODS:
            return True
        return user.role in ("admin", "sales", "service")


class CustomerListCreateView(generics.ListCreateAPIView):
    serializer_class = CustomerSerializer
    permission_classes = [CanManageService]

    def get_queryset(self):
        qs = Customer.objects.all()
        search = self.request.query_params.get("search")
        if search:
            qs = qs.filter(
                Q(name__icontains=search)
                | Q(contact_name__icontains=search)
                | Q(phone__icontains=search)
                | Q(city__icontains=search)
            )
        return qs


class CustomerDetailView(generics.RetrieveUpdateDestroyAPIView):
    queryset = Customer.objects.all()
    serializer_class = CustomerSerializer
    permission_classes = [CanManageService]


class MachineListCreateView(generics.ListCreateAPIView):
    serializer_class = MachineSerializer
    permission_classes = [CanManageService]

    def get_queryset(self):
        qs = Machine.objects.select_related("customer")
        customer = self.request.query_params.get("customer")
        if customer:
            qs = qs.filter(customer_id=customer)
        search = self.request.query_params.get("search")
        if search:
            qs = qs.filter(
                Q(name__icontains=search)
                | Q(brand__icontains=search)
                | Q(model__icontains=search)
                | Q(serial_no__icontains=search)
                | Q(customer__name__icontains=search)
            )
        return qs


class MachineDetailView(generics.RetrieveUpdateDestroyAPIView):
    queryset = Machine.objects.select_related("customer")
    serializer_class = MachineSerializer
    permission_classes = [CanManageService]


class ServiceReportListCreateView(generics.ListCreateAPIView):
    permission_classes = [CanManageService]

    def get_queryset(self):
        qs = (
            ServiceReport.objects.select_related("customer", "machine")
            .prefetch_related("departments", "parts", "photos")
        )
        params = self.request.query_params
        if params.get("status"):
            qs = qs.filter(status=params["status"])
        if params.get("customer"):
            qs = qs.filter(customer_id=params["customer"])
        if params.get("machine"):
            qs = qs.filter(machine_id=params["machine"])
        search = params.get("search")
        if search:
            qs = qs.filter(
                Q(report_no__icontains=search)
                | Q(customer__name__icontains=search)
                | Q(machine__serial_no__icontains=search)
                | Q(fault_description__icontains=search)
                | Q(work_done__icontains=search)
            )
        return qs

    def get_serializer_class(self):
        return ServiceReportWriteSerializer if self.request.method == "POST" else ServiceReportSerializer

    def perform_create(self, serializer):
        user = self.request.user
        serializer.save(
            technician=serializer.validated_data.get("technician") or user,
            technician_name=serializer.validated_data.get("technician_name")
            or user.get_full_name()
            or user.username,
        )


class ServiceReportDetailView(generics.RetrieveUpdateDestroyAPIView):
    permission_classes = [CanManageService]
    queryset = (
        ServiceReport.objects.select_related("customer", "machine")
        .prefetch_related("departments", "parts", "photos")
    )

    def get_serializer_class(self):
        if self.request.method in ("PUT", "PATCH"):
            return ServiceReportWriteSerializer
        return ServiceReportSerializer


@api_view(["POST"])
@permission_classes([CanManageService])
@parser_classes([MultiPartParser, FormParser])
def upload_photo(request, pk):
    try:
        report = ServiceReport.objects.get(pk=pk)
    except ServiceReport.DoesNotExist:
        return Response({"error": "Rapor bulunamadı"}, status=404)

    image = request.FILES.get("image")
    if not image:
        return Response({"error": "Fotoğraf dosyası gönderilmedi"}, status=400)

    photo = ServicePhoto.objects.create(
        report=report,
        image=image,
        caption=request.data.get("caption", ""),
        tag=request.data.get("tag", "ARIZA"),
    )
    return Response(ServicePhotoSerializer(photo, context={"request": request}).data, status=201)


@api_view(["PATCH", "DELETE"])
@permission_classes([CanManageService])
def photo_detail(request, pk, photo_id):
    photo = ServicePhoto.objects.filter(report_id=pk, id=photo_id).first()
    if photo is None:
        return Response({"error": "Fotoğraf bulunamadı"}, status=404)

    if request.method == "DELETE":
        photo.delete()
        return Response(status=204)

    for field in ("caption", "tag"):
        if field in request.data:
            setattr(photo, field, request.data[field])
    photo.save(update_fields=["caption", "tag"])
    return Response(ServicePhotoSerializer(photo, context={"request": request}).data)


@api_view(["POST"])
@permission_classes([CanManageService])
@parser_classes([MultiPartParser, FormParser])
def upload_signature(request, pk):
    try:
        report = ServiceReport.objects.get(pk=pk)
    except ServiceReport.DoesNotExist:
        return Response({"error": "Rapor bulunamadı"}, status=404)

    image = request.FILES.get("image")
    if not image:
        return Response({"error": "İmza görseli gönderilmedi"}, status=400)

    report.signature = image
    report.customer_rep = request.data.get("customer_rep", report.customer_rep)
    report.save(update_fields=["signature", "customer_rep"])
    return Response(ServiceReportSerializer(report, context={"request": request}).data)


@api_view(["GET"])
@permission_classes([CanManageService])
def report_pdf(request, pk):
    from django.http import HttpResponse

    try:
        report = (
            ServiceReport.objects.select_related("customer", "machine")
            .prefetch_related("departments", "parts", "photos")
            .get(pk=pk)
        )
    except ServiceReport.DoesNotExist:
        return Response({"error": "Rapor bulunamadı"}, status=404)

    pdf_bytes = build_report_pdf(report, MailSettings.load())
    if pdf_bytes is None:
        return Response(
            {"error": "PDF üretimi için reportlab kurulu değil (pip install reportlab)."},
            status=501,
        )
    response = HttpResponse(pdf_bytes, content_type="application/pdf")
    response["Content-Disposition"] = f'inline; filename="{report.report_no}.pdf"'
    return response


@api_view(["POST"])
@permission_classes([CanManageService])
def send_mail_view(request, pk):
    try:
        report = (
            ServiceReport.objects.select_related("customer", "machine")
            .prefetch_related("departments", "parts", "photos")
            .get(pk=pk)
        )
    except ServiceReport.DoesNotExist:
        return Response({"error": "Rapor bulunamadı"}, status=404)

    settings_obj = MailSettings.load()
    to = request.data.get("to") or ", ".join(
        filter(None, [report.customer.email, settings_obj.default_to])
    )
    ok, message = send_report_mail(
        report=report,
        to=to,
        cc=request.data.get("cc", ""),
        note=request.data.get("note", ""),
        attach_photos=request.data.get("attach_photos"),
    )
    if not ok:
        return Response({"error": message}, status=status.HTTP_400_BAD_REQUEST)
    return Response({"detail": message, "mailed_at": report.mailed_at, "mailed_to": report.mailed_to})


@api_view(["GET", "PUT", "PATCH"])
@permission_classes([permissions.IsAuthenticated])
def mail_settings_view(request):
    settings_obj = MailSettings.load()
    if request.method == "GET":
        return Response(MailSettingsSerializer(settings_obj).data)

    if request.user.role not in ("admin", "sales"):
        return Response({"error": "Bu ayarları yalnızca yönetici değiştirebilir."}, status=403)

    serializer = MailSettingsSerializer(settings_obj, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save()
    return Response(serializer.data)


@api_view(["POST"])
@permission_classes([permissions.IsAuthenticated])
def test_mail_view(request):
    to = request.data.get("to") or request.user.email
    ok, message = send_test_mail(to)
    if not ok:
        return Response({"error": message}, status=400)
    return Response({"detail": message})


@api_view(["GET"])
@permission_classes([permissions.IsAuthenticated])
def service_stats(request):
    today = timezone.localdate()
    month_start = today.replace(day=1)
    reports = ServiceReport.objects.all()

    upcoming = (
        reports.filter(next_maintenance__isnull=False, next_maintenance__lte=today + timezone.timedelta(days=45))
        .select_related("customer", "machine")
        .order_by("next_maintenance")[:10]
    )

    top_machines = (
        Machine.objects.annotate(report_count=Count("reports"))
        .filter(report_count__gt=0)
        .order_by("-report_count")[:5]
    )

    return Response({
        "monthly_count": reports.filter(service_date__gte=month_start).count(),
        "open_count": reports.filter(status__in=["TASLAK", "ACIK"]).count(),
        "waiting_parts": reports.filter(
            Q(status="PARCA_BEKLIYOR") | Q(parts__status__in=["GEREKLI", "SIPARIS", "TEKLIF"])
        ).distinct().count(),
        "machine_count": Machine.objects.count(),
        "customer_count": Customer.objects.count(),
        "upcoming_maintenance": [
            {
                "report_no": r.report_no,
                "customer": r.customer.name,
                "machine": (" ".join(p for p in (r.machine.brand, r.machine.name) if p)) if r.machine else "",
                "date": r.next_maintenance,
            }
            for r in upcoming
        ],
        "top_machines": [
            {
                "id": m.id,
                "name": " ".join(p for p in (m.brand, m.name) if p) or m.model,
                "serial_no": m.serial_no,
                "customer": m.customer.name,
                "report_count": m.report_count,
            }
            for m in top_machines
        ],
    })


@api_view(["POST"])
@permission_classes([CanManageService])
def sync_from_mobile(request):
    """Mobil uygulamanın ürettiği yedek JSON'unu sunucuya aktarır.

    Kayıtlar `external_id` üzerinden eşlenir; aynı kayıt tekrar gönderildiğinde
    yenisi oluşturulmaz, mevcut kayıt güncellenir.
    """
    payload = request.data
    customer_map = {}
    machine_map = {}
    created = {"customers": 0, "machines": 0, "reports": 0}

    for item in payload.get("musteriler", []):
        obj, is_new = Customer.objects.update_or_create(
            external_id=item.get("id", ""),
            defaults={
                "name": item.get("name", ""),
                "contact_name": item.get("contactName", ""),
                "phone": item.get("phone", ""),
                "email": item.get("email", ""),
                "address": item.get("address", ""),
                "city": item.get("city", ""),
                "notes": item.get("notes", ""),
            },
        )
        customer_map[item.get("id")] = obj
        created["customers"] += int(is_new)

    for item in payload.get("makineler", []):
        customer = customer_map.get(item.get("customerId"))
        if customer is None:
            customer = Customer.objects.filter(external_id=item.get("customerId", "")).first()
        if customer is None:
            continue
        obj, is_new = Machine.objects.update_or_create(
            external_id=item.get("id", ""),
            defaults={
                "customer": customer,
                "name": item.get("name", ""),
                "brand": item.get("brand", ""),
                "model": item.get("model", ""),
                "serial_no": item.get("serialNo", ""),
                "year": item.get("year", ""),
                "location": item.get("location", ""),
                "install_date": _as_date(item.get("installDate")),
                "warranty_end": _as_date(item.get("warrantyEnd")),
                "notes": item.get("notes", ""),
            },
        )
        machine_map[item.get("id")] = obj
        created["machines"] += int(is_new)

    for item in payload.get("raporlar", []):
        customer = customer_map.get(item.get("customerId")) or Customer.objects.filter(
            external_id=item.get("customerId", "")
        ).first()
        if customer is None:
            continue
        machine = machine_map.get(item.get("machineId")) or Machine.objects.filter(
            external_id=item.get("machineId", "")
        ).first()

        report, is_new = ServiceReport.objects.update_or_create(
            external_id=item.get("id", ""),
            defaults={
                "report_no": item.get("reportNo") or ServiceReport.build_report_no(),
                "customer": customer,
                "machine": machine,
                "type": item.get("type", "ARIZA"),
                "status": item.get("status", "ACIK"),
                "priority": item.get("priority", "NORMAL"),
                "service_date": _as_date(item.get("serviceDate")) or timezone.localdate(),
                "start_time": _as_time(item.get("startTime")),
                "end_time": _as_time(item.get("endTime")),
                "travel_km": item.get("travelKm") or 0,
                "fault_description": item.get("faultDescription", ""),
                "fault_cause": item.get("faultCause", ""),
                "work_done": item.get("workDone", ""),
                "recommendations": item.get("recommendations", ""),
                "technician_name": item.get("technician", ""),
                "customer_rep": item.get("customerRep", ""),
                "next_maintenance": _as_date(item.get("nextMaintenance")),
            },
        )
        created["reports"] += int(is_new)

        report.departments.all().delete()
        for d in item.get("departments", []):
            DepartmentWork.objects.create(
                report=report, department=d.get("department", "DIGER"), work=d.get("work", "")
            )
        report.parts.all().delete()
        for p in item.get("parts", []):
            SparePart.objects.create(
                report=report,
                name=p.get("name", ""),
                code=p.get("code", ""),
                quantity=p.get("quantity") or 1,
                unit=p.get("unit", "adet"),
                status=p.get("status", "TAKILDI"),
                note=p.get("note", ""),
            )

    return Response({"detail": "Aktarım tamamlandı", "created": created})


def _as_date(millis):
    if not millis:
        return None
    return timezone.datetime.fromtimestamp(int(millis) / 1000, tz=timezone.get_current_timezone()).date()


def _as_time(millis):
    if not millis:
        return None
    return timezone.datetime.fromtimestamp(int(millis) / 1000, tz=timezone.get_current_timezone()).time()


# ---------------------------------------------------------------- Cari / Finans


class LedgerEntryListCreateView(generics.ListCreateAPIView):
    serializer_class = LedgerEntrySerializer
    permission_classes = [CanManageService]

    def get_queryset(self):
        qs = LedgerEntry.objects.select_related("customer", "report")
        params = self.request.query_params
        if params.get("customer"):
            qs = qs.filter(customer_id=params["customer"])
        if params.get("type"):
            qs = qs.filter(type=params["type"])
        if params.get("from"):
            qs = qs.filter(date__gte=params["from"])
        if params.get("to"):
            qs = qs.filter(date__lte=params["to"])
        return qs


class LedgerEntryDetailView(generics.RetrieveUpdateDestroyAPIView):
    queryset = LedgerEntry.objects.select_related("customer", "report")
    serializer_class = LedgerEntrySerializer
    permission_classes = [CanManageService]


class ExpenseListCreateView(generics.ListCreateAPIView):
    serializer_class = ExpenseSerializer
    permission_classes = [CanManageService]
    parser_classes = [MultiPartParser, FormParser, JSONParser]

    def get_queryset(self):
        qs = Expense.objects.select_related("customer", "report")
        params = self.request.query_params
        if params.get("report"):
            qs = qs.filter(report_id=params["report"])
        if params.get("customer"):
            qs = qs.filter(customer_id=params["customer"])
        if params.get("category"):
            qs = qs.filter(category=params["category"])
        if params.get("from"):
            qs = qs.filter(date__gte=params["from"])
        if params.get("to"):
            qs = qs.filter(date__lte=params["to"])
        return qs


class ExpenseDetailView(generics.RetrieveUpdateDestroyAPIView):
    queryset = Expense.objects.select_related("customer", "report")
    serializer_class = ExpenseSerializer
    permission_classes = [CanManageService]
    parser_classes = [MultiPartParser, FormParser, JSONParser]


def _account_payload(account):
    return {
        "customer_id": account["customer"].id,
        "customer": account["customer"].name,
        "debit_try": account["debit_try"],
        "credit_try": account["credit_try"],
        "balance_try": account["balance_try"],
        "overdue_try": account["overdue_try"],
        "has_overdue": account["has_overdue"],
        "last_activity": account["last_activity"],
        "open_debts": [
            {
                "entry_id": debt["entry"].id,
                "date": debt["entry"].date,
                "description": debt["entry"].description,
                "open_try": debt["open_try"],
                "deadline": debt["deadline"],
                "is_overdue": debt["is_overdue"],
                "broken_promise": debt["broken_promise"],
                "days_late": debt["days_late"],
            }
            for debt in account["open_debts"]
        ],
    }


@api_view(["GET"])
@permission_classes([CanManageService])
def accounts_view(request):
    accounts = finance.all_accounts()
    accounts.sort(key=lambda a: a["balance_try"], reverse=True)
    return Response({
        "total_receivable": sum(
            (a["balance_try"] for a in accounts if a["balance_try"] > 0), Decimal("0")
        ),
        "total_overdue": sum((a["overdue_try"] for a in accounts), Decimal("0")),
        "accounts": [_account_payload(a) for a in accounts],
    })


@api_view(["GET"])
@permission_classes([CanManageService])
def customer_account_view(request, pk):
    try:
        customer = Customer.objects.get(pk=pk)
    except Customer.DoesNotExist:
        return Response({"error": "Müşteri bulunamadı"}, status=404)

    account = finance.customer_account(
        customer, grace_days=FinanceSettings.load().overdue_grace_days
    )
    entries = customer.ledger_entries.select_related("report").all()
    return Response({
        **_account_payload(account),
        "entries": LedgerEntrySerializer(entries, many=True).data,
    })


@api_view(["GET"])
@permission_classes([CanManageService])
def overdue_view(request):
    rows = finance.overdue_list()
    return Response({
        "total": sum((row["open_try"] for row in rows), Decimal("0")),
        "count": len(rows),
        "items": [
            {
                "customer_id": row["customer"].id,
                "customer": row["customer"].name,
                "phone": row["customer"].phone,
                "email": row["customer"].email,
                "description": row["entry"].description,
                "document_no": row["entry"].document_no,
                "date": row["entry"].date,
                "deadline": row["deadline"],
                "broken_promise": row["broken_promise"],
                "days_late": row["days_late"],
                "open_try": row["open_try"],
            }
            for row in rows
        ],
    })


def _resolve_period(request):
    today = timezone.localdate()
    year = int(request.query_params.get("year", today.year))
    month = int(request.query_params.get("month", today.month))
    return year, month


@api_view(["GET"])
@permission_classes([CanManageService])
def monthly_report_view(request):
    year, month = _resolve_period(request)
    return Response(finance.monthly_summary(year, month))


@api_view(["GET"])
@permission_classes([CanManageService])
def monthly_report_pdf(request):
    from django.http import HttpResponse

    year, month = _resolve_period(request)
    summary = finance.monthly_summary(year, month)
    pdf_bytes = build_finance_pdf(summary, MailSettings.load(), finance.overdue_list(), finance.all_accounts())
    if pdf_bytes is None:
        return Response({"error": "PDF üretimi için reportlab kurulu değil."}, status=501)
    response = HttpResponse(pdf_bytes, content_type="application/pdf")
    response["Content-Disposition"] = f'inline; filename="finans-{year}-{month:02d}.pdf"'
    return response


@api_view(["POST"])
@permission_classes([CanManageService])
def monthly_report_mail(request):
    year, month = _resolve_period(request)
    summary = finance.monthly_summary(year, month)
    ok, message = send_finance_mail(
        summary=summary,
        to=request.data.get("to", ""),
        note=request.data.get("note", ""),
        overdue=finance.overdue_list(),
        accounts=finance.all_accounts(),
    )
    if not ok:
        return Response({"error": message}, status=400)
    return Response({"detail": message})


@api_view(["POST"])
@permission_classes([CanManageService])
def refresh_rates_view(request):
    """Güncel kuru TCMB'den (ulaşılamazsa ECB'den) alıp ayarlara yazar."""
    quote, error = rates.fetch_rates()
    if quote is None:
        return Response({"error": error}, status=502)

    settings_obj = FinanceSettings.load()
    settings_obj.usd_rate = quote["usd"]
    settings_obj.eur_rate = quote["eur"]
    settings_obj.rate_source = quote["source"]
    settings_obj.rate_date_label = quote["date"]
    settings_obj.rates_updated_at = timezone.now()
    settings_obj.save()

    return Response({
        "detail": f"Kur güncellendi ({quote['source']} • {quote['date']}).",
        **FinanceSettingsSerializer(settings_obj).data,
    })


@api_view(["GET", "PUT", "PATCH"])
@permission_classes([permissions.IsAuthenticated])
def finance_settings_view(request):
    settings_obj = FinanceSettings.load()
    if request.method == "GET":
        return Response(FinanceSettingsSerializer(settings_obj).data)

    if request.user.role not in ("admin", "sales"):
        return Response({"error": "Bu ayarları yalnızca yönetici değiştirebilir."}, status=403)

    serializer = FinanceSettingsSerializer(settings_obj, data=request.data, partial=True)
    serializer.is_valid(raise_exception=True)
    serializer.save(rates_updated_at=timezone.now())
    return Response(serializer.data)
