"""Masraf değişikliklerinde cari yansıtmasını güncel tutan sinyaller."""

from django.db.models.signals import post_delete, post_save
from django.dispatch import receiver

from .models import Expense
from .reflection import sync_expense_reflection


@receiver(post_save, sender=Expense)
def expense_saved(sender, instance, **kwargs):
    if instance.report_id:
        sync_expense_reflection(instance.report)


@receiver(post_delete, sender=Expense)
def expense_deleted(sender, instance, **kwargs):
    # Rapor da silinmişse yansıtma kalemi zaten cascade ile gitmiştir
    from .models import ServiceReport

    report = ServiceReport.objects.filter(pk=instance.report_id).first()
    if report:
        sync_expense_reflection(report)
