"""Cari hesaplamalar: FIFO borç kapatma, vade takibi ve aylık özet."""

import calendar
import datetime
from decimal import Decimal

from django.db.models import Q

from .models import Customer, Expense, FinanceSettings, LedgerEntry, ServiceReport

ZERO = Decimal("0")
MONTH_NAMES = [
    "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
]


def month_label(year, month):
    return f"{MONTH_NAMES[month - 1]} {year}"


def month_range(year, month):
    first = datetime.date(year, month, 1)
    last = datetime.date(year, month, calendar.monthrange(year, month)[1])
    return first, last


def customer_account(customer, entries=None, today=None, grace_days=0):
    """Bir müşterinin cari durumu.

    Tahsilat ve iadeler tarih sırasıyla en eski borçtan başlayarak düşülür;
    kalan açık borçlar vade/söz kontrolüne girer. `grace_days`, vade gününde
    hemen uyarı verilmemesi için beklenen tolerans süresidir.
    """
    today = today or datetime.date.today()
    if entries is None:
        entries = list(customer.ledger_entries.all())
    entries = sorted(entries, key=lambda e: (e.date, e.id))

    debits = [e for e in entries if e.type == "BORC"]
    credits = [e for e in entries if e.type != "BORC"]

    debit_total = sum((e.try_amount for e in debits), ZERO)
    credit_total = sum((e.try_amount for e in credits), ZERO)

    remaining = credit_total
    open_debts = []
    for debt in debits:
        paid = min(remaining, debt.try_amount)
        remaining -= paid
        open_amount = debt.try_amount - paid
        if open_amount > Decimal("0.005"):
            deadline = debt.promised_date or debt.due_date
            days_late = (today - deadline).days if deadline and deadline < today else 0
            is_overdue = days_late > grace_days
            open_debts.append({
                "entry": debt,
                "open_try": open_amount,
                "deadline": deadline,
                "is_overdue": is_overdue,
                "broken_promise": bool(is_overdue and debt.promised_date),
                "days_late": days_late,
            })

    overdue_total = sum((d["open_try"] for d in open_debts if d["is_overdue"]), ZERO)

    return {
        "customer": customer,
        "debit_try": debit_total,
        "credit_try": credit_total,
        "balance_try": debit_total - credit_total,
        "open_debts": open_debts,
        "overdue_try": overdue_total,
        "has_overdue": overdue_total > 0,
        "last_activity": entries[-1].date if entries else None,
    }


def _grace_days(value=None):
    if value is not None:
        return value
    return FinanceSettings.load().overdue_grace_days


def all_accounts(today=None, grace_days=None):
    grace_days = _grace_days(grace_days)
    customers = list(Customer.objects.all())
    entries = list(LedgerEntry.objects.select_related("customer"))
    grouped = {}
    for entry in entries:
        grouped.setdefault(entry.customer_id, []).append(entry)
    return [customer_account(c, grouped.get(c.id, []), today, grace_days) for c in customers]


def overdue_list(today=None, grace_days=None):
    """Tüm müşterilerdeki vadesi/sözü geçmiş açık alacaklar, en gecikmişten başlayarak."""
    rows = []
    for account in all_accounts(today, grace_days):
        for debt in account["open_debts"]:
            if debt["is_overdue"]:
                rows.append({"customer": account["customer"], **debt})
    return sorted(rows, key=lambda r: r["days_late"], reverse=True)


def monthly_summary(year, month):
    first, last = month_range(year, month)
    entries = LedgerEntry.objects.filter(date__gte=first, date__lte=last)
    expenses = Expense.objects.filter(date__gte=first, date__lte=last)

    income = [e for e in entries if e.type == "BORC"]
    collected = [e for e in entries if e.type == "TAHSILAT"]
    expense_list = list(expenses)

    income_total = sum((e.try_amount for e in income), ZERO)
    collected_total = sum((e.try_amount for e in collected), ZERO)
    expense_total = sum((e.try_amount for e in expense_list), ZERO)

    by_category = {}
    for expense in expense_list:
        by_category[expense.category] = by_category.get(expense.category, ZERO) + expense.try_amount

    by_currency = {}
    for entry in income:
        by_currency[entry.currency] = by_currency.get(entry.currency, ZERO) + entry.amount

    by_customer = {}
    for entry in income:
        row = by_customer.setdefault(entry.customer_id, {"income": ZERO, "collected": ZERO})
        row["income"] += entry.try_amount
    for entry in collected:
        row = by_customer.setdefault(entry.customer_id, {"income": ZERO, "collected": ZERO})
        row["collected"] += entry.try_amount

    names = dict(Customer.objects.values_list("id", "name"))
    category_labels = dict(Expense.CATEGORY_CHOICES)

    return {
        "year": year,
        "month": month,
        "label": month_label(year, month),
        "income_try": income_total,
        "collected_try": collected_total,
        "expense_try": expense_total,
        "billable_expense_try": sum((e.try_amount for e in expense_list if e.billable), ZERO),
        "net_try": income_total - expense_total,
        "cash_flow_try": collected_total - expense_total,
        "service_count": ServiceReport.objects.filter(
            service_date__gte=first, service_date__lte=last
        ).count(),
        "fuel_liters": sum((e.quantity for e in expense_list if e.category == "YAKIT"), ZERO),
        "expense_by_category": [
            {"category": key, "label": category_labels.get(key, key), "amount": value}
            for key, value in sorted(by_category.items(), key=lambda kv: kv[1], reverse=True)
        ],
        "income_by_currency": [
            {"currency": key, "amount": value} for key, value in by_currency.items()
        ],
        "by_customer": [
            {
                "customer_id": key,
                "customer": names.get(key, "-"),
                "income": value["income"],
                "collected": value["collected"],
            }
            for key, value in sorted(by_customer.items(), key=lambda kv: kv[1]["income"], reverse=True)
        ],
    }


def receivables_total():
    return sum(
        (a["balance_try"] for a in all_accounts() if a["balance_try"] > 0),
        ZERO,
    )
