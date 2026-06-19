from django.db import models
from orders.models import Order, OrderItem
from . import dwd


class NestingJob(models.Model):
    STATUS_PENDING = "pending"
    STATUS_DONE = "done"

    STATUSES = [
        (STATUS_PENDING, "Hazırlanıyor"),
        (STATUS_DONE, "Tamamlandı"),
    ]

    name = models.CharField(max_length=200)
    plate_width = models.DecimalField(max_digits=8, decimal_places=2, help_text="Plaka genişliği (mm)")
    plate_height = models.DecimalField(max_digits=8, decimal_places=2, help_text="Plaka yüksekliği (mm)")
    kerf = models.DecimalField(max_digits=5, decimal_places=2, default=4.0, help_text="Freze çapı / takım boşluğu (mm)")
    tool_diameter = models.DecimalField(max_digits=5, decimal_places=2, default=6.0, help_text="Freze takım çapı (mm)")
    feed_rate = models.IntegerField(default=6000, help_text="İlerleme hızı (mm/dak)")
    spindle_speed = models.IntegerField(default=18000, help_text="Devir (RPM)")
    cut_depth = models.DecimalField(max_digits=5, decimal_places=2, default=18.0, help_text="Kesim derinliği (mm)")

    orders = models.ManyToManyField(Order, related_name="nesting_jobs", blank=True)
    status = models.CharField(max_length=20, choices=STATUSES, default=STATUS_PENDING)

    # Nesting sonuçları JSON olarak
    nesting_result = models.JSONField(null=True, blank=True)
    efficiency = models.DecimalField(max_digits=5, decimal_places=2, null=True, blank=True, help_text="Plaka kullanım verimliliği %")

    created_by = models.ForeignKey("accounts.User", on_delete=models.PROTECT)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "CNC Nesting İşi"
        verbose_name_plural = "CNC Nesting İşleri"
        ordering = ["-created_at"]

    def __str__(self):
        return f"{self.name} ({self.plate_width}x{self.plate_height})"


class NestingItem(models.Model):
    job = models.ForeignKey(NestingJob, on_delete=models.CASCADE, related_name="placed_items")
    order_item = models.ForeignKey(OrderItem, on_delete=models.CASCADE)
    piece_index = models.IntegerField(help_text="Aynı kalemden kaçıncı adet")

    x = models.DecimalField(max_digits=8, decimal_places=2)
    y = models.DecimalField(max_digits=8, decimal_places=2)
    width = models.DecimalField(max_digits=8, decimal_places=2)
    height = models.DecimalField(max_digits=8, decimal_places=2)
    rotated = models.BooleanField(default=False)

    plate_index = models.IntegerField(default=0, help_text="Kaçıncı plaka (0'dan başlar)")

    class Meta:
        ordering = ["plate_index", "y", "x"]


class DrillPanel(models.Model):
    """
    Altı kenar delme makinesi (六面钻) için bir parça/panel.

    IngoMachine/DWD .xml formatından içe aktarılabilir veya sıfırdan
    oluşturulabilir. Operasyonlar (delik/kanal) round-trip sadakati için
    ham attrib sözlükleri halinde JSON olarak saklanır; düzenleme bu JSON
    üzerinden yapılır, dışa aktarımda dwd modülü ile tekrar XML'e çevrilir.
    """
    SOURCE_IMPORTED = "imported"
    SOURCE_MANUAL = "manual"
    SOURCES = [
        (SOURCE_IMPORTED, "İçe aktarıldı"),
        (SOURCE_MANUAL, "Elle oluşturuldu"),
    ]

    name = models.CharField(max_length=200, help_text="Parça adı")
    project_name = models.CharField(max_length=200, blank=True, default="", help_text="DWD proje adı")
    order_no = models.CharField(max_length=100, blank=True, default="")

    length = models.DecimalField(max_digits=9, decimal_places=2, help_text="Parça boyu X (mm)")
    width = models.DecimalField(max_digits=9, decimal_places=2, help_text="Parça eni Y (mm)")
    thickness = models.DecimalField(max_digits=6, decimal_places=2, default=18, help_text="Kalınlık (mm)")
    material = models.CharField(max_length=100, blank=True, default="")

    application = models.CharField(max_length=50, default="IngoMachine")
    application_version = models.CharField(max_length=20, default="2.0")

    # Round-trip için ham veriler
    panel_attrib = models.JSONField(default=dict, blank=True, help_text="Panel düzeyi ham nitelikler")
    machinings = models.JSONField(default=list, blank=True, help_text="Operasyon listesi (delik/kanal)")
    edge_group_attrib = models.JSONField(default=dict, blank=True)
    edges = models.JSONField(default=list, blank=True)

    source = models.CharField(max_length=20, choices=SOURCES, default=SOURCE_MANUAL)
    order = models.ForeignKey(Order, on_delete=models.SET_NULL, null=True, blank=True, related_name="drill_panels")

    created_by = models.ForeignKey("accounts.User", on_delete=models.PROTECT, related_name="drill_panels")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Delme Paneli"
        verbose_name_plural = "Delme Panelleri"
        ordering = ["-created_at"]

    def __str__(self):
        return f"{self.name} ({self.length}x{self.width}x{self.thickness})"

    @property
    def operation_count(self) -> int:
        return len(self.machinings or [])

    # ---- dwd modülü ile köprü ----
    def to_dwd_panel(self) -> "dwd.Panel":
        return dwd.Panel.from_dict({
            "attrib": self.panel_attrib or {},
            "machinings": self.machinings or [],
            "edge_group_attrib": self.edge_group_attrib or {},
            "edges": self.edges or [],
        })

    def to_dwd_project(self) -> "dwd.Project":
        return dwd.Project(
            name=self.project_name or self.name,
            panels=[self.to_dwd_panel()],
            application=self.application,
            application_version=self.application_version,
        )

    def to_xml(self) -> str:
        return dwd.serialize(self.to_dwd_project())

    @classmethod
    def from_dwd_panel(cls, panel: "dwd.Panel", project: "dwd.Project", created_by) -> "DrillPanel":
        """Bir dwd.Panel'den (kaydedilmemiş) DrillPanel örneği üretir."""
        data = panel.to_dict()
        return cls(
            name=panel.name or project.name or "Panel",
            project_name=project.name,
            order_no=panel.attrib.get("OrderNo", ""),
            length=panel.length or 0,
            width=panel.width or 0,
            thickness=panel.thickness or 0,
            material=panel.attrib.get("Material", ""),
            application=project.application,
            application_version=project.application_version,
            panel_attrib=data["attrib"],
            machinings=data["machinings"],
            edge_group_attrib=data["edge_group_attrib"],
            edges=data["edges"],
            source=cls.SOURCE_IMPORTED,
            created_by=created_by,
        )

    def sync_dimensions_to_attrib(self) -> None:
        """Yapısal ölçü alanlarını panel_attrib içine yansıtır (dışa aktarım öncesi)."""
        attrib = dict(self.panel_attrib or {})
        attrib["Name"] = self.name
        attrib["Length"] = dwd._fmt(float(self.length))
        attrib["Width"] = dwd._fmt(float(self.width))
        attrib["Thickness"] = dwd._fmt(float(self.thickness))
        if self.material:
            attrib["Material"] = self.material
        if self.order_no:
            attrib["OrderNo"] = self.order_no
        self.panel_attrib = attrib
