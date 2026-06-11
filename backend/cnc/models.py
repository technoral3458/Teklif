from django.db import models
from orders.models import Order, OrderItem


class NestingJob(models.Model):
    STATUS_PENDING = "pending"
    STATUS_DONE = "done"

    STATUSES = [
        (STATUS_PENDING, "Hazırlanıyor"),
        (STATUS_DONE, "Tamamlandı"),
    ]

    name = models.CharField(max_length=200)
    plate_width = models.DecimalField(max_digits=8, decimal_places=2, help_text="Plaka genişliği (mm)")
    plate_height = models.DecimalField(max_digits=8, decimal_places=2, help_text="Plaka yüksekliği (mm)")
    kerf = models.DecimalField(max_digits=5, decimal_places=2, default=4.0, help_text="Freze çapı / takım boşluğu (mm)")
    tool_diameter = models.DecimalField(max_digits=5, decimal_places=2, default=6.0, help_text="Freze takım çapı (mm)")
    feed_rate = models.IntegerField(default=6000, help_text="İlerleme hızı (mm/dak)")
    spindle_speed = models.IntegerField(default=18000, help_text="Devir (RPM)")
    cut_depth = models.DecimalField(max_digits=5, decimal_places=2, default=18.0, help_text="Kesim derinliği (mm)")

    orders = models.ManyToManyField(Order, related_name="nesting_jobs", blank=True)
    status = models.CharField(max_length=20, choices=STATUSES, default=STATUS_PENDING)

    # Nesting sonuçları JSON olarak
    nesting_result = models.JSONField(null=True, blank=True)
    efficiency = models.DecimalField(max_digits=5, decimal_places=2, null=True, blank=True, help_text="Plaka kullanım verimliliği %")

    created_by = models.ForeignKey("accounts.User", on_delete=models.PROTECT)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "CNC Nesting İşi"
        verbose_name_plural = "CNC Nesting İşleri"
        ordering = ["-created_at"]

    def __str__(self):
        return f"{self.name} ({self.plate_width}x{self.plate_height})"


class NestingItem(models.Model):
    job = models.ForeignKey(NestingJob, on_delete=models.CASCADE, related_name="placed_items")
    order_item = models.ForeignKey(OrderItem, on_delete=models.CASCADE)
    piece_index = models.IntegerField(help_text="Aynı kalemden kaçıncı adet")

    x = models.DecimalField(max_digits=8, decimal_places=2)
    y = models.DecimalField(max_digits=8, decimal_places=2)
    width = models.DecimalField(max_digits=8, decimal_places=2)
    height = models.DecimalField(max_digits=8, decimal_places=2)
    rotated = models.BooleanField(default=False)

    plate_index = models.IntegerField(default=0, help_text="Kaçıncı plaka (0'dan başlar)")

    class Meta:
        ordering = ["plate_index", "y", "x"]
