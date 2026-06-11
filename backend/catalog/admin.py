from django.contrib import admin
from .models import Brand, CapModel, Color

@admin.register(Brand)
class BrandAdmin(admin.ModelAdmin):
    list_display = ["name", "is_active"]

@admin.register(CapModel)
class CapModelAdmin(admin.ModelAdmin):
    list_display = ["code", "name", "brand", "min_width", "max_width", "min_height", "max_height", "milling_offset"]
    list_filter = ["brand"]

@admin.register(Color)
class ColorAdmin(admin.ModelAdmin):
    list_display = ["name", "code", "hex_color", "is_active"]
