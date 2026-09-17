from django.db import models
from accounts.models import User


class Customer(models.Model):
    name = models.CharField(max_length=200, verbose_name="Firma adı")
    contact_name = models.CharField(max_length=150, blank=True, verbose_name="Yetkili")
    phone = models.CharField(max_length=30, blank=True)
    email = models.EmailField(blank=True)
    address = models.TextField(blank=True)
    city = models.CharField(max_length=100, blank=True)
    notes = models.TextField(blank=True)

    # Mobil uygulamadan gelen kayıtları eşlemek için
    external_id = models.CharField(max_length=64, blank=True, db_index=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Servis Müşterisi"
        verbose_name_plural = "Servis Müşterileri"
        ordering = ["name"]

    def __str__(self):
        return self.name


class Machine(models.Model):
    customer = models.ForeignKey(Customer, on_delete=models.CASCADE, related_name="machines")
    name = models.CharField(max_length=200, verbose_name="Makine adı / tipi")
    brand = models.CharField(max_length=100, blank=True, verbose_name="Marka")
    model = models.CharField(max_length=100, blank=True)
    serial_no = models.CharField(max_length=100, blank=True, db_index=True, verbose_name="Seri no")
    year = models.CharField(max_length=10, blank=True, verbose_name="Üretim yılı")
    location = models.CharField(max_length=150, blank=True, verbose_name="Konum / hat")
    install_date = models.DateField(null=True, blank=True, verbose_name="Kurulum tarihi")
    warranty_end = models.DateField(null=True, blank=True, verbose_name="Garanti bitişi")
    notes = models.TextField(blank=True)

    external_id = models.CharField(max_length=64, blank=True, db_index=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Makine"
        verbose_name_plural = "Makineler"
        ordering = ["customer__name", "name"]

    def __str__(self):
        parts = [p for p in (self.brand, self.name) if p]
        label = " ".join(parts) or self.model or self.serial_no or "Makine"
        return f"{label} ({self.customer.name})"


class ServiceReport(models.Model):
    TYPE_CHOICES = [
        ("ARIZA", "Arıza"),
        ("PERIYODIK_BAKIM", "Periyodik Bakım"),
        ("KURULUM", "Kurulum"),
        ("DEVREYE_ALMA", "Devreye Alma"),
        ("REVIZYON", "Revizyon"),
        ("KESIF", "Keşif"),
        ("EGITIM", "Eğitim"),
        ("GARANTI", "Garanti"),
    ]

    STATUS_CHOICES = [
        ("TASLAK", "Taslak"),
        ("ACIK", "Açık"),
        ("COZULDU", "Çözüldü"),
        ("GECICI_COZUM", "Geçici Çözüm"),
        ("PARCA_BEKLIYOR", "Parça Bekliyor"),
        ("TEKRAR_ZIYARET", "Tekrar Ziyaret Gerekli"),
    ]

    PRIORITY_CHOICES = [
        ("DUSUK", "Düşük"),
        ("NORMAL", "Normal"),
        ("YUKSEK", "Yüksek"),
        ("KRITIK", "Kritik / Duruş"),
    ]

    report_no = models.CharField(max_length=32, unique=True, verbose_name="Rapor no")
    customer = models.ForeignKey(Customer, on_delete=models.PROTECT, related_name="reports")
    machine = models.ForeignKey(
        Machine, on_delete=models.SET_NULL, null=True, blank=True, related_name="reports"
    )

    type = models.CharField(max_length=20, choices=TYPE_CHOICES, default="ARIZA")
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default="ACIK")
    priority = models.CharField(max_length=20, choices=PRIORITY_CHOICES, default="NORMAL")

    service_date = models.DateField(verbose_name="Servis tarihi")
    start_time = models.TimeField(null=True, blank=True)
    end_time = models.TimeField(null=True, blank=True)
    travel_km = models.DecimalField(max_digits=8, decimal_places=1, default=0)

    fault_description = models.TextField(blank=True, verbose_name="Arıza / talep tanımı")
    fault_cause = models.TextField(blank=True, verbose_name="Arıza nedeni")
    work_done = models.TextField(blank=True, verbose_name="Yapılan işlem")
    recommendations = models.TextField(blank=True, verbose_name="Öneriler")

    technician = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True, related_name="service_reports"
    )
    technician_name = models.CharField(max_length=150, blank=True)
    customer_rep = models.CharField(max_length=150, blank=True, verbose_name="Müşteri yetkilisi")
    signature = models.ImageField(upload_to="servis/imzalar/", null=True, blank=True)

    next_maintenance = models.DateField(null=True, blank=True, verbose_name="Sonraki bakım")

    mailed_to = models.CharField(max_length=500, blank=True)
    mailed_at = models.DateTimeField(null=True, blank=True)

    external_id = models.CharField(max_length=64, blank=True, db_index=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Servis Raporu"
        verbose_name_plural = "Servis Raporları"
        ordering = ["-service_date", "-id"]

    def __str__(self):
        return f"{self.report_no} - {self.customer.name}"

    def save(self, *args, **kwargs):
        if not self.report_no:
            self.report_no = self.build_report_no()
        super().save(*args, **kwargs)

    @classmethod
    def build_report_no(cls, prefix="SRV"):
        import datetime

        period = datetime.date.today().strftime("%Y%m")
        head = f"{prefix}-{period}-"
        last = (
            cls.objects.filter(report_no__startswith=head)
            .order_by("-report_no")
            .values_list("report_no", flat=True)
            .first()
        )
        seq = int(last.rsplit("-", 1)[1]) + 1 if last else 1
        return f"{head}{seq:04d}"

    @property
    def duration_minutes(self):
        if not self.start_time or not self.end_time:
            return None
        start = self.start_time.hour * 60 + self.start_time.minute
        end = self.end_time.hour * 60 + self.end_time.minute
        return end - start if end > start else None


class DepartmentWork(models.Model):
    DEPARTMENT_CHOICES = [
        ("MEKANIK", "Mekanik"),
        ("ELEKTRIK", "Elektrik"),
        ("ELEKTRONIK", "Elektronik"),
        ("PNOMATIK", "Pnömatik"),
        ("HIDROLIK", "Hidrolik"),
        ("YAZILIM", "Yazılım / PLC"),
        ("KALIBRASYON", "Kalibrasyon"),
        ("OTOMASYON", "Otomasyon"),
        ("TEMIZLIK", "Temizlik / Yağlama"),
        ("DIGER", "Diğer"),
    ]

    report = models.ForeignKey(ServiceReport, on_delete=models.CASCADE, related_name="departments")
    department = models.CharField(max_length=20, choices=DEPARTMENT_CHOICES)
    work = models.TextField(blank=True)

    class Meta:
        verbose_name = "Çalışılan Bölüm"
        verbose_name_plural = "Çalışılan Bölümler"

    def __str__(self):
        return f"{self.report.report_no} - {self.get_department_display()}"


class ServicePhoto(models.Model):
    TAG_CHOICES = [
        ("ARIZA", "Arıza"),
        ("ONCESI", "İşlem Öncesi"),
        ("SONRASI", "İşlem Sonrası"),
        ("PARCA", "Parça"),
        ("ETIKET", "Makine Etiketi"),
        ("DIGER", "Diğer"),
    ]

    report = models.ForeignKey(ServiceReport, on_delete=models.CASCADE, related_name="photos")
    image = models.ImageField(upload_to="servis/fotograflar/")
    caption = models.CharField(max_length=300, blank=True)
    tag = models.CharField(max_length=20, choices=TAG_CHOICES, default="ARIZA")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "Servis Fotoğrafı"
        verbose_name_plural = "Servis Fotoğrafları"
        ordering = ["id"]

    def __str__(self):
        return f"{self.report.report_no} - {self.get_tag_display()}"


class SparePart(models.Model):
    STATUS_CHOICES = [
        ("TAKILDI", "Takıldı"),
        ("GEREKLI", "Gerekli"),
        ("SIPARIS", "Sipariş Edilecek"),
        ("TEKLIF", "Teklif Verilecek"),
    ]

    report = models.ForeignKey(ServiceReport, on_delete=models.CASCADE, related_name="parts")
    name = models.CharField(max_length=200)
    code = models.CharField(max_length=100, blank=True)
    quantity = models.DecimalField(max_digits=10, decimal_places=2, default=1)
    unit = models.CharField(max_length=20, default="adet")
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default="TAKILDI")
    note = models.CharField(max_length=300, blank=True)

    class Meta:
        verbose_name = "Yedek Parça"
        verbose_name_plural = "Yedek Parçalar"
        ordering = ["id"]

    def __str__(self):
        return f"{self.name} x{self.quantity}"


class MailSettings(models.Model):
    """Servis raporu maillerinin gönderileceği SMTP hesabı. Tek kayıt tutulur."""

    SECURITY_CHOICES = [
        ("STARTTLS", "STARTTLS (genelde 587)"),
        ("SSL", "SSL/TLS (genelde 465)"),
        ("NONE", "Şifreleme yok (25)"),
    ]

    host = models.CharField(max_length=200, blank=True, verbose_name="SMTP sunucusu")
    port = models.PositiveIntegerField(default=587)
    security = models.CharField(max_length=10, choices=SECURITY_CHOICES, default="STARTTLS")
    username = models.CharField(max_length=200, blank=True)
    password = models.CharField(max_length=200, blank=True)
    from_address = models.EmailField(blank=True, verbose_name="Gönderen adresi")
    from_name = models.CharField(max_length=150, blank=True, verbose_name="Gönderen adı")
    default_to = models.CharField(max_length=500, blank=True, verbose_name="Varsayılan alıcı")
    default_cc = models.CharField(max_length=500, blank=True, verbose_name="Bilgi (CC)")
    subject_template = models.CharField(
        max_length=300,
        default="Servis Raporu {rapor_no} - {musteri}",
        verbose_name="Konu şablonu",
    )
    attach_photos = models.BooleanField(default=True, verbose_name="Fotoğrafları ekle")

    # Rapor başlığında görünen firma künyesi
    company_name = models.CharField(max_length=200, blank=True)
    company_address = models.TextField(blank=True)
    company_phone = models.CharField(max_length=50, blank=True)
    company_email = models.EmailField(blank=True)
    company_web = models.CharField(max_length=150, blank=True)

    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Mail Ayarları"
        verbose_name_plural = "Mail Ayarları"

    def __str__(self):
        return self.host or "Mail ayarları tanımlı değil"

    @property
    def is_configured(self):
        return bool(self.host and self.from_address)

    @classmethod
    def load(cls):
        obj = cls.objects.first()
        if obj is None:
            obj = cls.objects.create()
        return obj
