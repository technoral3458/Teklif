from django.utils import timezone
from rest_framework import generics, permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from .models import Order, OrderItem
from .serializers import OrderSerializer, OrderCreateSerializer, OrderItemSerializer
from accounts.models import User


class CanApproveOrders(permissions.BasePermission):
    def has_permission(self, request, view):
        return request.user.is_authenticated and request.user.can_approve_orders()


class OrderListCreateView(generics.ListCreateAPIView):
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        user = self.request.user
        if user.can_approve_orders() or user.role == User.ROLE_CNC:
            qs = Order.objects.all()
        else:
            qs = Order.objects.filter(dealer=user)

        status_filter = self.request.query_params.get("status")
        if status_filter:
            qs = qs.filter(status=status_filter)
        return qs.select_related("dealer").prefetch_related("items")

    def get_serializer_class(self):
        if self.request.method == "POST":
            return OrderCreateSerializer
        return OrderSerializer

    def perform_create(self, serializer):
        serializer.save(dealer=self.request.user)


class OrderDetailView(generics.RetrieveUpdateDestroyAPIView):
    serializer_class = OrderSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        user = self.request.user
        if user.can_approve_orders() or user.role == User.ROLE_CNC:
            return Order.objects.all()
        return Order.objects.filter(dealer=user)


@api_view(["POST"])
@permission_classes([CanApproveOrders])
def approve_order(request, pk):
    try:
        order = Order.objects.get(pk=pk)
    except Order.DoesNotExist:
        return Response({"error": "Sipariş bulunamadı"}, status=404)

    # Onaylanan kalemlerdeki düzeltmeleri kaydet
    items_data = request.data.get("items", [])
    for item_data in items_data:
        try:
            item = OrderItem.objects.get(pk=item_data["id"], order=order)
            if "approved_width" in item_data:
                item.approved_width = item_data["approved_width"]
            if "approved_height" in item_data:
                item.approved_height = item_data["approved_height"]
            item.save()
        except OrderItem.DoesNotExist:
            pass

    order.status = Order.STATUS_CNC_QUEUE
    order.reviewed_by = request.user
    order.reviewed_at = timezone.now()
    order.review_notes = request.data.get("review_notes", "")
    order.save()
    return Response(OrderSerializer(order).data)


@api_view(["POST"])
@permission_classes([CanApproveOrders])
def reject_order(request, pk):
    try:
        order = Order.objects.get(pk=pk)
    except Order.DoesNotExist:
        return Response({"error": "Sipariş bulunamadı"}, status=404)

    order.status = Order.STATUS_REJECTED
    order.reviewed_by = request.user
    order.reviewed_at = timezone.now()
    order.review_notes = request.data.get("review_notes", "")
    order.save()
    return Response(OrderSerializer(order).data)
