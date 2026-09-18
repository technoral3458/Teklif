"""Cari, masraf ve aylık rapor uçlarının uçtan uca testi."""
import datetime
from decimal import Decimal

from django.test import TestCase
from rest_framework.test import APIClient

from accounts.models import User
from . import rates
from .models import Customer, Expense, FinanceSettings, LedgerEntry, Machine


class FinanceApiTest(TestCase):
    def setUp(self):
        self.user = User.objects.create_user("mali", password="x", role="admin")
        self.client_api = APIClient()
        self.client_api.force_authenticate(self.user)
        self.today = datetime.date.today()

    def api(self, method, url, data=None):
        fn = getattr(self.client_api, method)
        return fn(url, data, format="json") if data is not None else fn(url)

    def test_flow(self):
        # Müşteri + makine
        c1 = Customer.objects.create(name="Öz Kardeşler Plastik", email="a@b.com")
        c2 = Customer.objects.create(name="Demir Metal")
        m1 = Machine.objects.create(customer=c1, name="Enjeksiyon", serial_no="HT-1")

        # Servis raporu + dolar cinsinden servis bedeli
        r = self.api("post", "/api/service/reports/", {
            "customer": c1.id, "machine": m1.id, "type": "ARIZA", "status": "COZULDU",
            "service_date": str(self.today - datetime.timedelta(days=40)),
            "work_done": "Valf değişimi",
            "charge_amount": "500.00", "charge_currency": "USD", "charge_rate": "40.0000",
            "charge_due_date": str(self.today - datetime.timedelta(days=10)),
        })
        assert r.status_code == 201, r.content
        report_id = r.data["id"]
        assert r.data["charge"]["try_amount"] == Decimal("20000.0000"), r.data["charge"]
        print("  servis bedeli:", r.data["charge"]["amount"], r.data["charge"]["currency"],
              "→", r.data["charge"]["try_amount"], "TL")

        # Servise ait masraflar
        for category, amount, qty in (("YAKIT", "1500.00", "45"), ("KONAKLAMA", "2200.00", "2"),
                                      ("YEMEK", "450.00", "0"), ("YOL", "320.00", "0")):
            res = self.api("post", "/api/service/expenses/", {
                "report": report_id, "customer": c1.id, "category": category,
                "date": str(self.today), "amount": amount, "currency": "TRY",
                "quantity": qty, "description": f"{category} gideri",
            })
            assert res.status_code == 201, res.content

        # İkinci müşteriye TL hakediş + kısmi tahsilat
        self.api("post", "/api/service/ledger/", {
            "customer": c2.id, "type": "BORC", "date": str(self.today - datetime.timedelta(days=5)),
            "amount": "10000.00", "currency": "TRY", "description": "Bakım bedeli",
            "due_date": str(self.today + datetime.timedelta(days=15)),
        })
        self.api("post", "/api/service/ledger/", {
            "customer": c2.id, "type": "TAHSILAT", "date": str(self.today),
            "amount": "4000.00", "currency": "TRY", "payment_method": "HAVALE",
        })

        # Hesaplar
        res = self.api("get", "/api/service/accounts/")
        assert res.status_code == 200, res.content
        balances = {a["customer"]: a["balance_try"] for a in res.data["accounts"]}
        # 500 USD × 40 = 20.000 servis bedeli + 4.470 yansıtılan masraf
        assert balances["Öz Kardeşler Plastik"] == Decimal("24470.0000"), balances
        assert balances["Demir Metal"] == Decimal("6000.0000"), balances
        print("  bakiyeler:", {k: float(v) for k, v in balances.items()})
        print("  toplam alacak:", float(res.data["total_receivable"]),
              "| gecikmiş:", float(res.data["total_overdue"]))

        # Vadesi geçen: servis bedeli ve yansıtılan masraf kalemi, 10 gün önce doldu
        res = self.api("get", "/api/service/overdue/")
        assert res.data["count"] == 2, res.data
        assert Decimal(str(res.data["total"])) == Decimal("24470"), res.data["total"]
        assert all(i["customer"] == "Öz Kardeşler Plastik" for i in res.data["items"])
        assert res.data["items"][0]["days_late"] == 10, res.data["items"][0]
        print("  gecikmiş:", res.data["count"], "kalem,", float(res.data["total"]), "TL")

        # Söz verilen tarih geçince ayrıca işaretlenmeli
        entry = LedgerEntry.objects.get(report_id=report_id, kind="SERVIS")
        entry.promised_date = self.today - datetime.timedelta(days=3)
        entry.save()
        res = self.api("get", "/api/service/overdue/")
        # Liste en gecikmişten başlar; servis bedeli kalemini açıklamasından bul
        service_item = next(
            i for i in res.data["items"] if i["description"].startswith("Servis bedeli")
        )
        assert service_item["broken_promise"] is True, service_item
        assert service_item["days_late"] == 3, service_item
        print("  söz verilen tarih geçti:", service_item["days_late"], "gün")

        # Kısmi tahsilat sonrası açık borç
        self.api("post", "/api/service/ledger/", {
            "customer": c1.id, "type": "TAHSILAT", "date": str(self.today),
            "amount": "12000.00", "currency": "TRY",
        })
        res = self.api("get", f"/api/service/accounts/{c1.id}/")
        # 24.470 borç - 12.000 tahsilat: servis bedelinden 8.000 + masraf 4.470 açık
        assert res.data["balance_try"] == Decimal("12470.0000"), res.data["balance_try"]
        assert len(res.data["open_debts"]) == 2, res.data["open_debts"]
        assert res.data["open_debts"][0]["open_try"] == Decimal("8000.0000")
        assert res.data["open_debts"][1]["open_try"] == Decimal("4470.0000")
        print("  kısmi tahsilat sonrası açık:",
              [float(d["open_try"]) for d in res.data["open_debts"]])

        # Aylık rapor (bu ay: tahsilatlar + masraflar)
        res = self.api("get", f"/api/service/monthly-report/?year={self.today.year}&month={self.today.month}")
        assert res.status_code == 200, res.content
        summary = res.data
        assert summary["expense_try"] == Decimal("4470.00"), summary["expense_try"]
        assert summary["fuel_liters"] == Decimal("45.00"), summary["fuel_liters"]
        assert summary["collected_try"] == Decimal("16000.0000"), summary["collected_try"]
        # Bu ayki hakediş yalnızca ikinci müşterinin bakım bedeli; ilk raporun
        # servis bedeli ve yansıtılan masrafı 40 gün önceye (geçen aya) tarihli.
        assert summary["income_try"] == Decimal("10000.0000"), summary["income_try"]
        print("  aylık:", summary["label"],
              "| tahsilat:", float(summary["collected_try"]),
              "| masraf:", float(summary["expense_try"]),
              "| yakıt:", float(summary["fuel_liters"]), "lt")
        print("  masraf dağılımı:", [(r["label"], float(r["amount"])) for r in summary["expense_by_category"]])

        # Rapor detayında ücret ve masraf toplamı
        res = self.api("get", f"/api/service/reports/{report_id}/")
        assert res.data["expense_total"] == Decimal("4470.00"), res.data["expense_total"]
        assert Decimal(res.data["customer_total"]) == Decimal("24470.00"), res.data["customer_total"]
        print("  müşteriye toplam:", float(Decimal(res.data["customer_total"])),
              "| masraf:", float(res.data["expense_total"]),
              "| servis kârı:", float(Decimal(res.data["customer_total"]) - res.data["expense_total"]))

        # PDF
        res = self.api("get", f"/api/service/monthly-report/pdf/?year={self.today.year}&month={self.today.month}")
        assert res.status_code == 200 and res["Content-Type"] == "application/pdf"
        print("  aylık rapor PDF:", len(res.content), "bayt")

        # Kur ayarları
        res = self.api("put", "/api/service/finance-settings/", {"usd_rate": "41.5", "eur_rate": "45.2"})
        assert res.status_code == 200, res.content
        assert FinanceSettings.load().usd_rate == Decimal("41.5000")

        # Kur gönderilmezse ayarlardaki güncel kur uygulanır (45,2)
        res = self.api("post", "/api/service/ledger/", {
            "customer": c2.id, "type": "BORC", "date": str(self.today),
            "amount": "100.00", "currency": "EUR",
        })
        assert res.status_code == 201, res.data
        assert Decimal(res.data["try_amount"]) == Decimal("4520"), res.data["try_amount"]
        print("  kursuz EUR girişi ayardaki kurla tamamlandı:", res.data["try_amount"], "TL")

        # Ayarlarda da kur yoksa kayıt reddedilir (500 EUR = 500 TL olmasın)
        FinanceSettings.objects.all().update(usd_rate=0, eur_rate=0)
        res = self.api("post", "/api/service/ledger/", {
            "customer": c2.id, "type": "BORC", "date": str(self.today),
            "amount": "100.00", "currency": "EUR",
        })
        assert res.status_code == 400, res.data
        print("  kursuz döviz girişi reddedildi ✔")
        print("CARİ UÇLARI ÇALIŞIYOR")


class GracePeriodTest(TestCase):
    """Gecikme toleransı: vade geçtikten sonra kaç gün beklenmesi gerektiği."""

    def setUp(self):
        self.user = User.objects.create_user("mali2", password="x", role="admin")
        self.client_api = APIClient()
        self.client_api.force_authenticate(self.user)
        self.today = datetime.date.today()
        self.customer = Customer.objects.create(name="Test Firma")
        LedgerEntry.objects.create(
            customer=self.customer, type="BORC",
            date=self.today - datetime.timedelta(days=30),
            amount="5000.00", currency="TRY", rate=1,
            due_date=self.today - datetime.timedelta(days=3),
        )

    def test_grace_period_suppresses_warning(self):
        settings_obj = FinanceSettings.load()
        settings_obj.overdue_grace_days = 0
        settings_obj.save()
        res = self.client_api.get("/api/service/overdue/")
        self.assertEqual(res.data["count"], 1)
        self.assertEqual(res.data["items"][0]["days_late"], 3)

        settings_obj.overdue_grace_days = 5
        settings_obj.save()
        res = self.client_api.get("/api/service/overdue/")
        self.assertEqual(res.data["count"], 0, "5 günlük tolerans içinde uyarı verilmemeli")

        settings_obj.overdue_grace_days = 2
        settings_obj.save()
        res = self.client_api.get("/api/service/overdue/")
        self.assertEqual(res.data["count"], 1, "tolerans aşılınca yeniden uyarılmalı")


class CurrencyRateTest(TestCase):
    """Döviz bedelinin TL'ye doğru çevrilmesi ve kursuz kaydın engellenmesi."""

    def setUp(self):
        self.user = User.objects.create_user("kur", password="x", role="admin")
        self.client_api = APIClient()
        self.client_api.force_authenticate(self.user)
        self.today = datetime.date.today()
        self.customer = Customer.objects.create(name="Avrupa Makine")

    def _report(self, **charge):
        payload = {
            "customer": self.customer.id, "type": "ARIZA", "status": "COZULDU",
            "service_date": str(self.today), "work_done": "Devreye alma",
        }
        payload.update(charge)
        return self.client_api.post("/api/service/reports/", payload, format="json")

    def test_euro_charge_without_rate_is_rejected(self):
        """Kur yoksa 500 EUR sessizce 500 TL olarak kaydedilmemeli."""
        FinanceSettings.objects.all().delete()
        res = self._report(charge_amount="500.00", charge_currency="EUR")
        self.assertEqual(res.status_code, 400, res.data)
        self.assertIn("charge_rate", res.data)
        self.assertEqual(LedgerEntry.objects.count(), 0)

    def test_euro_charge_uses_settings_rate_when_omitted(self):
        """Kur gönderilmezse cari ayarlarındaki güncel kur kullanılır."""
        settings_obj = FinanceSettings.load()
        settings_obj.eur_rate = Decimal("48.4321")
        settings_obj.save()

        res = self._report(charge_amount="500.00", charge_currency="EUR")
        self.assertEqual(res.status_code, 201, res.data)
        entry = LedgerEntry.objects.get()
        self.assertEqual(entry.rate, Decimal("48.4321"))
        self.assertEqual(entry.try_amount, Decimal("24216.0500"))

    def test_profit_with_euro_income_and_lira_expenses(self):
        """EUR hakediş + TL masraf: masraflar müşteriye yansıtılınca kâr servis bedeli kadar."""
        settings_obj = FinanceSettings.load()
        settings_obj.eur_rate = Decimal("48.00")
        settings_obj.save()

        res = self._report(charge_amount="500.00", charge_currency="EUR")
        report_id = res.data["id"]

        for amount in ("1500.00", "2200.00"):
            self.client_api.post("/api/service/expenses/", {
                "report": report_id, "category": "YAKIT", "date": str(self.today),
                "amount": amount, "currency": "TRY",
            }, format="json")

        detail = self.client_api.get(f"/api/service/reports/{report_id}/").data
        self.assertEqual(detail["charge"]["try_amount"], Decimal("24000.0000"))
        self.assertEqual(detail["expense_total"], Decimal("3700.00"))
        # Masraflar müşteriye yansıtıldığı için toplam borç bedelin üstüne eklenir
        self.assertEqual(Decimal(detail["customer_total"]), Decimal("27700.00"))
        profit = Decimal(detail["customer_total"]) - detail["expense_total"]
        self.assertEqual(profit, Decimal("24000.00"), "yansıtılan masraf kârı düşürmemeli")

        summary = self.client_api.get(
            f"/api/service/monthly-report/?year={self.today.year}&month={self.today.month}"
        ).data
        self.assertEqual(summary["income_try"], Decimal("27700.0000"))
        self.assertEqual(summary["expense_try"], Decimal("3700.00"))
        self.assertEqual(summary["net_try"], Decimal("24000.0000"))

    def test_ledger_entry_without_rate_falls_back_to_settings(self):
        settings_obj = FinanceSettings.load()
        settings_obj.usd_rate = Decimal("41.20")
        settings_obj.save()
        res = self.client_api.post("/api/service/ledger/", {
            "customer": self.customer.id, "type": "BORC", "date": str(self.today),
            "amount": "100.00", "currency": "USD",
        }, format="json")
        self.assertEqual(res.status_code, 201, res.data)
        self.assertEqual(LedgerEntry.objects.get().rate, Decimal("41.2000"))

    def test_expense_in_foreign_currency_converts(self):
        settings_obj = FinanceSettings.load()
        settings_obj.eur_rate = Decimal("48.00")
        settings_obj.save()
        res = self.client_api.post("/api/service/expenses/", {
            "category": "KONAKLAMA", "date": str(self.today),
            "amount": "120.00", "currency": "EUR",
        }, format="json")
        self.assertEqual(res.status_code, 201, res.data)
        self.assertEqual(Expense.objects.get().try_amount, Decimal("5760.0000"))


class TcmbParserTest(TestCase):
    """TCMB bülteninin ayrıştırılması."""

    SAMPLE = """<?xml version="1.0" encoding="ISO-8859-9"?>
<Tarih_Date Tarih="18.09.2026" Date="09/18/2026" Bulten_No="2026/180">
	<Currency CrossOrder="0" Kod="USD" CurrencyCode="USD">
		<Unit>1</Unit><Isim>ABD DOLARI</Isim>
		<ForexBuying>41.1234</ForexBuying><ForexSelling>41.2000</ForexSelling>
	</Currency>
	<Currency CrossOrder="9" Kod="EUR" CurrencyCode="EUR">
		<Unit>1</Unit><Isim>EURO</Isim>
		<ForexBuying>48.3456</ForexBuying><ForexSelling>48.4321</ForexSelling>
	</Currency>
	<Currency CrossOrder="10" Kod="JPY" CurrencyCode="JPY">
		<Unit>100</Unit><Isim>JAPON YENI</Isim>
		<ForexBuying>27.8900</ForexBuying><ForexSelling>28.0500</ForexSelling>
	</Currency>
</Tarih_Date>"""

    def test_parses_selling_rate(self):
        self.assertEqual(rates.parse_tcmb_rate(self.SAMPLE, "USD"), Decimal("41.2000"))
        self.assertEqual(rates.parse_tcmb_rate(self.SAMPLE, "EUR"), Decimal("48.4321"))

    def test_normalises_by_unit(self):
        """JPY 100 birim üzerinden yayımlanır."""
        self.assertEqual(rates.parse_tcmb_rate(self.SAMPLE, "JPY"), Decimal("0.2805"))

    def test_unknown_currency(self):
        self.assertIsNone(rates.parse_tcmb_rate(self.SAMPLE, "XYZ"))


class ExpenseReflectionTest(TestCase):
    """Masrafların müşteri carisine yansıtılması."""

    def setUp(self):
        self.user = User.objects.create_user("yansit", password="x", role="admin")
        self.client_api = APIClient()
        self.client_api.force_authenticate(self.user)
        self.today = datetime.date.today()
        self.customer = Customer.objects.create(name="Kadir Patron Makine")
        settings_obj = FinanceSettings.load()
        settings_obj.eur_rate = Decimal("48.00")
        settings_obj.save()

        res = self.client_api.post("/api/service/reports/", {
            "customer": self.customer.id, "type": "ARIZA", "status": "COZULDU",
            "service_date": str(self.today), "work_done": "Devreye alma",
            "charge_amount": "500.00", "charge_currency": "EUR",
        }, format="json")
        self.assertEqual(res.status_code, 201, res.data)
        self.report_id = res.data["id"]

    def _expense(self, category, amount, billable=True):
        return self.client_api.post("/api/service/expenses/", {
            "report": self.report_id, "category": category, "date": str(self.today),
            "amount": amount, "currency": "TRY", "billable": billable,
        }, format="json")

    def test_billable_expenses_are_charged_to_customer(self):
        self._expense("YAKIT", "1500.00")
        self._expense("KONAKLAMA", "2200.00")

        entries = LedgerEntry.objects.filter(report_id=self.report_id, type="BORC")
        kinds = {e.kind: e for e in entries}
        self.assertIn("SERVIS", kinds)
        self.assertIn("MASRAF", kinds)
        self.assertEqual(kinds["SERVIS"].try_amount, Decimal("24000.0000"))
        self.assertEqual(kinds["MASRAF"].try_amount, Decimal("3700.0000"))

        # Müşterinin toplam borcu servis bedeli + yansıtılan masraf
        account = self.client_api.get(f"/api/service/accounts/{self.customer.id}/").data
        self.assertEqual(account["balance_try"], Decimal("27700.0000"))

        detail = self.client_api.get(f"/api/service/reports/{self.report_id}/").data
        self.assertEqual(Decimal(detail["customer_total"]), Decimal("27700.00"))
        self.assertEqual(Decimal(detail["billable_expense_total"]), Decimal("3700.00"))

    def test_non_billable_expense_is_excluded(self):
        self._expense("YAKIT", "1500.00")
        self._expense("YEMEK", "450.00", billable=False)

        reflection = LedgerEntry.objects.get(report_id=self.report_id, kind="MASRAF")
        self.assertEqual(reflection.try_amount, Decimal("1500.0000"))

        detail = self.client_api.get(f"/api/service/reports/{self.report_id}/").data
        self.assertEqual(Decimal(detail["expense_total"]), Decimal("1950.00"))
        # Kâr = (bedel + yansıtılan) - tüm masraf = 24000 + 1500 - 1950
        profit = Decimal(detail["customer_total"]) - Decimal(detail["expense_total"])
        self.assertEqual(profit, Decimal("23550.00"))

    def test_reflection_updates_when_expense_changes(self):
        res = self._expense("YAKIT", "1500.00")
        expense_id = res.data["id"]
        self.assertEqual(
            LedgerEntry.objects.get(report_id=self.report_id, kind="MASRAF").amount,
            Decimal("1500.00"),
        )

        self.client_api.patch(
            f"/api/service/expenses/{expense_id}/", {"amount": "1800.00"}, format="json"
        )
        self.assertEqual(
            LedgerEntry.objects.get(report_id=self.report_id, kind="MASRAF").amount,
            Decimal("1800.00"),
        )

        self.client_api.delete(f"/api/service/expenses/{expense_id}/")
        self.assertFalse(
            LedgerEntry.objects.filter(report_id=self.report_id, kind="MASRAF").exists(),
            "yansıtılacak masraf kalmayınca kalem silinmeli",
        )

    def test_foreign_currency_expense_is_reflected_in_lira(self):
        self.client_api.post("/api/service/expenses/", {
            "report": self.report_id, "category": "KONAKLAMA", "date": str(self.today),
            "amount": "120.00", "currency": "EUR", "billable": True,
        }, format="json")
        reflection = LedgerEntry.objects.get(report_id=self.report_id, kind="MASRAF")
        self.assertEqual(reflection.currency, "TRY")
        self.assertEqual(reflection.try_amount, Decimal("5760.0000"))

    def test_expense_pdf_contains_receipts(self):
        from django.core.files.uploadedfile import SimpleUploadedFile
        from io import BytesIO
        from PIL import Image

        def png():
            buf = BytesIO()
            Image.new("RGB", (400, 600), (240, 240, 240)).save(buf, "PNG")
            return buf.getvalue()

        self.client_api.post("/api/service/expenses/", {
            "report": self.report_id, "category": "YAKIT", "date": str(self.today),
            "amount": "1500.00", "currency": "TRY", "billable": True,
            "receipt": SimpleUploadedFile("fis1.png", png(), "image/png"),
        }, format="multipart")
        self.client_api.post("/api/service/expenses/", {
            "report": self.report_id, "category": "KONAKLAMA", "date": str(self.today),
            "amount": "2200.00", "currency": "TRY", "billable": True,
            "receipt": SimpleUploadedFile("fis2.png", png(), "image/png"),
        }, format="multipart")

        res = self.client_api.get(f"/api/service/reports/{self.report_id}/expense-pdf/")
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res["Content-Type"], "application/pdf")
        self.assertTrue(res.content.startswith(b"%PDF"))
        self.assertGreater(len(res.content), 3000)
