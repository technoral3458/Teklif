from django.urls import path, include
from rest_framework.routers import DefaultRouter
from . import views

router = DefaultRouter()
router.register("brands", views.BrandViewSet)
router.register("models", views.CapModelViewSet)
router.register("colors", views.ColorViewSet)

urlpatterns = [path("", include(router.urls))]
