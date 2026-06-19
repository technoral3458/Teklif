"""
Fanuc/Haas uyumlu NC (G-code) üretimi.

- Parametrik ifadeler expr.eval_expr ile çözülür.
- Referans köşeye (BL/BR/TL/TR) göre koordinat dönüşümü uygulanır.
- Yaylarda I/J = merkez - başlangıç (artımlı), Fanuc standardı.
- Aynı anda simülasyon için yol listesi (paths) üretilir.
"""
from expr import eval_expr, build_variables


def xform(x, y, ref, W, H):
    """Op-yerel koordinatı parça-yerel koordinata çevirir (referans köşeye göre)."""
    if ref == "BR":
        return W - x, y
    if ref == "TL":
        return x, H - y
    if ref == "TR":
        return W - x, H - y
    return x, y  # BL (varsayılan)


def _mirrored(ref):
    """Tek eksende ayna olan köşelerde yay yönü ters çevrilmeli."""
    return ref in ("BR", "TL")


def _f(value):
    return f"{value:.3f}"


def generate_op(op, moves, variables, W, H, x_off=0.0, y_off=0.0,
                feed_xy=3000, feed_z=1000, safe_z=5.0,
                paths=None, lines=None):
    """Tek bir operasyonun NC satırlarını ve simülasyon yollarını üretir."""
    lines = lines if lines is not None else []
    paths = paths if paths is not None else []

    ref = op.get("ref_corner", "BL")
    flip = _mirrored(ref)
    depth = eval_expr(op.get("depth", "-T"), variables)
    feed = eval_expr(op["feed"], variables) if op.get("feed") else feed_xy
    if feed <= 0:
        feed = feed_xy

    comp = op.get("comp_mode", "none")

    lines.append(f"(OP: {op.get('name', '')} - {op.get('op_type', 'inner')})")
    lines.append(f"T{op.get('tool_no', 1)} M6")
    lines.append(f"G0 Z{_f(safe_z)}")

    cur_x = cur_y = None
    sorted_moves = sorted(moves, key=lambda m: m.get("seq", 0))

    for move in sorted_moves:
        mt = move.get("move_type", "line")
        lx = eval_expr(move.get("x", "0"), variables)
        ly = eval_expr(move.get("y", "0"), variables)
        tx, ty = xform(lx, ly, ref, W, H)
        X, Y = tx + x_off, ty + y_off

        if mt == "start":
            lines.append(f"G0 X{_f(X)} Y{_f(Y)}")
            if comp in ("G41", "G42"):
                lines.append(f"{comp} D{op.get('tool_no', 1)}")
            lines.append(f"G1 Z{_f(depth)} F{int(feed_z)}")
            paths.append({"type": "rapid", "x1": x_off, "y1": y_off, "x2": X, "y2": Y})
            cur_x, cur_y = X, Y

        elif mt == "line":
            lines.append(f"G1 X{_f(X)} Y{_f(Y)} F{int(feed)}")
            if cur_x is not None:
                paths.append({"type": "cut", "x1": cur_x, "y1": cur_y, "x2": X, "y2": Y})
            cur_x, cur_y = X, Y

        elif mt in ("arc_cw", "arc_ccw"):
            lcx = eval_expr(move.get("cx", "0"), variables)
            lcy = eval_expr(move.get("cy", "0"), variables)
            tcx, tcy = xform(lcx, lcy, ref, W, H)
            CX, CY = tcx + x_off, tcy + y_off
            g = "G2" if mt == "arc_cw" else "G3"
            if flip:
                g = "G3" if g == "G2" else "G2"
            if cur_x is not None:
                i_val, j_val = CX - cur_x, CY - cur_y
            else:
                i_val, j_val = 0.0, 0.0
            lines.append(f"{g} X{_f(X)} Y{_f(Y)} I{_f(i_val)} J{_f(j_val)} F{int(feed)}")
            paths.append({"type": "arc", "x1": cur_x or X, "y1": cur_y or Y,
                          "x2": X, "y2": Y, "cx": CX, "cy": CY, "g": g})
            cur_x, cur_y = X, Y

        elif mt in ("arc_cw_r", "arc_ccw_r"):
            r = eval_expr(move.get("r", "0"), variables)
            g = "G2" if mt == "arc_cw_r" else "G3"
            if flip:
                g = "G3" if g == "G2" else "G2"
            lines.append(f"{g} X{_f(X)} Y{_f(Y)} R{_f(r)} F{int(feed)}")
            paths.append({"type": "arc", "x1": cur_x or X, "y1": cur_y or Y,
                          "x2": X, "y2": Y, "r": r, "g": g})
            cur_x, cur_y = X, Y

        elif mt == "arc_3p":
            # 3 noktalı yay: orta nokta (cx,cy) üzerinden geçer; basitçe iki çizgi
            lcx = eval_expr(move.get("cx", "0"), variables)
            lcy = eval_expr(move.get("cy", "0"), variables)
            mx, my = xform(lcx, lcy, ref, W, H)
            MX, MY = mx + x_off, my + y_off
            lines.append(f"G1 X{_f(MX)} Y{_f(MY)} F{int(feed)}")
            lines.append(f"G1 X{_f(X)} Y{_f(Y)} F{int(feed)}")
            if cur_x is not None:
                paths.append({"type": "cut", "x1": cur_x, "y1": cur_y, "x2": MX, "y2": MY})
            paths.append({"type": "cut", "x1": MX, "y1": MY, "x2": X, "y2": Y})
            cur_x, cur_y = X, Y

    if comp in ("G41", "G42"):
        lines.append("G40")
    lines.append(f"G0 Z{_f(safe_z)}")
    return lines, paths


def generate_model_nc(model, ops, moves_by_op, W, H, T, prog_no=1, x_off=0.0, y_off=0.0):
    """Tek bir model için tam NC programı + simülasyon üretir."""
    constants = model.get("_constants", {})
    variables = build_variables(W, H, T, constants)
    safe_z = float(model.get("safe_z", 5.0))
    feed_xy = int(model.get("feed_xy", 3000))
    feed_z = int(model.get("feed_z", 1000))
    spindle = int(model.get("spindle_speed", 18000))

    lines = [f"O{prog_no:04d}", f"(MODEL: {model.get('name', '')})",
             "G21", "G90 G54", f"S{spindle} M3", f"G0 Z{_f(safe_z)}"]
    paths = []

    for op in sorted(ops, key=lambda o: o.get("seq", 0)):
        generate_op(op, moves_by_op.get(op["id"], []), variables, W, H,
                    x_off=x_off, y_off=y_off, feed_xy=feed_xy, feed_z=feed_z,
                    safe_z=safe_z, paths=paths, lines=lines)

    lines.append(f"G0 Z{_f(safe_z)}")
    lines.append("M5")
    lines.append("M30")
    nc = "\n".join(lines)
    sim = {"paths": paths, "safe_z": safe_z}
    return nc, sim


def generate_job_nc(job, placements, models_data, prog_no=1):
    """
    Nesting işi için 2-geçişli NC (önce tüm inner, sonra tüm outer).

    models_data[model_id] = {"model": {...}, "ops": [...], "moves": {op_id: [moves]}}
    placement: {"x","y","w","h","model_id","label", ...}
    """
    safe_z = 5.0
    spindle = 18000
    for md in models_data.values():
        safe_z = float(md["model"].get("safe_z", 5.0))
        spindle = int(md["model"].get("spindle_speed", 18000))
        break

    lines = [f"O{prog_no:04d}", f"(NESTING: {job.get('name', '')})",
             "G21", "G90 G54", f"S{spindle} M3", f"G0 Z{safe_z:.3f}"]
    paths = []

    for phase in ("inner", "outer"):
        lines.append(f"(=== GECIS: {phase.upper()} ===)")
        for pl in placements:
            md = models_data.get(pl.get("model_id"))
            if not md:
                continue
            model = md["model"]
            variables = build_variables(pl["w"], pl["h"],
                                        model.get("_T", 18), model.get("_constants", {}))
            for op in sorted(md["ops"], key=lambda o: o.get("seq", 0)):
                if op.get("op_type", "inner") != phase:
                    continue
                generate_op(op, md["moves"].get(op["id"], []), variables,
                            pl["w"], pl["h"], x_off=pl["x"], y_off=pl["y"],
                            feed_xy=int(model.get("feed_xy", 3000)),
                            feed_z=int(model.get("feed_z", 1000)),
                            safe_z=float(model.get("safe_z", 5.0)),
                            paths=paths, lines=lines)

    lines.append(f"G0 Z{safe_z:.3f}")
    lines.append("M5")
    lines.append("M30")
    return "\n".join(lines), {"paths": paths, "safe_z": safe_z}
