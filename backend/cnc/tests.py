import os

from django.contrib.auth import get_user_model
from django.test import TestCase
from rest_framework.test import APIClient

from . import dwd
from .models import DrillPanel

SAMPLE_PATH = os.path.join(os.path.dirname(__file__), "sample_files", "sample_123.xml")


class DwdParseTests(TestCase):
    """IngoMachine/DWD XML okuma testleri (örnek dosya üzerinden)."""

    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        cls.project = dwd.parse_file(SAMPLE_PATH)

    def test_project_meta(self):
        self.assertEqual(self.project.application, "IngoMachine")
        self.assertEqual(self.project.application_version, "2.0")
        self.assertEqual(self.project.name, "123")
        self.assertEqual(len(self.project.panels), 1)

    def test_panel_dimensions(self):
        panel = self.project.panels[0]
        self.assertEqual(panel.length, 500)
        self.assertEqual(panel.width, 300)
        self.assertEqual(panel.thickness, 18)
        self.assertEqual(panel.name, "500-300-44")

    def test_machining_counts(self):
        summary = self.project.summary()
        self.assertEqual(summary["drill_v"], 4)   # 4 dikey Ø15 delik
        self.assertEqual(summary["drill_h"], 4)   # 4 yatay Ø8 kavela
        self.assertEqual(summary["milling"], 4)   # 4 freze kanalı

    def test_vertical_drill_values(self):
        panel = self.project.panels[0]
        v = [m for m in panel.machinings if m.type == dwd.TYPE_VERTICAL_DRILL]
        self.assertTrue(all(m.face == dwd.FACE_TOP for m in v))
        self.assertTrue(all(m.diameter == 15 for m in v))
        self.assertTrue(all(m.depth == 13 for m in v))
        self.assertIn((33, 250), [(m.x, m.y) for m in v])

    def test_horizontal_drill_faces(self):
        panel = self.project.panels[0]
        h = [m for m in panel.machinings if m.type == dwd.TYPE_HORIZONTAL_DRILL]
        faces = sorted(m.face for m in h)
        self.assertEqual(faces, [3, 3, 4, 4])  # sağ ve sol kenar
        self.assertTrue(all(m.diameter == 8 for m in h))

    def test_milling_lines(self):
        panel = self.project.panels[0]
        mills = [m for m in panel.machinings if m.type == dwd.TYPE_MILLING]
        self.assertTrue(all(m.tool_name == "xxd10" for m in mills))
        self.assertTrue(all(len(m.lines) == 1 for m in mills))
        self.assertEqual(mills[0].lines[0].end_x, 400)


class DwdRoundTripTests(TestCase):
    """Oku → yaz → tekrar oku sırasında veri kaybı olmamalı."""

    def test_roundtrip_preserves_model(self):
        original = dwd.parse_file(SAMPLE_PATH)
        serialized = dwd.serialize(original)
        reparsed = dwd.parse(serialized)

        self.assertEqual(original.name, reparsed.name)
        self.assertEqual(len(original.panels), len(reparsed.panels))

        op = original.panels[0]
        rp = reparsed.panels[0]
        self.assertEqual(op.attrib, rp.attrib)
        self.assertEqual(len(op.machinings), len(rp.machinings))
        for a, b in zip(op.machinings, rp.machinings):
            self.assertEqual(a.attrib, b.attrib)
            self.assertEqual(len(a.lines), len(b.lines))
            for la, lb in zip(a.lines, b.lines):
                self.assertEqual(la.attrib, lb.attrib)
        self.assertEqual(op.edge_group_attrib, rp.edge_group_attrib)
        self.assertEqual([e.attrib for e in op.edges], [e.attrib for e in rp.edges])

    def test_roundtrip_is_idempotent(self):
        """İki kez serialize edilen çıktı bayt-bayt aynı olmalı."""
        project = dwd.parse_file(SAMPLE_PATH)
        first = dwd.serialize(project)
        second = dwd.serialize(dwd.parse(first))
        self.assertEqual(first, second)


class DwdBuilderTests(TestCase):
    """CAM tarafı: yeni operasyon üretme."""

    def test_build_panel_from_scratch(self):
        panel = dwd.Panel(attrib={
            "Name": "TEST", "Length": "600", "Width": "400", "Thickness": "18",
        })
        panel.add(dwd.vertical_drill(panel.next_id(), x=37, y=37, diameter=15,
                                     depth=13, face=dwd.FACE_TOP))
        panel.add(dwd.horizontal_drill(panel.next_id(), x=600, y=200, diameter=8,
                                       depth=33, face=dwd.FACE_RIGHT, z=9))
        panel.add(dwd.groove(panel.next_id(), x=0, y=9, end_x=600, end_y=9,
                             depth=6, tool_name="xxd10", face=dwd.FACE_BOTTOM))

        self.assertEqual([m.id for m in panel.machinings],
                         ["100001", "100002", "100003"])

        project = dwd.Project(name="TEST", panels=[panel])
        xml = dwd.serialize(project)
        reparsed = dwd.parse(xml)
        self.assertEqual(reparsed.summary(),
                         {"panels": 1, "drill_v": 1, "drill_h": 1, "milling": 1})
        self.assertEqual(reparsed.panels[0].machinings[2].lines[0].end_x, 600)


class DrillPanelModelTests(TestCase):
    """Model ↔ dwd köprüsü."""

    def test_from_dwd_and_back_to_xml(self):
        project = dwd.parse_file(SAMPLE_PATH)
        User = get_user_model()
        user = User.objects.create_user("op", role=User.ROLE_CNC)

        panel_obj = DrillPanel.from_dwd_panel(project.panels[0], project, created_by=user)
        panel_obj.save()

        self.assertEqual(panel_obj.length, 500)
        self.assertEqual(panel_obj.width, 300)
        self.assertEqual(panel_obj.operation_count, 12)

        # XML'e geri çevir → tekrar oku → operasyon sayısı korunmalı
        reparsed = dwd.parse(panel_obj.to_xml())
        self.assertEqual(reparsed.summary(),
                         {"panels": 1, "drill_v": 4, "drill_h": 4, "milling": 4})


class DrillPanelAPITests(TestCase):
    """İçe aktar → düzenle → dışa aktar uç noktaları."""

    def setUp(self):
        User = get_user_model()
        self.user = User.objects.create_user("cnc", role=User.ROLE_CNC)
        self.client = APIClient()
        self.client.force_authenticate(self.user)

    def _import_sample(self):
        with open(SAMPLE_PATH, "rb") as fh:
            return self.client.post("/api/cnc/drill-panels/import/",
                                    {"file": fh}, format="multipart")

    def test_import_creates_panel(self):
        resp = self._import_sample()
        self.assertEqual(resp.status_code, 201)
        self.assertEqual(len(resp.data), 1)
        self.assertEqual(resp.data[0]["operation_count"], 12)
        self.assertEqual(DrillPanel.objects.count(), 1)

    def test_import_requires_file(self):
        resp = self.client.post("/api/cnc/drill-panels/import/", {}, format="multipart")
        self.assertEqual(resp.status_code, 400)

    def test_import_rejects_bad_xml(self):
        from django.core.files.uploadedfile import SimpleUploadedFile
        bad = SimpleUploadedFile("x.xml", b"<not><valid", content_type="application/xml")
        resp = self.client.post("/api/cnc/drill-panels/import/",
                                {"file": bad}, format="multipart")
        self.assertEqual(resp.status_code, 400)

    def test_list_and_detail(self):
        self._import_sample()
        panel_id = DrillPanel.objects.first().id

        lst = self.client.get("/api/cnc/drill-panels/")
        self.assertEqual(lst.status_code, 200)
        self.assertEqual(len(lst.data), 1)

        detail = self.client.get(f"/api/cnc/drill-panels/{panel_id}/")
        self.assertEqual(detail.status_code, 200)
        self.assertEqual(len(detail.data["machinings"]), 12)

    def test_edit_dimensions(self):
        self._import_sample()
        panel = DrillPanel.objects.first()
        resp = self.client.patch(f"/api/cnc/drill-panels/{panel.id}/",
                                 {"length": 550}, format="json")
        self.assertEqual(resp.status_code, 200)
        panel.refresh_from_db()
        self.assertEqual(float(panel.length), 550)

    def test_export_reflects_edits(self):
        self._import_sample()
        panel = DrillPanel.objects.first()
        self.client.patch(f"/api/cnc/drill-panels/{panel.id}/",
                          {"length": 550, "name": "DUZENLI"}, format="json")

        resp = self.client.get(f"/api/cnc/drill-panels/{panel.id}/export/")
        self.assertEqual(resp.status_code, 200)
        self.assertIn("attachment", resp["Content-Disposition"])
        reparsed = dwd.parse(resp.content.decode("utf-8"))
        self.assertEqual(reparsed.panels[0].length, 550)
        self.assertEqual(reparsed.panels[0].name, "DUZENLI")

    def test_create_blank_panel(self):
        resp = self.client.post("/api/cnc/drill-panels/create/", {
            "name": "YENI", "length": 600, "width": 400, "thickness": 18,
        }, format="json")
        self.assertEqual(resp.status_code, 201)
        self.assertEqual(resp.data["source"], DrillPanel.SOURCE_MANUAL)

    def test_export_multiple(self):
        self._import_sample()
        self._import_sample()
        ids = list(DrillPanel.objects.values_list("id", flat=True))
        resp = self.client.post("/api/cnc/drill-panels/export/",
                                {"ids": ids, "project_name": "TOPLU"}, format="json")
        self.assertEqual(resp.status_code, 200)
        reparsed = dwd.parse(resp.content.decode("utf-8"))
        self.assertEqual(len(reparsed.panels), 2)

    def test_unauthenticated_blocked(self):
        client = APIClient()
        resp = client.get("/api/cnc/drill-panels/")
        self.assertIn(resp.status_code, (401, 403))
