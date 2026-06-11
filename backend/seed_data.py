"""Demo veri oluşturucu"""
import os
import django
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
django.setup()

from accounts.models import User
from catalog.models import Brand, CapModel, Color

# Kullanıcılar
admin_user, _ = User.objects.get_or_create(username="admin", defaults={
    "email": "admin@memkap.com", "role": "admin",
    "first_name": "Admin", "last_name": "Kullanıcı",
    "company": "MemKap A.Ş.", "is_staff": True, "is_superuser": True,
})
admin_user.set_password("admin123")
admin_user.save()

sales_user, _ = User.objects.get_or_create(username="satis", defaults={
    "email": "satis@memkap.com", "role": "sales",
    "first_name": "Satış", "last_name": "Uzmanı",
    "company": "MemKap A.Ş.",
})
sales_user.set_password("satis123")
sales_user.save()

dealer1, _ = User.objects.get_or_create(username="bayi1", defaults={
    "email": "bayi1@test.com", "role": "dealer",
    "first_name": "Ahmet", "last_name": "Yılmaz",
    "company": "Yılmaz Mobilya", "city": "İstanbul", "phone": "0532 111 1111",
})
dealer1.set_password("bayi123")
dealer1.save()

dealer2, _ = User.objects.get_or_create(username="bayi2", defaults={
    "email": "bayi2@test.com", "role": "dealer",
    "first_name": "Mehmet", "last_name": "Demir",
    "company": "Demir Mutfak", "city": "Ankara", "phone": "0533 222 2222",
})
dealer2.set_password("bayi123")
dealer2.save()

cnc_user, _ = User.objects.get_or_create(username="cnc", defaults={
    "email": "cnc@memkap.com", "role": "cnc",
    "first_name": "CNC", "last_name": "Operatörü",
    "company": "MemKap A.Ş.",
})
cnc_user.set_password("cnc123")
cnc_user.save()

# Markalar
brand1, _ = Brand.objects.get_or_create(name="MemKap Pro")
brand2, _ = Brand.objects.get_or_create(name="UltraMem")

# Kapak modelleri
models_data = [
    {"brand": brand1, "name": "Klasik Düz", "code": "MKP-K01", "min_width": 200, "max_width": 900, "min_height": 200, "max_height": 1200, "milling_offset": 4},
    {"brand": brand1, "name": "Oval Çerçeveli", "code": "MKP-O01", "min_width": 250, "max_width": 800, "min_height": 250, "max_height": 1000, "milling_offset": 5},
    {"brand": brand1, "name": "Kaset Model", "code": "MKP-C01", "min_width": 300, "max_width": 1000, "min_height": 300, "max_height": 1200, "milling_offset": 6},
    {"brand": brand2, "name": "Moderno Düz", "code": "UM-M01", "min_width": 200, "max_width": 1200, "min_height": 200, "max_height": 2400, "milling_offset": 4},
    {"brand": brand2, "name": "Shaker Model", "code": "UM-S01", "min_width": 250, "max_width": 900, "min_height": 250, "max_height": 1200, "milling_offset": 5},
]
for m in models_data:
    CapModel.objects.get_or_create(code=m["code"], defaults=m)

# Renkler
colors = [
    ("Beyaz Mat", "BYZ-MAT", "#F5F5F5"),
    ("Siyah Mat", "SYH-MAT", "#1A1A1A"),
    ("Antrasit", "ANT", "#383838"),
    ("Krem", "KRM", "#FFF8E7"),
    ("Gri Açık", "GRI-AC", "#C8C8C8"),
    ("Ahşap Meşe", "AHS-MSE", "#8B6914"),
    ("Ahşap Ceviz", "AHS-CEV", "#5C3A1E"),
    ("Mavi Akdeniz", "MAV-AKD", "#1E5799"),
    ("Yeşil Zeytin", "YES-ZEY", "#4A5C2A"),
    ("Kırmızı Mat", "KIR-MAT", "#8B0000"),
]
for name, code, hex_c in colors:
    Color.objects.get_or_create(code=code, defaults={"name": name, "hex_color": hex_c})

print("Demo veriler oluşturuldu!")
print("Kullanıcılar:")
print("  admin / admin123 (Yönetici)")
print("  satis / satis123 (Satış Ekibi)")
print("  bayi1 / bayi123  (Bayi - Yılmaz Mobilya)")
print("  bayi2 / bayi123  (Bayi - Demir Mutfak)")
print("  cnc   / cnc123   (CNC Operatörü)")
