from django.db import models


class Brand(models.Model):
    name = models.CharField(max_length=100)
    is_active = models.BooleanField(default=True)

    def __str__(self):
        return self.name

    class Meta:
        verbose_name = "Marka"
        verbose_name_plural = "Markalar"


class CapModel(models.Model):
    brand = models.ForeignKey(Brand, on_delete=models.CASCADE, related_name="models")
    name = models.CharField(max_length=100)
    code = models.CharField(max_length=50, unique=True)
    description = models.TextField(blank=True)

    # Varsayılan boyut sınırları (mm)
    min_width = models.DecimalField(max_digits=8, decimal_places=2, default=100)
    max_width = models.DecimalField(max_digits=8, decimal_places=2, default=1200)
    min_height = models.DecimalField(max_digits=8, decimal_places=2, default=100)
    max_height = models.DecimalField(max_digits=8, decimal_places=2, default=2400)

    # Freze payı (mm) - kesim için plakadan çıkarılan pay
    milling_offset = models.DecimalField(max_digits=6, decimal_places=2, default=5.0)

    is_active = models.BooleanField(default=True)

    def __str__(self):
        return f"{self.brand.name} - {self.name}"

    class Meta:
        verbose_name = "Kapak Modeli"
        verbose_name_plural = "Kapak Modelleri"


class Color(models.Model):
    name = models.CharField(max_length=100)
    code = models.CharField(max_length=20)
    hex_color = models.CharField(max_length=7, default="#FFFFFF")
    is_active = models.BooleanField(default=True)

    def __str__(self):
        return f"{self.name} ({self.code})"

    class Meta:
        verbose_name = "Renk"
        verbose_name_plural = "Renkler"
