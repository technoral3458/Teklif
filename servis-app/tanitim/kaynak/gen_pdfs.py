# -*- coding: utf-8 -*-
"""Videoda gösterilecek PDF'leri uygulamanın kendi üreticisiyle hazırlar (geçici DB)."""
import os, sys, datetime, tempfile, shutil
from decimal import Decimal

BASE = "/home/user/Teklif/backend"
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "assets")
sys.path.insert(0, BASE)
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")

import django
django.setup()
from django.conf import settings

tmp = tempfile.mkdtemp(prefix="promo-db-")
settings.DATABASES["default"] = {
    "ENGINE": "django.db.backends.sqlite3",
    "NAME": os.path.join(tmp, "promo.sqlite3"),
    "ATOMIC_REQUESTS": False, "AUTOCOMMIT": True, "CONN_MAX_AGE": 0,
    "OPTIONS": {}, "TIME_ZONE": None, "USER": "", "PASSWORD": "", "HOST": "", "PORT": "",
    "TEST": {"CHARSET": None, "COLLATION": None, "NAME": None, "MIRROR": None},
    "CONN_HEALTH_CHECKS": False,
}
settings.MEDIA_ROOT = os.path.join(tmp, "media")
os.makedirs(settings.MEDIA_ROOT, exist_ok=True)

from django.core.management import call_command
call_command("migrate", verbosity=0, run_syncdb=True)

from django.core.files import File
from service.models import (Customer, Machine, ServiceReport, DepartmentWork, ServicePhoto,
                            SparePart, MailSettings, LedgerEntry, Expense)
from service.pdf import build_report_pdf, build_expense_pdf

company = MailSettings.load()
company.company_name = "Deli Kadir Teknik Servis"
company.company_address = "Organize Sanayi Bölgesi, 4. Cadde No:12 / Bursa"
company.company_phone = "+90 532 000 00 00"
company.company_email = "servis@delikadir.com"
company.company_web = "delikadir.com"
company.save()

cus = Customer.objects.create(name="Mermerci Makine San. Tic. A.Ş.", contact_name="Hüseyin Yalçın",
                              phone="+90 224 000 00 00", email="bakim@mermercimakine.com",
                              city="Bursa", address="OSB 12. Cadde No:4, Nilüfer / Bursa")
mac = Machine.objects.create(customer=cus, name="CNC Köprü Kesme Makinesi", brand="Teknoral",
                             model="TK-4200", serial_no="TK4200-2231", year="2021",
                             location="2. Hat - Kesim")

rep = ServiceReport.objects.create(
    report_no="SRV-202609-0042", customer=cus, machine=mac, type="ARIZA", status="COZULDU",
    priority="YUKSEK", service_date=datetime.date(2026, 9, 16),
    start_time=datetime.time(9, 10), end_time=datetime.time(16, 40), travel_km=Decimal("214.0"),
    fault_description="Kesim sırasında X ekseninde titreşim ve ölçü kaçıklığı. Operatör, 2 mm'ye "
                      "varan sapma ve yüksek sesli uğultu bildirdi.",
    fault_cause="X ekseni lineer kızak rulmanının iç bileziğinde çatlak; yetersiz yağlama nedeniyle "
                "aşınma ilerlemiş. Servo sürücüde aşırı akım hatası buna bağlı oluşmuş.",
    work_done="Rulman sökülerek yenisi (SKF 6208-2RS) takıldı, kızak temizlenip gresle yağlandı. "
              "Servo sürücü parametreleri yeniden ayarlandı, X ekseni referanslandı. "
              "Test kesiminde ölçü sapması 0,03 mm'ye indi.",
    recommendations="Yağlama periyodu 500 saate çekilmeli. 3 ay sonra kızak kontrolü önerilir. "
                    "Yedekte 1 adet 6208-2RS bulundurulması faydalı olur.",
    technician_name="Kadir Yıldız", customer_rep="Hüseyin Yalçın",
    next_maintenance=datetime.date(2026, 12, 16),
)
with open(os.path.join(ASSETS, "imza.png"), "rb") as fh:
    rep.signature.save("imza.png", File(fh), save=True)

for dep, work in (("MEKANIK", "X ekseni kızak ve rulman değişimi, gresleme."),
                  ("ELEKTRONIK", "Servo sürücü aşırı akım hatası silindi, parametreler yenilendi."),
                  ("YAZILIM", "Eksen referans ve backlash değerleri güncellendi.")):
    DepartmentWork.objects.create(report=rep, department=dep, work=work)

for fn, tag, cap in (("foto_ariza.png", "ARIZA", "Çatlak rulman iç bileziği"),
                     ("foto_oncesi.png", "ONCESI", "Sürücü kartında aşırı akım hatası"),
                     ("foto_sonrasi.png", "SONRASI", "Değişim sonrası test kesimi"),
                     ("foto_parca.png", "PARCA", "Takılan yeni rulman")):
    p = ServicePhoto(report=rep, tag=tag, caption=cap)
    with open(os.path.join(ASSETS, fn), "rb") as fh:
        p.image.save(fn, File(fh), save=True)

SparePart.objects.create(report=rep, name="Rulman 6208-2RS", code="SKF-6208", quantity=2,
                         unit="adet", status="TAKILDI", note="Orijinal muadil")
SparePart.objects.create(report=rep, name="Lineer kızak gresi", code="LGEP-2", quantity=1,
                         unit="tüp", status="TAKILDI")
SparePart.objects.create(report=rep, name="X ekseni kayışı", code="HTD-8M-1200", quantity=1,
                         unit="adet", status="TEKLIF", note="Sonraki bakımda değişmeli")

LedgerEntry.objects.create(customer=cus, report=rep, type="BORC", kind="SERVIS",
                           date=rep.service_date, amount=Decimal("500.00"), currency="EUR",
                           rate=Decimal("47.8231"), description="Servis bedeli",
                           due_date=datetime.date(2026, 10, 16))

exps = (("KONAKLAMA", "Grand Otel - 1 gece", Decimal("1474.00"), "fis_1_grand.png", 0),
        ("YAKIT", "Motorin 42,30 L", Decimal("2031.26"), "fis_2_petrol.png", Decimal("42.30")),
        ("YOL", "Kuzey Marmara otoyol geçişi", Decimal("184.50"), "fis_3_hgs.png", 0),
        ("YEMEK", "Yol ve saha yemeği", Decimal("328.00"), "fis_4_market.png", 0))
for cat, desc, amount, fis, qty in exps:
    e = Expense(report=rep, customer=cus, category=cat, date=datetime.date(2026, 9, 16),
                amount=amount, currency="TRY", rate=1, description=desc,
                quantity=qty or 0, billable=True)
    with open(os.path.join(ASSETS, fis), "rb") as fh:
        e.receipt.save(fis, File(fh), save=False)
    e.save()

out = os.path.join(HERE, "pdf")
os.makedirs(out, exist_ok=True)
for name, data in (("servis_raporu.pdf", build_report_pdf(rep, company)),
                   ("masraf_dokumu.pdf", build_expense_pdf(rep, company))):
    assert data, name
    with open(os.path.join(out, name), "wb") as fh:
        fh.write(data)
    print(name, len(data), "bayt")

print("servis bedeli TL:", rep.service_charge, "| masraf:", rep.billable_expense_total,
      "| müşteriye toplam:", rep.customer_total, "| süre:", rep.duration_minutes)
shutil.rmtree(tmp, ignore_errors=True)
