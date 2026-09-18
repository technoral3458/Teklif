from django.apps import AppConfig


class ServiceConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "service"
    verbose_name = "Servis Yönetimi"

    def ready(self):
        # Masraflar nereden değişirse değişsin cari yansıtması tutarlı kalsın
        from . import signals  # noqa: F401
