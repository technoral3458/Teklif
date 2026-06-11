from rest_framework import serializers
from .models import NestingJob, NestingItem
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
