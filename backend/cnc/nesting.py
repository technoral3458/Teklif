"""
2D Rectangle Bin Packing - Guillotine Algorithm
Membran kapak parçalarını plakaya yerleştirir.
"""
from dataclasses import dataclass, field
from typing import List, Tuple, Optional


@dataclass
class Piece:
    id: str
    width: float
    height: float
    label: str = ""


@dataclass
class PlacedPiece:
    id: str
    x: float
    y: float
    width: float
    height: float
    rotated: bool
    label: str = ""
    plate_index: int = 0


@dataclass
class FreeRect:
    x: float
    y: float
    width: float
    height: float

    @property
    def area(self):
        return self.width * self.height


class GuillotineNester:
    """
    Guillotine cut 2D bin packing.
    Her kesimden sonra kalan alanı iki dikdörtgene böler.
    MINSHORT split rule kullanır (kısa kenar tarafını böl).
    """

    def __init__(self, plate_width: float, plate_height: float, kerf: float = 4.0, allow_rotation: bool = True):
        self.plate_width = plate_width
        self.plate_height = plate_height
        self.kerf = kerf  # Freze boşluğu
        self.allow_rotation = allow_rotation

    def nest(self, pieces: List[Piece]) -> Tuple[List[PlacedPiece], List[Piece], float]:
        """
        Parçaları plakaya yerleştirir.
        Returns: (yerleştirilen parçalar, yerleştirilemeyen parçalar, verimlilik %)
        """
        # Büyükten küçüğe sırala (alan bazında)
        sorted_pieces = sorted(pieces, key=lambda p: p.width * p.height, reverse=True)

        placed: List[PlacedPiece] = []
        unplaced: List[Piece] = []
        free_rects = [FreeRect(0, 0, self.plate_width, self.plate_height)]

        for piece in sorted_pieces:
            result = self._find_best_fit(piece, free_rects)
            if result is None:
                unplaced.append(piece)
                continue

            rect, rotated = result
            w = piece.height if rotated else piece.width
            h = piece.width if rotated else piece.height

            placed.append(PlacedPiece(
                id=piece.id,
                x=rect.x,
                y=rect.y,
                width=w,
                height=h,
                rotated=rotated,
                label=piece.label,
            ))

            # Alanı böl
            free_rects = self._split(free_rects, rect, w + self.kerf, h + self.kerf)
            free_rects = self._merge_free_rects(free_rects)

        total_area = self.plate_width * self.plate_height
        used_area = sum(p.width * p.height for p in placed)
        efficiency = (used_area / total_area * 100) if total_area > 0 else 0

        return placed, unplaced, round(efficiency, 2)

    def _find_best_fit(self, piece: Piece, free_rects: List[FreeRect]) -> Optional[Tuple[FreeRect, bool]]:
        """En iyi sığan serbest dikdörtgeni bul (Best Short Side Fit)."""
        best_rect = None
        best_rotated = False
        best_short_side = float("inf")

        for rect in free_rects:
            # Normal yön
            if piece.width <= rect.width and piece.height <= rect.height:
                short_side = min(rect.width - piece.width, rect.height - piece.height)
                if short_side < best_short_side:
                    best_short_side = short_side
                    best_rect = rect
                    best_rotated = False

            # 90 derece döndürülmüş
            if self.allow_rotation and piece.height <= rect.width and piece.width <= rect.height:
                short_side = min(rect.width - piece.height, rect.height - piece.width)
                if short_side < best_short_side:
                    best_short_side = short_side
                    best_rect = rect
                    best_rotated = True

        return (best_rect, best_rotated) if best_rect else None

    def _split(self, free_rects: List[FreeRect], used: FreeRect, w: float, h: float) -> List[FreeRect]:
        """Kullanılan alanı çıkarıp geriye kalan serbest dikdörtgenleri üret."""
        new_free = []
        for rect in free_rects:
            if rect == used:
                # Bu dikdörtgeni böl
                # Sağ kısım
                if rect.width - w > 0:
                    new_free.append(FreeRect(
                        x=rect.x + w,
                        y=rect.y,
                        width=rect.width - w,
                        height=h,
                    ))
                # Üst kısım
                if rect.height - h > 0:
                    new_free.append(FreeRect(
                        x=rect.x,
                        y=rect.y + h,
                        width=rect.width,
                        height=rect.height - h,
                    ))
            else:
                new_free.append(rect)
        return new_free

    def _merge_free_rects(self, free_rects: List[FreeRect]) -> List[FreeRect]:
        """Küçük ve kullanımsız alanları temizle."""
        return [r for r in free_rects if r.width > self.kerf and r.height > self.kerf]


def nest_multiple_plates(
    pieces: List[Piece],
    plate_width: float,
    plate_height: float,
    kerf: float = 4.0,
    allow_rotation: bool = True,
    max_plates: int = 20,
) -> Tuple[List[List[PlacedPiece]], float]:
    """
    Parçaları birden fazla plakaya yerleştirir.
    Returns: (her plaka için yerleştirilmiş parça listesi, ortalama verimlilik)
    """
    remaining = list(pieces)
    all_plates: List[List[PlacedPiece]] = []
    total_efficiency = 0.0

    for plate_num in range(max_plates):
        if not remaining:
            break

        nester = GuillotineNester(plate_width, plate_height, kerf, allow_rotation)
        placed, unplaced, efficiency = nester.nest(remaining)

        if not placed:
            break

        for p in placed:
            p.plate_index = plate_num

        all_plates.append(placed)
        total_efficiency += efficiency
        remaining = unplaced

    avg_efficiency = total_efficiency / len(all_plates) if all_plates else 0
    return all_plates, round(avg_efficiency, 2)
