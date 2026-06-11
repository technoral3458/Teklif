import io
from django.http import HttpResponse
from rest_framework import generics, permissions, status
from rest_framework.decorators import api_view, permission_classes
from rest_framework.response import Response
from .models import NestingJob, NestingItem
from .serializers import NestingJobSerializer, NestingJobCreateSerializer
from .nesting import Piece, nest_multiple_plates
from .gcode import generate_gcode
from orders.models import Order, OrderItem
from accounts.models import User


class IsCNCOrAdmin(permissions.BasePermission):
    def has_permission(self, request, view):
        return request.user.is_authenticated and request.user.role in (
            User.ROLE_CNC, User.ROLE_ADMIN, User.ROLE_SALES
        )


class NestingJobListView(generics.ListAPIView):
    queryset = NestingJob.objects.all().prefetch_related("orders", "placed_items")
    serializer_class = NestingJobSerializer
    permission_classes = [IsCNCOrAdmin]


class NestingJobDetailView(generics.RetrieveDestroyAPIView):
    queryset = NestingJob.objects.all()
    serializer_class = NestingJobSerializer
    permission_classes = [IsCNCOrAdmin]


@api_view(["POST"])
@permission_classes([IsCNCOrAdmin])
def create_nesting_job(request):
    serializer = NestingJobCreateSerializer(data=request.data)
    if not serializer.is_valid():
        return Response(serializer.errors, status=400)

    data = serializer.validated_data
    order_ids = data.pop("order_ids")

    # Siparişleri getir
    orders = Order.objects.filter(id__in=order_ids, status=Order.STATUS_CNC_QUEUE)
    if not orders.exists():
        return Response({"error": "Geçerli CNC kuyruğundaki sipariş bulunamadı"}, status=400)

    # Parçaları topla
    pieces = []
    for order in orders:
        for item in order.items.all():
            for idx in range(item.quantity):
                w = float(item.final_width) + float(item.cap_model.milling_offset) * 2
                h = float(item.final_height) + float(item.cap_model.milling_offset) * 2
                pieces.append(Piece(
                    id=f"{item.id}_{idx}",
                    width=w,
                    height=h,
                    label=f"{item.cap_model.code} {float(item.final_width):.0f}x{float(item.final_height):.0f}",
                ))

    if not pieces:
        return Response({"error": "Sipariş kalemlerinde parça bulunamadı"}, status=400)

    # Nesting çalıştır
    plates, avg_efficiency = nest_multiple_plates(
        pieces=pieces,
        plate_width=float(data["plate_width"]),
        plate_height=float(data["plate_height"]),
        kerf=float(data["kerf"]),
    )

    # Job kaydet
    job = NestingJob.objects.create(**data, created_by=request.user, efficiency=avg_efficiency)
    job.orders.set(orders)

    # Placed items kaydet
    item_map = {}  # "item_id_idx" -> OrderItem
    for order in orders:
        for item in order.items.all():
            for idx in range(item.quantity):
                item_map[f"{item.id}_{idx}"] = item

    nesting_result = []
    for plate_idx, plate_pieces in enumerate(plates):
        for pp in plate_pieces:
            order_item = item_map.get(pp.id)
            if order_item:
                NestingItem.objects.create(
                    job=job,
                    order_item=order_item,
                    piece_index=int(pp.id.split("_")[-1]),
                    x=pp.x,
                    y=pp.y,
                    width=pp.width,
                    height=pp.height,
                    rotated=pp.rotated,
                    plate_index=plate_idx,
                )
            nesting_result.append({
                "id": pp.id,
                "x": pp.x, "y": pp.y,
                "width": pp.width, "height": pp.height,
                "rotated": pp.rotated,
                "plate_index": plate_idx,
                "label": pp.label,
            })

    job.nesting_result = nesting_result
    job.status = NestingJob.STATUS_DONE
    job.save()

    # Siparişleri "üretimde" yap
    orders.update(status=Order.STATUS_IN_PRODUCTION)

    return Response(NestingJobSerializer(job).data, status=201)


@api_view(["GET"])
@permission_classes([IsCNCOrAdmin])
def download_gcode(request, pk):
    try:
        job = NestingJob.objects.get(pk=pk)
    except NestingJob.DoesNotExist:
        return Response({"error": "İş bulunamadı"}, status=404)

    if not job.nesting_result:
        return Response({"error": "Nesting sonucu yok"}, status=400)

    # nesting_result'tan PlacedPiece listesi oluştur
    from .nesting import PlacedPiece

    plate_count = max((p["plate_index"] for p in job.nesting_result), default=0) + 1
    plates = [[] for _ in range(plate_count)]

    for p in job.nesting_result:
        plates[p["plate_index"]].append(PlacedPiece(
            id=p["id"],
            x=p["x"], y=p["y"],
            width=p["width"], height=p["height"],
            rotated=p["rotated"],
            label=p.get("label", ""),
            plate_index=p["plate_index"],
        ))

    gcode = generate_gcode(
        plates=plates,
        tool_diameter=float(job.tool_diameter),
        feed_rate=job.feed_rate,
        spindle_speed=job.spindle_speed,
        cut_depth=float(job.cut_depth),
        plate_width=float(job.plate_width),
        plate_height=float(job.plate_height),
        program_name=job.name.upper().replace(" ", "_")[:8],
    )

    filename = f"NC_{job.name}_{job.id}.nc"
    response = HttpResponse(gcode, content_type="text/plain")
    response["Content-Disposition"] = f'attachment; filename="{filename}"'
    return response


@api_view(["GET"])
@permission_classes([IsCNCOrAdmin])
def cnc_queue(request):
    """CNC kuyruğundaki siparişleri listele."""
    from orders.models import Order
    from orders.serializers import OrderSerializer
    orders = Order.objects.filter(status=Order.STATUS_CNC_QUEUE).select_related("dealer").prefetch_related("items")
    return Response(OrderSerializer(orders, many=True).data)
