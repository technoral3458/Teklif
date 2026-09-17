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
    path("sync/", views.sync_from_mobile),
]
