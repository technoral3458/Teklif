"""DXF içe aktarma (opsiyonel - ezdxf kuruluysa)."""
import io


def dxf_available():
    try:
        import ezdxf  # noqa: F401
        return True
    except Exception:
        return False


def parse_dxf(data: bytes):
    """DXF dosyasından kontur (line/arc) listesi çıkarır."""
    import ezdxf

    doc = ezdxf.read(io.BytesIO(data))
    msp = doc.modelspace()

    contours = []
    xs, ys = [], []
    for entity in msp:
        et = entity.dxftype()
        if et == "LINE":
            s, e = entity.dxf.start, entity.dxf.end
            contours.append({"type": "line", "x1": s[0], "y1": s[1],
                             "x2": e[0], "y2": e[1], "layer": entity.dxf.layer})
            xs += [s[0], e[0]]; ys += [s[1], e[1]]
        elif et == "ARC":
            c = entity.dxf.center
            contours.append({"type": "arc", "cx": c[0], "cy": c[1],
                             "r": entity.dxf.radius,
                             "start_angle": entity.dxf.start_angle,
                             "end_angle": entity.dxf.end_angle,
                             "layer": entity.dxf.layer})
            xs += [c[0] - entity.dxf.radius, c[0] + entity.dxf.radius]
            ys += [c[1] - entity.dxf.radius, c[1] + entity.dxf.radius]
        elif et == "LWPOLYLINE":
            pts = list(entity.get_points("xy"))
            for i in range(len(pts) - 1):
                contours.append({"type": "line", "x1": pts[i][0], "y1": pts[i][1],
                                 "x2": pts[i + 1][0], "y2": pts[i + 1][1],
                                 "layer": entity.dxf.layer})
            for p in pts:
                xs.append(p[0]); ys.append(p[1])

    width = (max(xs) - min(xs)) if xs else 0
    height = (max(ys) - min(ys)) if ys else 0
    layers = sorted({c["layer"] for c in contours})
    return {"contours": contours, "width": round(width, 2),
            "height": round(height, 2), "layers": layers}
