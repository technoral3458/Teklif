"""Cari, masraf ve aylık rapor uçlarının uçtan uca testi."""
import datetime
from decimal import Decimal

from django.test import TestCase
from rest_framework.test import APIClient

from accounts.models import User
from .models import Customer, FinanceSettings, LedgerEntry, Machine


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
        assert balances["Öz Kardeşler Plastik"] == Decimal("20000.0000"), balances
        assert balances["Demir Metal"] == Decimal("6000.0000"), balances
        print("  bakiyeler:", {k: float(v) for k, v in balances.items()})
        print("  toplam alacak:", float(res.data["total_receivable"]),
              "| gecikmiş:", float(res.data["total_overdue"]))

        # Vadesi geçen: ilk müşterinin vadesi 10 gün önce doldu
        res = self.api("get", "/api/service/overdue/")
        assert res.data["count"] == 1, res.data
        assert res.data["items"][0]["customer"] == "Öz Kardeşler Plastik"
        assert res.data["items"][0]["days_late"] == 10, res.data["items"][0]
        print("  gecikmiş:", res.data["items"][0]["customer"], res.data["items"][0]["days_late"], "gün")

        # Söz verilen tarih geçince ayrıca işaretlenmeli
        entry = LedgerEntry.objects.get(report_id=report_id)
        entry.promised_date = self.today - datetime.timedelta(days=3)
        entry.save()
        res = self.api("get", "/api/service/overdue/")
        assert res.data["items"][0]["broken_promise"] is True, res.data["items"][0]
        assert res.data["items"][0]["days_late"] == 3
        print("  söz verilen tarih geçti:", res.data["items"][0]["days_late"], "gün")

        # Kısmi tahsilat sonrası açık borç
        self.api("post", "/api/service/ledger/", {
            "customer": c1.id, "type": "TAHSILAT", "date": str(self.today),
            "amount": "12000.00", "currency": "TRY",
        })
        res = self.api("get", f"/api/service/accounts/{c1.id}/")
        assert res.data["balance_try"] == Decimal("8000.0000"), res.data["balance_try"]
        assert len(res.data["open_debts"]) == 1
        assert res.data["open_debts"][0]["open_try"] == Decimal("8000.0000")
        print("  kısmi tahsilat sonrası açık borç:", float(res.data["open_debts"][0]["open_try"]))

        # Aylık rapor (bu ay: tahsilatlar + masraflar)
        res = self.api("get", f"/api/service/monthly-report/?year={self.today.year}&month={self.today.month}")
        assert res.status_code == 200, res.content
        summary = res.data
        assert summary["expense_try"] == Decimal("4470.00"), summary["expense_try"]
        assert summary["fuel_liters"] == Decimal("45.00"), summary["fuel_liters"]
        assert summary["collected_try"] == Decimal("16000.0000"), summary["collected_try"]
        print("  aylık:", summary["label"],
              "| tahsilat:", float(summary["collected_try"]),
              "| masraf:", float(summary["expense_try"]),
              "| yakıt:", float(summary["fuel_liters"]), "lt")
        print("  masraf dağılımı:", [(r["label"], float(r["amount"])) for r in summary["expense_by_category"]])

        # Rapor detayında ücret ve masraf toplamı
        res = self.api("get", f"/api/service/reports/{report_id}/")
        assert res.data["expense_total"] == Decimal("4470.00"), res.data["expense_total"]
        print("  rapor masraf toplamı:", float(res.data["expense_total"]),
              "| servis kârı:", float(res.data["charge"]["try_amount"] - res.data["expense_total"]))

        # PDF
        res = self.api("get", f"/api/service/monthly-report/pdf/?year={self.today.year}&month={self.today.month}")
        assert res.status_code == 200 and res["Content-Type"] == "application/pdf"
        print("  aylık rapor PDF:", len(res.content), "bayt")

        # Kur ayarları
        res = self.api("put", "/api/service/finance-settings/", {"usd_rate": "41.5", "eur_rate": "45.2"})
        assert res.status_code == 200, res.content
        assert FinanceSettings.load().usd_rate == Decimal("41.5000")

        # Döviz işleminde kur zorunlu
        res = self.api("post", "/api/service/ledger/", {
            "customer": c2.id, "type": "BORC", "date": str(self.today),
            "amount": "100.00", "currency": "EUR", "rate": "0",
        })
        assert res.status_code == 400, res.data
        print("  kursuz döviz girişi reddedildi ✔")
        print("CARİ UÇLARI ÇALIŞIYOR")
