from django.urls import path
from . import views

urlpatterns = [
    path("jobs/", views.NestingJobListView.as_view()),
    path("jobs/<int:pk>/", views.NestingJobDetailView.as_view()),
    path("jobs/create/", views.create_nesting_job),
    path("jobs/<int:pk>/gcode/", views.download_gcode),
    path("queue/", views.cnc_queue),
]
