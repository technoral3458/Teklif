from django.db import models
from accounts.models import User
from catalog.models import CapModel, Color


class Order(models.Model):
    STATUS_DRAFT = "draft"
    STATUS_PENDING = "pending"
    STATUS_APPROVED = "approved"
    STATUS_REJECTED = "rejected"
    STATUS_CNC_QUEUE = "cnc_queue"
    STATUS_IN_PRODUCTION = "in_production"
    STATUS_DONE = "done"

    STATUSES = [
        (STATUS_DRAFT, "Taslak"),
        (STATUS_PENDING, "Onay Bekliyor"),
        (STATUS_APPROVED, "Onaylandı"),
        (STATUS_REJECTED, "Reddedildi"),
        (STATUS_CNC_QUEUE, "CNC Kuyruğunda"),
        (STATUS_IN_PRODUCTION, "Üretimde"),
        (STATUS_DONE, "Tamamlandı"),
    ]

    dealer = models.ForeignKey(User, on_delete=models.PROTECT, related_name="orders", limit_choices_to={"role": "dealer"})
    order_number = models.CharField(max_length=20, unique=True)
    status = models.CharField(max_length=20, choices=STATUSES, default=STATUS_PENDING)
    notes = models.TextField(blank=True)

    reviewed_by = models.ForeignKey(User, null=True, blank=True, on_delete=models.SET_NULL, related_name="reviewed_orders")
    review_notes = models.TextField(blank=True)
    reviewed_at = models.DateTimeField(null=True, blank=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Sipariş"
        verbose_name_plural = "Siparişler"
        ordering = ["-created_at"]

    def __str__(self):
        return f"{self.order_number} - {self.dealer.company}"

    def save(self, *args, **kwargs):
        if not self.order_number:
            import datetime
            last = Order.objects.order_by("-id").first()
            num = (last.id + 1) if last else 1
            self.order_number = f"SP-{datetime.date.today().strftime('%Y%m')}-{num:04d}"
        super().save(*args, **kwargs)


class OrderItem(models.Model):
    order = models.ForeignKey(Order, on_delete=models.CASCADE, related_name="items")
    cap_model = models.ForeignKey(CapModel, on_delete=models.PROTECT)
    color = models.ForeignKey(Color, on_delete=models.PROTECT)

    width = models.DecimalField(max_digits=8, decimal_places=2, help_text="Genişlik (mm)")
    height = models.DecimalField(max_digits=8, decimal_places=2, help_text="Yükseklik (mm)")
    quantity = models.PositiveIntegerField(default=1)

    notes = models.CharField(max_length=500, blank=True)

    # Satış ekibi onayı sonrası düzeltmeler
    approved_width = models.DecimalField(max_digits=8, decimal_places=2, null=True, blank=True)
    approved_height = models.DecimalField(max_digits=8, decimal_places=2, null=True, blank=True)

    class Meta:
        verbose_name = "Sipariş Kalemi"
        verbose_name_plural = "Sipariş Kalemleri"

    def __str__(self):
        return f"{self.order.order_number} - {self.cap_model.code} {self.width}x{self.height}"

    @property
    def final_width(self):
        return self.approved_width or self.width

    @property
    def final_height(self):
        return self.approved_height or self.height
