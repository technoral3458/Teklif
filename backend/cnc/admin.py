from django.contrib import admin
from .models import NestingJob, NestingItem, DrillPanel


class NestingItemInline(admin.TabularInline):
    model = NestingItem
    extra = 0
    readonly_fields = ["x", "y", "width", "height", "rotated", "plate_index"]


@admin.register(NestingJob)
class NestingJobAdmin(admin.ModelAdmin):
    list_display = ["name", "plate_width", "plate_height", "efficiency", "status", "created_at"]
    inlines = [NestingItemInline]


@admin.register(DrillPanel)
class DrillPanelAdmin(admin.ModelAdmin):
    list_display = ["name", "length", "width", "thickness", "operation_count", "source", "created_at"]
    list_filter = ["source", "created_at"]
    search_fields = ["name", "project_name", "order_no"]
    readonly_fields = ["created_at", "updated_at"]
