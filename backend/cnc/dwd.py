"""
IngoMachine / DWD panel formatı (.xml) okuyucu-yazıcı.

Altı kenar delme makinesinin (六面钻) CAM zinciri için çekirdek modül.
Bu format, makineden bağımsız bir parça/panel tarifidir: bir panelin
yüzey/kenar delikleri ve kanalları (machining) burada tanımlanır, DWD
yazılımı bu XML'i okuyup makineye gönderir.

Tasarım: Round-trip (oku → yaz → tekrar oku) sırasında veri kaybı
olmaması için her elemanın ham nitelikleri (attrib) korunur. Üstüne,
okurken/yazarken kullanışlı olsun diye tipli erişimciler (face, type,
diameter ...) ve yeni operasyon üretmek için yapıcı (builder) fonksiyonlar
eklenmiştir.

Yüz (Face) numaralandırması:
    5 = üst yüzey,  6 = alt yüzey
    1,2 = ön/arka kenarlar (Y=0 / Y=Width)
    3   = sağ kenar (X=Length),  4 = sol kenar (X=0)

Operasyon tipleri (Machining @Type):
    1 = yatay delme  (kenara, Face 1-4)
    2 = dikey delme  (yüzeye, Face 5/6)
    3 = freze/kanal  (Lines alt elemanları ile yol tanımlı)
"""
from __future__ import annotations

import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from typing import Dict, List, Optional

# Operasyon tipleri
TYPE_HORIZONTAL_DRILL = 1   # yatay delme (kenar)
TYPE_VERTICAL_DRILL = 2     # dikey delme (yüzey)
TYPE_MILLING = 3            # freze / kanal

# Yüzler
FACE_FRONT = 1
FACE_BACK = 2
FACE_RIGHT = 3
FACE_LEFT = 4
FACE_TOP = 5
FACE_BOTTOM = 6


def _fmt(value) -> str:
    """Sayıyı XML'e yazılacak biçime çevirir (tam sayılar ondalıksız)."""
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, float):
        if value.is_integer():
            return str(int(value))
        return repr(value)
    return str(value)


def _num(attrib: Dict[str, str], key: str, default: float = 0.0) -> float:
    """Attrib içinden sayısal değer okur."""
    raw = attrib.get(key)
    if raw is None or raw == "":
        return default
    try:
        return float(raw)
    except ValueError:
        return default


@dataclass
class Line:
    """Type 3 (freze) operasyonunun yol parçası."""
    attrib: Dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {"attrib": dict(self.attrib)}

    @classmethod
    def from_dict(cls, data: dict) -> "Line":
        return cls(attrib=dict(data.get("attrib", {})))

    @property
    def end_x(self) -> float:
        return _num(self.attrib, "EndX")

    @property
    def end_y(self) -> float:
        return _num(self.attrib, "EndY")

    @property
    def angle(self) -> float:
        return _num(self.attrib, "Angle")

    @property
    def line_id(self) -> str:
        return self.attrib.get("LineID", "")


@dataclass
class Machining:
    """Tek bir makine operasyonu (delik / kanal)."""
    attrib: Dict[str, str] = field(default_factory=dict)
    lines: List[Line] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {"attrib": dict(self.attrib), "lines": [ln.to_dict() for ln in self.lines]}

    @classmethod
    def from_dict(cls, data: dict) -> "Machining":
        return cls(
            attrib=dict(data.get("attrib", {})),
            lines=[Line.from_dict(ln) for ln in data.get("lines", [])],
        )

    # --- tipli erişimciler ---
    @property
    def id(self) -> str:
        return self.attrib.get("ID", "")

    @property
    def type(self) -> int:
        return int(_num(self.attrib, "Type"))

    @property
    def face(self) -> int:
        return int(_num(self.attrib, "Face"))

    @property
    def x(self) -> float:
        return _num(self.attrib, "X")

    @property
    def y(self) -> float:
        return _num(self.attrib, "Y")

    @property
    def z(self) -> float:
        return _num(self.attrib, "Z")

    @property
    def depth(self) -> float:
        return _num(self.attrib, "Depth")

    @property
    def diameter(self) -> float:
        return _num(self.attrib, "Diameter")

    @property
    def tool_name(self) -> str:
        return self.attrib.get("ToolName", "")

    @property
    def is_drill(self) -> bool:
        return self.type in (TYPE_HORIZONTAL_DRILL, TYPE_VERTICAL_DRILL)

    @property
    def is_milling(self) -> bool:
        return self.type == TYPE_MILLING

    def describe(self) -> str:
        """İnsan-okur özet (loglama/önizleme için)."""
        names = {
            TYPE_HORIZONTAL_DRILL: "Yatay delme",
            TYPE_VERTICAL_DRILL: "Dikey delme",
            TYPE_MILLING: "Freze/kanal",
        }
        face_names = {
            FACE_FRONT: "ön", FACE_BACK: "arka", FACE_RIGHT: "sağ",
            FACE_LEFT: "sol", FACE_TOP: "üst", FACE_BOTTOM: "alt",
        }
        op = names.get(self.type, f"Tip{self.type}")
        fc = face_names.get(self.face, f"yüz{self.face}")
        if self.is_drill:
            return (f"{op} | {fc} yüz | Ø{_fmt(self.diameter)} "
                    f"derinlik {_fmt(self.depth)} @ ({_fmt(self.x)},{_fmt(self.y)})")
        return (f"{op} | {fc} yüz | takım {self.tool_name or '?'} "
                f"derinlik {_fmt(self.depth)} | {len(self.lines)} yol")


@dataclass
class Edge:
    """EdgeGroup içindeki bir kenar tanımı (bantlama vb.)."""
    attrib: Dict[str, str] = field(default_factory=dict)


@dataclass
class Panel:
    """Tek bir parça/panel ve üzerindeki tüm operasyonlar."""
    attrib: Dict[str, str] = field(default_factory=dict)
    machinings: List[Machining] = field(default_factory=list)
    edge_group_attrib: Dict[str, str] = field(default_factory=dict)
    edges: List[Edge] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "attrib": dict(self.attrib),
            "machinings": [m.to_dict() for m in self.machinings],
            "edge_group_attrib": dict(self.edge_group_attrib),
            "edges": [{"attrib": dict(e.attrib)} for e in self.edges],
        }

    @classmethod
    def from_dict(cls, data: dict) -> "Panel":
        return cls(
            attrib=dict(data.get("attrib", {})),
            machinings=[Machining.from_dict(m) for m in data.get("machinings", [])],
            edge_group_attrib=dict(data.get("edge_group_attrib", {})),
            edges=[Edge(attrib=dict(e.get("attrib", {}))) for e in data.get("edges", [])],
        )

    @property
    def name(self) -> str:
        return self.attrib.get("Name", "")

    @property
    def length(self) -> float:
        return _num(self.attrib, "Length")

    @property
    def width(self) -> float:
        return _num(self.attrib, "Width")

    @property
    def thickness(self) -> float:
        return _num(self.attrib, "Thickness")

    def add(self, machining: Machining) -> Machining:
        self.machinings.append(machining)
        return machining

    def next_id(self) -> str:
        """Mevcut ID'lerden bir sonraki ardışık makine ID'sini üretir."""
        ids = [int(m.id) for m in self.machinings if m.id.isdigit()]
        base = max(ids) if ids else 100000
        return str(base + 1)


@dataclass
class Project:
    """Bir DWD dosyasının kökü."""
    name: str = ""
    panels: List[Panel] = field(default_factory=list)
    application: str = "IngoMachine"
    application_version: str = "2.0"

    # ---- istatistik / özet ----
    def summary(self) -> Dict[str, int]:
        counts = {"panels": len(self.panels), "drill_v": 0, "drill_h": 0, "milling": 0}
        for panel in self.panels:
            for m in panel.machinings:
                if m.type == TYPE_VERTICAL_DRILL:
                    counts["drill_v"] += 1
                elif m.type == TYPE_HORIZONTAL_DRILL:
                    counts["drill_h"] += 1
                elif m.type == TYPE_MILLING:
                    counts["milling"] += 1
        return counts


# ---------------------------------------------------------------------------
# Parser
# ---------------------------------------------------------------------------

def parse(xml_text: str) -> Project:
    """DWD/IngoMachine XML metnini Project nesnesine çevirir."""
    root = ET.fromstring(xml_text)
    project = Project(
        application=root.get("Application", "IngoMachine"),
        application_version=root.get("ApplicationVersion", "2.0"),
    )

    proj_el = root.find("Project")
    if proj_el is not None:
        project.name = proj_el.get("Name", "")
        panels_el = proj_el.find("Panels")
        if panels_el is not None:
            for panel_el in panels_el.findall("Panel"):
                project.panels.append(_parse_panel(panel_el))
    return project


def _parse_panel(panel_el: ET.Element) -> Panel:
    panel = Panel(attrib=dict(panel_el.attrib))

    machines_el = panel_el.find("Machines")
    if machines_el is not None:
        for m_el in machines_el.findall("Machining"):
            machining = Machining(attrib=dict(m_el.attrib))
            lines_el = m_el.find("Lines")
            if lines_el is not None:
                for line_el in lines_el.findall("Line"):
                    machining.lines.append(Line(attrib=dict(line_el.attrib)))
            panel.machinings.append(machining)

    eg_el = panel_el.find("EdgeGroup")
    if eg_el is not None:
        panel.edge_group_attrib = dict(eg_el.attrib)
        for edge_el in eg_el.findall("Edge"):
            panel.edges.append(Edge(attrib=dict(edge_el.attrib)))

    return panel


def parse_file(path: str) -> Project:
    with open(path, "r", encoding="utf-8") as fh:
        return parse(fh.read())


# ---------------------------------------------------------------------------
# Serializer
# ---------------------------------------------------------------------------

def serialize(project: Project) -> str:
    """Project nesnesini DWD/IngoMachine XML metnine çevirir."""
    out: List[str] = ['<?xml version="1.0"?>']
    out.append(
        '<Root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xmlns:xsd="http://www.w3.org/2001/XMLSchema" '
        f'Application="{project.application}" '
        f'ApplicationVersion="{project.application_version}">'
    )
    out.append(f'  <Project Name="{_esc(project.name)}">')
    out.append("    <Panels>")
    for panel in project.panels:
        out.extend(_serialize_panel(panel))
    out.append("    </Panels>")
    out.append("  </Project>")
    out.append("</Root>")
    return "\n".join(out)


def _serialize_panel(panel: Panel) -> List[str]:
    lines: List[str] = []
    lines.append(f"      <Panel {_attrs(panel.attrib)}>")
    lines.append("        <Machines>")
    for m in panel.machinings:
        if m.lines:
            lines.append(f"          <Machining {_attrs(m.attrib)}>")
            lines.append("            <Lines>")
            for ln in m.lines:
                lines.append(f"              <Line {_attrs(ln.attrib)} />")
            lines.append("            </Lines>")
            lines.append("          </Machining>")
        else:
            lines.append(f"          <Machining {_attrs(m.attrib)} />")
    lines.append("        </Machines>")

    if panel.edge_group_attrib or panel.edges:
        lines.append(f"        <EdgeGroup {_attrs(panel.edge_group_attrib)}>")
        for edge in panel.edges:
            lines.append(f"          <Edge {_attrs(edge.attrib)} />")
        lines.append("        </EdgeGroup>")

    lines.append("      </Panel>")
    return lines


def _attrs(attrib: Dict[str, str]) -> str:
    return " ".join(f'{k}="{_esc(v)}"' for k, v in attrib.items())


def _esc(value: str) -> str:
    return (
        str(value)
        .replace("&", "&amp;")
        .replace('"', "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )


def serialize_file(project: Project, path: str) -> None:
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(serialize(project))


# ---------------------------------------------------------------------------
# Operasyon üreticiler (CAM tarafı için)
# ---------------------------------------------------------------------------

def vertical_drill(machining_id: str, x: float, y: float, diameter: float,
                   depth: float, face: int = FACE_TOP, z: float = 0.0) -> Machining:
    """Yüzeye dikey delik (Type 2). Face 5 üst, 6 alt."""
    return Machining(attrib={
        "IsGenCode": "2",
        "X": _fmt(x), "Y": _fmt(y), "Z": _fmt(z),
        "Face": _fmt(face),
        "Depth": _fmt(depth), "Diameter": _fmt(diameter),
        "ID": machining_id, "Type": _fmt(TYPE_VERTICAL_DRILL),
    })


def horizontal_drill(machining_id: str, x: float, y: float, diameter: float,
                     depth: float, face: int, z: float = 0.0) -> Machining:
    """Kenara yatay delik (Type 1). Face 1-4. Z genelde panel ortası."""
    return Machining(attrib={
        "IsGenCode": "2",
        "X": _fmt(x), "Y": _fmt(y), "Z": _fmt(z),
        "Face": _fmt(face),
        "Depth": _fmt(depth), "Diameter": _fmt(diameter),
        "ID": machining_id, "Type": _fmt(TYPE_HORIZONTAL_DRILL),
    })


def groove(machining_id: str, x: float, y: float, end_x: float, end_y: float,
           depth: float, tool_name: str, face: int = FACE_BOTTOM,
           start_z: float = 0.0, angle: float = 0.0,
           unit_tag: str = "铣型") -> Machining:
    """Yüzeye düz kanal/freze (Type 3), tek doğrusal yol ile."""
    m = Machining(attrib={
        "IsGenCode": "2", "EdgeABThickness": "0",
        "X": _fmt(x), "Y": _fmt(y), "StartZ": _fmt(start_z),
        "Depth": _fmt(depth), "ZDepth": "0", "ToolOffset": "",
        "ToolName": tool_name, "Face": _fmt(face),
        "Type": _fmt(TYPE_MILLING), "ID": machining_id,
    })
    m.lines.append(Line(attrib={
        "Angle": _fmt(angle), "UnitTag": unit_tag,
        "EndX": _fmt(end_x), "EndY": _fmt(end_y), "LineID": "1",
    }))
    return m
