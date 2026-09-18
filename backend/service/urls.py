from django.urls import path

from . import views

urlpatterns = [
    path("customers/", views.CustomerListCreateView.as_view()),
    path("customers/<int:pk>/", views.CustomerDetailView.as_view()),
    path("machines/", views.MachineListCreateView.as_view()),
    path("machines/<int:pk>/", views.MachineDetailView.as_view()),
    path("reports/", views.ServiceReportListCreateView.as_view()),
    path("reports/<int:pk>/", views.ServiceReportDetailView.as_view()),
    path("reports/<int:pk>/photos/", views.upload_photo),
    path("reports/<int:pk>/photos/<int:photo_id>/", views.photo_detail),
    path("reports/<int:pk>/signature/", views.upload_signature),
    path("reports/<int:pk>/pdf/", views.report_pdf),
    path("reports/<int:pk>/send-mail/", views.send_mail_view),
    path("mail-settings/", views.mail_settings_view),
    path("mail-settings/test/", views.test_mail_view),
    path("stats/", views.service_stats),

    # Cari ve masraf
    path("ledger/", views.LedgerEntryListCreateView.as_view()),
    path("ledger/<int:pk>/", views.LedgerEntryDetailView.as_view()),
    path("expenses/", views.ExpenseListCreateView.as_view()),
    path("expenses/<int:pk>/", views.ExpenseDetailView.as_view()),
    path("accounts/", views.accounts_view),
    path("accounts/<int:pk>/", views.customer_account_view),
    path("overdue/", views.overdue_view),
    path("monthly-report/", views.monthly_report_view),
    path("monthly-report/pdf/", views.monthly_report_pdf),
    path("monthly-report/mail/", views.monthly_report_mail),
    path("finance-settings/", views.finance_settings_view),
    path("finance-settings/refresh-rates/", views.refresh_rates_view),
    path("sync/", views.sync_from_mobile),
]
