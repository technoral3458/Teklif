"""Masrafların müşteri carisine yansıtılması.

Yansıtılacak masraflar, rapora bağlı tek bir borç kalemi olarak tutulur.
Masraf eklendikçe/değiştikçe/silindikçe bu kalem yeniden hesaplanır.
"""

from decimal import Decimal

from .models import LedgerEntry


def sync_expense_reflection(report):
    """Raporun yansıtılan masraf kalemini günceller ve kalemi döndürür."""
    if report is None:
        return None

    entry = report.ledger_entries.filter(type="BORC", kind="MASRAF").first()
    expenses = [e for e in report.expenses.all() if e.billable]
    total = sum((e.try_amount for e in expenses), Decimal("0"))

    if total <= Decimal("0.005"):
        if entry:
            entry.delete()
        return None

    values = {
        "customer": report.customer,
        "type": "BORC",
        "kind": "MASRAF",
        "date": report.service_date,
        "amount": total,
        "currency": "TRY",
        "rate": Decimal("1"),
        "description": f"Yansıtılan masraflar ({len(expenses)} kalem) — {report.report_no}",
    }
    charge = report.service_charge
    if charge:
        values["due_date"] = charge.due_date

    if entry:
        for field, value in values.items():
            setattr(entry, field, value)
        entry.save()
    else:
        entry = LedgerEntry.objects.create(report=report, **values)
    return entry
