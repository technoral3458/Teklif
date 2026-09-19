from django.test import TestCase
from django.utils import timezone

from .models import Customer, Machine, MailSettings, ServiceReport, SparePart
from .pdf import build_report_pdf


class ReportNumberTests(TestCase):
    def setUp(self):
        self.customer = Customer.objects.create(name="Test Fabrika")
        self.machine = Machine.objects.create(
            customer=self.customer, name="Pres", brand="Marka", serial_no="SN-1"
        )

    def test_report_no_is_generated_and_incremented(self):
        first = ServiceReport.objects.create(
            customer=self.customer, machine=self.machine, service_date=timezone.localdate()
        )
        second = ServiceReport.objects.create(
            customer=self.customer, machine=self.machine, service_date=timezone.localdate()
        )
        period = timezone.localdate().strftime("%Y%m")
        self.assertEqual(first.report_no, f"SRV-{period}-0001")
        self.assertEqual(second.report_no, f"SRV-{period}-0002")

    def test_duration_minutes(self):
        report = ServiceReport.objects.create(
            customer=self.customer,
            service_date=timezone.localdate(),
            start_time="09:30",
            end_time="12:15",
        )
        report.refresh_from_db()
        self.assertEqual(report.duration_minutes, 165)

    def test_pdf_is_produced(self):
        report = ServiceReport.objects.create(
            customer=self.customer,
            machine=self.machine,
            service_date=timezone.localdate(),
            fault_description="Şanzıman ısınıyor, çığlık sesi var.",
            work_done="Rulman değişimi yapıldı, yağ seviyesi tamamlandı.",
            technician_name="Ahmet Yılmaz",
        )
        SparePart.objects.create(report=report, name="Rulman 6204", code="R-6204", quantity=2)
        pdf = build_report_pdf(report, MailSettings.load())
        self.assertIsNotNone(pdf)
        self.assertTrue(pdf.startswith(b"%PDF"))


class MailSettingsTests(TestCase):
    def test_load_creates_singleton(self):
        first = MailSettings.load()
        second = MailSettings.load()
        self.assertEqual(first.pk, second.pk)
        self.assertFalse(first.is_configured)


class DurationTests(TestCase):
    """Çalışma süresi hesabı."""

    def setUp(self):
        self.customer = Customer.objects.create(name="Süre Test")

    def _report(self, start, end):
        report = ServiceReport.objects.create(
            customer=self.customer, service_date=timezone.localdate(),
            start_time=start, end_time=end,
        )
        # Saat alanları veritabanından okunurken time nesnesine dönüşüyor
        report.refresh_from_db()
        return report

    def test_same_day(self):
        self.assertEqual(self._report("08:49", "15:50").duration_minutes, 421)

    def test_overnight(self):
        """22:30 - 02:15 arası gece vardiyası: 3 sa 45 dk."""
        self.assertEqual(self._report("22:30", "02:15").duration_minutes, 225)

    def test_equal_times_is_none(self):
        self.assertIsNone(self._report("09:00", "09:00").duration_minutes)

    def test_missing_time_is_none(self):
        self.assertIsNone(self._report("09:00", None).duration_minutes)
