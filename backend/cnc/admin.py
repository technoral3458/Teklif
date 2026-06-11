from django.contrib import admin
from .models import NestingJob, NestingItem


class NestingItemInline(admin.TabularInline):
    model = NestingItem
    extra = 0
    readonly_fields = ["x", "y", "width", "height", "rotated", "plate_index"]


@admin.register(NestingJob)
class NestingJobAdmin(admin.ModelAdmin):
    list_display = ["name", "plate_width", "plate_height", "efficiency", "status", "created_at"]
    inlines = [NestingItemInline]
