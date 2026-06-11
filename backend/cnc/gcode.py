"""
Fanuc/ISO G-code üreteci.
Membran kapak parçaları için kontur frezeleme G-kodu üretir.
"""
from typing import List
from .nesting import PlacedPiece


def generate_gcode(
    plates: List[List[PlacedPiece]],
    tool_diameter: float,
    feed_rate: int,
    spindle_speed: int,
    cut_depth: float,
    plate_width: float,
    plate_height: float,
    program_name: str = "MEMBRAN",
) -> str:
    """Tüm plakalar için G-code üretir."""
    lines = ["%"]
    prog_num = 1

    for plate_idx, pieces in enumerate(plates):
        plate_lines = _plate_gcode(
            pieces=pieces,
            plate_idx=plate_idx,
            tool_diameter=tool_diameter,
            feed_rate=feed_rate,
            spindle_speed=spindle_speed,
            cut_depth=cut_depth,
            program_name=program_name,
            prog_num=prog_num,
        )
        lines.extend(plate_lines)
        prog_num += 1

    lines.append("%")
    return "\n".join(lines)


def _plate_gcode(
    pieces: List[PlacedPiece],
    plate_idx: int,
    tool_diameter: float,
    feed_rate: int,
    spindle_speed: int,
    cut_depth: float,
    program_name: str,
    prog_num: int,
) -> List[str]:
    r = tool_diameter / 2.0
    safe_z = 50.0
    approach_z = 2.0

    lines = [
        f"O{prog_num:04d} ({program_name} PLAKA-{plate_idx + 1})",
        f"({len(pieces)} PARCA - {plate_idx + 1}. PLAKA)",
        "G21 (METRIK)",
        "G17 G40 G49 G80 G90 (BASLANGIC)",
        f"T1 M6 (FREZE D={tool_diameter}MM)",
        "G43 H1 (TAKIM BOYU KOMPANZASYONU)",
        f"S{spindle_speed} M3 (DEVIR VE YONDE)",
        f"G0 Z{safe_z:.1f} (GUVENLI YUK)",
        "",
    ]

    for i, piece in enumerate(pieces):
        # Parça konturunu frezeleme yolu (saat yönünün tersine, G41 sol kompanzasyon)
        x0 = float(piece.x) + r       # Start point (sol-alt + offset)
        y0 = float(piece.y) - r       # Yaklaşma noktası (parçanın altından)
        x1 = float(piece.x) - r       # Sol kenar (kompanzasyonlu)
        y1 = float(piece.y) - r
        x2 = float(piece.x) + float(piece.width) + r   # Sağ kenar
        y3 = float(piece.y) + float(piece.height) + r  # Üst kenar

        label = piece.label or f"PARCA-{i+1}"

        lines += [
            f"({i+1}. PARCA: {label} {float(piece.width):.1f}x{float(piece.height):.1f}mm)",
            f"G0 X{x0:.3f} Y{y0:.3f}",
            f"G0 Z{approach_z:.1f}",
            f"G1 Z-{cut_depth:.1f} F{feed_rate // 3} (DALMA)",
            f"G41 D1 (SOL TAKIM KOMPANZASYONU)",
            f"G1 Y{float(piece.y):.3f} F{feed_rate} (GIRIS)",
            f"G1 X{float(piece.x):.3f}",                                             # Sol-alt
            f"G1 Y{float(piece.y) + float(piece.height):.3f}",                      # Sol-üst
            f"G1 X{float(piece.x) + float(piece.width):.3f}",                       # Sağ-üst
            f"G1 Y{float(piece.y):.3f}",                                             # Sağ-alt
            f"G1 X{x0:.3f}",                                                          # Başa dön
            "G40 (KOMPANZASYON IPTAL)",
            f"G0 Z{safe_z:.1f}",
            "",
        ]

    lines += [
        f"G0 X0 Y0 (PARK)",
        "M5 (DEVIR DUR)",
        "M30 (PROGRAM SONU)",
        "",
    ]
    return lines
