from rest_framework import serializers
from .models import Order, OrderItem
from catalog.serializers import CapModelSerializer, ColorSerializer


class OrderItemSerializer(serializers.ModelSerializer):
    cap_model_detail = CapModelSerializer(source="cap_model", read_only=True)
    color_detail = ColorSerializer(source="color", read_only=True)
    final_width = serializers.ReadOnlyField()
    final_height = serializers.ReadOnlyField()

    class Meta:
        model = OrderItem
        fields = "__all__"


class OrderSerializer(serializers.ModelSerializer):
    items = OrderItemSerializer(many=True, read_only=True)
    dealer_name = serializers.CharField(source="dealer.company", read_only=True)
    status_display = serializers.CharField(source="get_status_display", read_only=True)

    class Meta:
        model = Order
        fields = "__all__"
        read_only_fields = ["order_number", "reviewed_by", "reviewed_at"]


class OrderCreateSerializer(serializers.ModelSerializer):
    items = OrderItemSerializer(many=True)

    class Meta:
        model = Order
        fields = ["notes", "items"]

    def create(self, validated_data):
        items_data = validated_data.pop("items")
        order = Order.objects.create(**validated_data)
        for item in items_data:
            OrderItem.objects.create(order=order, **item)
        return order
