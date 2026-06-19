"""
MaxRects (BSSF) yerleşim/optimizasyon algoritması.

nest(items, sheet_w, sheet_h, margin, allow_rotate) ->
    (placements, sheet_count, utilization_pct)
"""

COLOR_PALETTE = [
    "#3B82F6", "#EF4444", "#22C55E", "#F59E0B",
    "#8B5CF6", "#EC4899", "#06B6D4", "#84CC16",
]


def _prune(rects):
    """Başka bir dikdörtgenin içinde tamamen kalan serbest dikdörtgenleri at."""
    pruned = []
    for i, a in enumerate(rects):
        contained = False
        for j, b in enumerate(rects):
            if i == j:
                continue
            if _contains(b, a):
                # Eşitlik durumunda yalnızca bir kez atılsın diye index karşılaştır
                if a != b or j < i:
                    contained = True
                    break
        if not contained:
            pruned.append(a)
    return pruned


def _contains(outer, inner):
    ox, oy, ow, oh = outer
    ix, iy, iw, ih = inner
    return ox <= ix and oy <= iy and ox + ow >= ix + iw and oy + oh >= iy + ih


def _split_free_rects(free_rects, placed):
    """Yerleştirilen alana değen tüm serbest dikdörtgenleri parçala (MaxRects)."""
    px, py, pw, ph = placed
    result = []
    for rect in free_rects:
        fx, fy, fw, fh = rect
        # Kesişim yoksa olduğu gibi koru
        if px >= fx + fw or px + pw <= fx or py >= fy + fh or py + ph <= fy:
            result.append(rect)
            continue
        # Sol parça
        if px > fx:
            result.append((fx, fy, px - fx, fh))
        # Sağ parça
        if px + pw < fx + fw:
            result.append((px + pw, fy, fx + fw - (px + pw), fh))
        # Alt parça
        if py > fy:
            result.append((fx, fy, fw, py - fy))
        # Üst parça
        if py + ph < fy + fh:
            result.append((fx, py + ph, fw, fy + fh - (py + ph)))
    return _prune(result)


def _find_best(free_rects, pw, ph, allow_rotate):
    """BSSF: en küçük kısa-kenar artığını veren yeri bul."""
    best_rect = None
    best_score = float("inf")
    best_rot = False
    for rect in free_rects:
        _, _, rw, rh = rect
        if rw >= pw and rh >= ph:
            score = min(rw - pw, rh - ph)
            if score < best_score:
                best_score, best_rect, best_rot = score, rect, False
        if allow_rotate and rw >= ph and rh >= pw:
            score = min(rw - ph, rh - pw)
            if score < best_score:
                best_score, best_rect, best_rot = score, rect, True
    return best_rect, best_rot


def _pack_maxrects(pieces, W, H, margin, allow_rotate):
    placements = []
    free_rects_per_sheet = [[(0, 0, W, H)]]
    color_map = {}

    for idx, piece in enumerate(pieces):
        pw, ph = piece["w"] + margin, piece["h"] + margin
        placed = False

        while not placed:
            for sheet_idx, free_rects in enumerate(free_rects_per_sheet):
                best_rect, best_rot = _find_best(free_rects, pw, ph, allow_rotate)
                if best_rect is None:
                    continue
                x, y = best_rect[0], best_rect[1]
                aw = ph if best_rot else pw  # margin dahil yerleşim alanı
                ah = pw if best_rot else ph

                key = piece.get("model_id") or piece.get("label", "")
                if key not in color_map:
                    color_map[key] = COLOR_PALETTE[len(color_map) % len(COLOR_PALETTE)]

                placements.append({
                    "x": x, "y": y,
                    "w": (piece["h"] if best_rot else piece["w"]),
                    "h": (piece["w"] if best_rot else piece["h"]),
                    "sheet": sheet_idx,
                    "rotated": best_rot,
                    "label": piece["label"],
                    "color": color_map[key],
                    "group": piece.get("group", idx),
                    "model_id": piece.get("model_id"),
                })
                free_rects_per_sheet[sheet_idx] = _split_free_rects(
                    free_rects, (x, y, aw, ah)
                )
                placed = True
                break

            if not placed:
                # Hiçbir levhaya sığmadı: yeni levha aç ve tekrar dene
                free_rects_per_sheet.append([(0, 0, W, H)])
                # Parça hiçbir boş levhaya sığmıyorsa (çok büyük) sonsuz döngüyü önle
                if piece["w"] + margin > W and piece["h"] + margin > H and \
                   not (allow_rotate and piece["h"] + margin <= W and piece["w"] + margin <= H):
                    placed = True  # atla

    total_area = sum(p["w"] * p["h"] for p in pieces)
    used_sheets = (max(p["sheet"] for p in placements) + 1) if placements else 1
    sheet_area = used_sheets * W * H
    util = (total_area / sheet_area * 100) if sheet_area > 0 else 0
    return {"placements": placements, "sheet_count": used_sheets, "util": round(util, 2)}


def nest(items, sheet_w, sheet_h, margin=5, allow_rotate=False):
    # qty'leri ayrı parçalara genişlet
    pieces = []
    for gi, item in enumerate(items):
        for _ in range(int(item.get("qty", 1))):
            pieces.append({
                "w": float(item["w"]), "h": float(item["h"]),
                "label": item.get("label", ""),
                "model_id": item.get("model_id"),
                "group": gi,
            })

    if not pieces:
        return [], 0, 0.0

    strategies = [
        sorted(pieces, key=lambda p: p["w"] * p["h"], reverse=True),
        sorted(pieces, key=lambda p: p["w"] + p["h"], reverse=True),
        sorted(pieces, key=lambda p: max(p["w"], p["h"]), reverse=True),
    ]

    best = None
    for strategy in strategies:
        result = _pack_maxrects(strategy, sheet_w, sheet_h, margin, allow_rotate)
        if best is None or result["sheet_count"] < best["sheet_count"] or \
           (result["sheet_count"] == best["sheet_count"] and result["util"] > best["util"]):
            best = result

    return best["placements"], best["sheet_count"], best["util"]
