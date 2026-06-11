from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from .models import User

@admin.register(User)
class CustomUserAdmin(UserAdmin):
    fieldsets = UserAdmin.fieldsets + (
        ("Ek Bilgiler", {"fields": ("role", "phone", "company", "city")}),
    )
    list_display = ["username", "email", "role", "company", "city"]
    list_filter = ["role"]
