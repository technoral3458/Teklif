from django.urls import path
from . import views

urlpatterns = [
    path("jobs/", views.NestingJobListView.as_view()),
    path("jobs/<int:pk>/", views.NestingJobDetailView.as_view()),
    path("jobs/create/", views.create_nesting_job),
    path("jobs/<int:pk>/gcode/", views.download_gcode),
    path("queue/", views.cnc_queue),

    # Altı kenar delme — DWD panelleri
    path("drill-panels/", views.DrillPanelListView.as_view()),
    path("drill-panels/import/", views.import_drill_panels),
    path("drill-panels/create/", views.create_drill_panel),
    path("drill-panels/export/", views.export_drill_panels),
    path("drill-panels/<int:pk>/", views.DrillPanelDetailView.as_view()),
    path("drill-panels/<int:pk>/export/", views.export_drill_panel),
]
