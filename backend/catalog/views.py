from rest_framework import viewsets, permissions
from .models import Brand, CapModel, Color
from .serializers import BrandSerializer, CapModelSerializer, ColorSerializer
from accounts.models import User


class IsAdminOrReadOnly(permissions.BasePermission):
    def has_permission(self, request, view):
        if request.method in permissions.SAFE_METHODS:
            return request.user.is_authenticated
        return request.user.is_authenticated and request.user.role == User.ROLE_ADMIN


class BrandViewSet(viewsets.ModelViewSet):
    queryset = Brand.objects.filter(is_active=True)
    serializer_class = BrandSerializer
    permission_classes = [IsAdminOrReadOnly]


class CapModelViewSet(viewsets.ModelViewSet):
    queryset = CapModel.objects.filter(is_active=True).select_related("brand")
    serializer_class = CapModelSerializer
    permission_classes = [IsAdminOrReadOnly]

    def get_queryset(self):
        qs = super().get_queryset()
        brand_id = self.request.query_params.get("brand")
        if brand_id:
            qs = qs.filter(brand_id=brand_id)
        return qs


class ColorViewSet(viewsets.ModelViewSet):
    queryset = Color.objects.filter(is_active=True)
    serializer_class = ColorSerializer
    permission_classes = [IsAdminOrReadOnly]
