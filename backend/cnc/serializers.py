from rest_framework import serializers
from .models import NestingJob, NestingItem, DrillPanel
from orders.serializers import OrderSerializer


class NestingItemSerializer(serializers.ModelSerializer):
    class Meta:
        model = NestingItem
        fields = "__all__"


class NestingJobSerializer(serializers.ModelSerializer):
    placed_items = NestingItemSerializer(many=True, read_only=True)
    orders_detail = OrderSerializer(source="orders", many=True, read_only=True)

    class Meta:
        model = NestingJob
        fields = "__all__"
        read_only_fields = ["nesting_result", "efficiency", "created_by"]


class NestingJobCreateSerializer(serializers.ModelSerializer):
    order_ids = serializers.ListField(child=serializers.IntegerField(), write_only=True)

    class Meta:
        model = NestingJob
        fields = [
            "name", "plate_width", "plate_height", "kerf",
            "tool_diameter", "feed_rate", "spindle_speed", "cut_depth",
            "order_ids",
        ]


class DrillPanelListSerializer(serializers.ModelSerializer):
    """Liste görünümü: ağır JSON alanları olmadan özet."""
    operation_count = serializers.IntegerField(read_only=True)

    class Meta:
        model = DrillPanel
        fields = [
            "id", "name", "project_name", "order_no",
            "length", "width", "thickness", "material",
            "source", "operation_count", "created_at", "updated_at",
        ]


class DrillPanelSerializer(serializers.ModelSerializer):
    """Detay/düzenleme: operasyon JSON'ı dahil."""
    operation_count = serializers.IntegerField(read_only=True)

    class Meta:
        model = DrillPanel
        fields = "__all__"
        read_only_fields = ["created_by", "created_at", "updated_at", "source", "application", "application_version"]
