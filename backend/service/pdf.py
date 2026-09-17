"""Servis raporunun A4 PDF çıktısı (mail eki ve yazdırma için)."""

import io
import os

try:
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.utils import ImageReader
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.pdfgen import canvas as pdf_canvas

    REPORTLAB_AVAILABLE = True
except ImportError:  # reportlab kurulu değilse mail PDF'siz gönderilir
    REPORTLAB_AVAILABLE = False


PAGE_W, PAGE_H = (595.27, 841.89)
MARGIN = 36
INK = (0.07, 0.13, 0.18)
MUTED = (0.36, 0.42, 0.48)
ACCENT = (0.06, 0.30, 0.46)
SOFT = (0.93, 0.95, 0.97)
BORDER = (0.83, 0.86, 0.90)

# Türkçe karakterler için TrueType font; bulunamazsa Helvetica'ya düşülür.
FONT_CANDIDATES = [
    ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
    ("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"),
    ("/Library/Fonts/Arial.ttf", "/Library/Fonts/Arial Bold.ttf"),
    ("C:/Windows/Fonts/arial.ttf", "C:/Windows/Fonts/arialbd.ttf"),
]

_ASCII_FALLBACK = str.maketrans({
    "ş": "s", "Ş": "S", "ğ": "g", "Ğ": "G", "ı": "i", "İ": "I",
    "ç": "c", "Ç": "C", "ö": "o", "Ö": "O", "ü": "u", "Ü": "U",
})

_fonts = None


def _register_fonts():
    """(normal, bold, unicode_destegi) döndürür."""
    global _fonts
    if _fonts is not None:
        return _fonts
    for regular, bold in FONT_CANDIDATES:
        if os.path.exists(regular) and os.path.exists(bold):
            try:
                pdfmetrics.registerFont(TTFont("ServisSans", regular))
                pdfmetrics.registerFont(TTFont("ServisSans-Bold", bold))
                _fonts = ("ServisSans", "ServisSans-Bold", True)
                return _fonts
            except Exception:
                continue
    _fonts = ("Helvetica", "Helvetica-Bold", False)
    return _fonts


class _Layout:
    """Yukarıdan aşağı akan, sayfa sonunda otomatik sayfa açan basit yerleşim."""

    def __init__(self, buffer, company):
        self.c = pdf_canvas.Canvas(buffer, pagesize=A4)
        self.regular, self.bold, self.unicode_ok = _register_fonts()
        self.company = company
        self.y = PAGE_H - MARGIN
        self.page = 0
        self._start_page()

    # ----------------------------------------------------------- yardımcılar

    def text_safe(self, value):
        value = "" if value is None else str(value)
        return value if self.unicode_ok else value.translate(_ASCII_FALLBACK)

    def font(self, size, bold=False):
        self.c.setFont(self.bold if bold else self.regular, size)

    def width_of(self, value, size, bold=False):
        return self.c.stringWidth(self.text_safe(value), self.bold if bold else self.regular, size)

    def wrap(self, value, size, width, bold=False):
        lines = []
        for paragraph in self.text_safe(value).split("\n"):
            if not paragraph.strip():
                lines.append("")
                continue
            current = ""
            for word in paragraph.split(" "):
                candidate = f"{current} {word}".strip()
                if self.width_of(candidate, size, bold) <= width:
                    current = candidate
                else:
                    if current:
                        lines.append(current)
                    current = word
            if current:
                lines.append(current)
        return lines or [""]

    def _start_page(self):
        self.page += 1
        self.c.setFillColorRGB(*ACCENT)
        self.c.rect(0, PAGE_H - 4, PAGE_W, 4, stroke=0, fill=1)
        self.font(7.5)
        self.c.setFillColorRGB(*MUTED)
        footer = "  •  ".join(
            p for p in (self.company.company_name, self.company.company_phone, self.company.company_email) if p
        )
        self.c.drawString(MARGIN, 24, self.text_safe(footer))
        self.c.drawRightString(PAGE_W - MARGIN, 24, f"Sayfa {self.page}")
        self.y = PAGE_H - MARGIN - 8

    def ensure(self, height):
        if self.y - height < MARGIN + 24:
            self.c.showPage()
            self._start_page()

    def write(self, value, size=9, bold=False, color=INK, x=MARGIN, width=None, leading=None):
        if not value:
            return
        width = width or (PAGE_W - 2 * MARGIN - (x - MARGIN))
        leading = leading or size + 3
        for line in self.wrap(value, size, width, bold):
            self.ensure(leading)
            self.font(size, bold)
            self.c.setFillColorRGB(*color)
            self.c.drawString(x, self.y - size, line)
            self.y -= leading

    def at(self, value, x, y, size=9, bold=False, color=INK, align="left"):
        self.font(size, bold)
        self.c.setFillColorRGB(*color)
        value = self.text_safe(value)
        if align == "right":
            self.c.drawRightString(x, y, value)
        elif align == "center":
            self.c.drawCentredString(x, y, value)
        else:
            self.c.drawString(x, y, value)

    def box(self, x, y, w, h, color, stroke=False, radius=6):
        self.c.setFillColorRGB(*color)
        self.c.setStrokeColorRGB(*color)
        self.c.roundRect(x, y, w, h, radius, stroke=1 if stroke else 0, fill=0 if stroke else 1)

    def section(self, title):
        self.ensure(24)
        self.c.setFillColorRGB(*ACCENT)
        self.c.rect(MARGIN, self.y - 13, 3, 11, stroke=0, fill=1)
        self.at(title, MARGIN + 9, self.y - 11, 8.5, True, ACCENT)
        self.y -= 20


def build_report_pdf(report, company):
    """Raporu PDF'e çevirip bytes döndürür; reportlab yoksa None."""
    if not REPORTLAB_AVAILABLE:
        return None

    buffer = io.BytesIO()
    L = _Layout(buffer, company)
    machine = report.machine

    # --------------------------------------------------------------- başlık
    top = L.y
    L.at(company.company_name or "Servis Raporu", MARGIN, top - 13, 14, True, INK)
    info_lines = [p for p in (company.company_address, company.company_phone, company.company_email, company.company_web) if p]
    for i, line in enumerate(info_lines):
        L.at(line, MARGIN, top - 26 - i * 10, 8, False, MUTED)

    L.at("SERVİS RAPORU", PAGE_W - MARGIN, top - 13, 14, True, ACCENT, "right")
    L.at(report.report_no, PAGE_W - MARGIN, top - 27, 10, True, INK, "right")
    L.at(report.service_date.strftime("%d.%m.%Y"), PAGE_W - MARGIN, top - 39, 9, False, MUTED, "right")

    L.y = top - max(52, 26 + len(info_lines) * 10) - 6
    L.c.setStrokeColorRGB(*BORDER)
    L.c.line(MARGIN, L.y, PAGE_W - MARGIN, L.y)
    L.y -= 12

    # -------------------------------------------------------------- künyeler
    col_gap = 12
    col_w = (PAGE_W - 2 * MARGIN - col_gap) / 2
    customer_rows = [
        ("Firma", report.customer.name),
        ("Yetkili", report.customer.contact_name),
        ("Telefon", report.customer.phone),
        ("E-posta", report.customer.email),
        ("Adres", " / ".join(p for p in (report.customer.address, report.customer.city) if p)),
    ]
    machine_rows = [
        ("Makine", " ".join(p for p in (machine.brand, machine.name) if p) if machine else "-"),
        ("Model", machine.model if machine else "-"),
        ("Seri No", machine.serial_no if machine else "-"),
        ("Konum", machine.location if machine else "-"),
        ("Garanti", machine.warranty_end.strftime("%d.%m.%Y") if machine and machine.warranty_end else "-"),
    ]
    value_w = col_w - 20 - 60
    wrapped = [
        [(label, L.wrap(value or "-", 8, value_w, True)[:2]) for label, value in rows]
        for rows in (customer_rows, machine_rows)
    ]
    card_h = 18 + max(sum(len(lines) for _, lines in card) for card in wrapped) * 12 + 8
    L.ensure(card_h + 10)
    card_top = L.y - card_h

    for index, (title, card) in enumerate(zip(("MÜŞTERİ", "MAKİNE"), wrapped)):
        x = MARGIN + index * (col_w + col_gap)
        L.box(x, card_top, col_w, card_h, SOFT)
        L.at(title, x + 10, card_top + card_h - 13, 8, True, ACCENT)
        line_index = 0
        for label, lines in card:
            line_y = card_top + card_h - 27 - line_index * 12
            L.at(f"{label}:", x + 10, line_y, 8, False, MUTED)
            for offset, line in enumerate(lines):
                L.at(line, x + 70, line_y - offset * 12, 8, True, INK)
            line_index += len(lines)
    L.y = card_top - 14

    # ------------------------------------------------------- servis bilgileri
    L.section("SERVİS BİLGİLERİ")
    duration = report.duration_minutes
    duration_label = "-"
    if duration:
        hours, minutes = divmod(duration, 60)
        duration_label = (f"{hours} sa " if hours else "") + (f"{minutes} dk" if minutes else "")

    items = [
        ("SERVİS TİPİ", report.get_type_display()),
        ("DURUM", report.get_status_display()),
        ("ÖNCELİK", report.get_priority_display()),
        ("TEKNİSYEN", report.technician_name or "-"),
        ("BAŞLANGIÇ", report.start_time.strftime("%H:%M") if report.start_time else "-"),
        ("BİTİŞ", report.end_time.strftime("%H:%M") if report.end_time else "-"),
        ("SÜRE", duration_label),
        ("YOL (KM)", f"{float(report.travel_km):g}"),
    ]
    cols = 4
    cell_w = (PAGE_W - 2 * MARGIN) / cols
    rows_count = (len(items) + cols - 1) // cols
    L.ensure(rows_count * 26 + 4)
    block_top = L.y
    for i, (label, value) in enumerate(items):
        cx = MARGIN + (i % cols) * cell_w
        cy = block_top - (i // cols) * 26
        L.at(label, cx, cy - 8, 6.5, False, MUTED)
        L.at(value, cx, cy - 20, 9, True, INK)
    L.y = block_top - rows_count * 26 - 6

    # ------------------------------------------------------------- bölümler
    departments = list(report.departments.all())
    if departments:
        L.section("ÇALIŞILAN BÖLÜMLER")
        for d in departments:
            label = d.get_department_display()
            chip_w = L.width_of(label, 8, True) + 14
            L.ensure(18)
            chip_y = L.y - 15
            L.box(MARGIN, chip_y, chip_w, 15, ACCENT, radius=7.5)
            L.at(label, MARGIN + 7, chip_y + 4.5, 8, True, (1, 1, 1))
            if d.work:
                saved = L.y
                L.y = saved - 2
                L.write(d.work, 8.5, x=MARGIN + chip_w + 10, width=PAGE_W - 2 * MARGIN - chip_w - 10)
                L.y = min(L.y, chip_y - 4)
            else:
                L.y = chip_y - 4

    # ------------------------------------------------------------ açıklamalar
    for title, body in (
        ("ARIZA / TALEP TANIMI", report.fault_description),
        ("ARIZA NEDENİ (KÖK NEDEN)", report.fault_cause),
        ("YAPILAN İŞLEM / ÇÖZÜM", report.work_done),
        ("ÖNERİLER", report.recommendations),
    ):
        if body:
            L.section(title)
            L.write(body, 9, leading=13)
            L.y -= 8

    # ---------------------------------------------------------- yedek parça
    parts = list(report.parts.all())
    if parts:
        L.section("YEDEK PARÇALAR")
        col_x = [MARGIN, MARGIN + 0.42 * (PAGE_W - 2 * MARGIN), MARGIN + 0.60 * (PAGE_W - 2 * MARGIN), MARGIN + 0.74 * (PAGE_W - 2 * MARGIN)]
        L.ensure(20)
        header_y = L.y - 16
        L.c.setFillColorRGB(*SOFT)
        L.c.rect(MARGIN, header_y, PAGE_W - 2 * MARGIN, 16, stroke=0, fill=1)
        for x, label in zip(col_x, ("PARÇA", "KOD", "MİKTAR", "DURUM")):
            L.at(label, x + 6, header_y + 5, 7.5, True, MUTED)
        L.y = header_y

        for part in parts:
            L.ensure(18)
            row_y = L.y - 17
            L.at(part.name, col_x[0] + 6, row_y + 5, 8.5, True, INK)
            L.at(part.code or "-", col_x[1], row_y + 5, 8.5, False, INK)
            L.at(f"{float(part.quantity):g} {part.unit}", col_x[2], row_y + 5, 8.5, False, INK)
            L.at(part.get_status_display(), col_x[3], row_y + 5, 8.5, False, INK)
            L.c.setStrokeColorRGB(*BORDER)
            L.c.line(MARGIN, row_y, PAGE_W - MARGIN, row_y)
            L.y = row_y - 2
        L.y -= 6

    # ------------------------------------------------------------ fotoğraflar
    photos = [p for p in report.photos.all() if p.image]
    if photos:
        # Başlık ile ilk fotoğraf satırı ayrı sayfalara düşmesin
        L.ensure(24 + 118 + 24)
        L.section(f"FOTOĞRAFLAR ({len(photos)})")
        gap = 10
        cell_w = (PAGE_W - 2 * MARGIN - gap) / 2
        img_h = 118
        for i in range(0, len(photos), 2):
            row = photos[i:i + 2]
            L.ensure(img_h + 24)
            row_top = L.y - img_h
            for j, photo in enumerate(row):
                x = MARGIN + j * (cell_w + gap)
                L.box(x, row_top, cell_w, img_h, SOFT, radius=4)
                try:
                    photo.image.open("rb")
                    reader = ImageReader(photo.image)
                    iw, ih = reader.getSize()
                    scale = min(cell_w / iw, img_h / ih)
                    L.c.drawImage(
                        reader, x + (cell_w - iw * scale) / 2, row_top + (img_h - ih * scale) / 2,
                        iw * scale, ih * scale, mask="auto",
                    )
                    photo.image.close()
                except Exception:
                    pass
                caption = " - ".join(p for p in (photo.get_tag_display(), photo.caption) if p)
                L.at(L.wrap(caption, 7.5, cell_w)[0], x, row_top - 10, 7.5, False, MUTED)
            L.y = row_top - 22

    # ----------------------------------------------------------------- imza
    L.ensure(96)
    sign_top = L.y - 82
    col_w = (PAGE_W - 2 * MARGIN - 20) / 2
    L.box(MARGIN, sign_top, col_w, 82, BORDER, stroke=True)
    L.at("SERVİS TEKNİSYENİ", MARGIN + 10, sign_top + 68, 7, True, MUTED)
    L.at(report.technician_name or "-", MARGIN + 10, sign_top + 8, 9, True, INK)

    rx = MARGIN + col_w + 20
    L.box(rx, sign_top, col_w, 82, BORDER, stroke=True)
    L.at("MÜŞTERİ YETKİLİSİ", rx + 10, sign_top + 68, 7, True, MUTED)
    L.at(report.customer_rep or "-", rx + 10, sign_top + 8, 9, True, INK)
    if report.signature:
        try:
            report.signature.open("rb")
            reader = ImageReader(report.signature)
            iw, ih = reader.getSize()
            scale = min((col_w - 20) / iw, 44 / ih)
            L.c.drawImage(reader, rx + 10, sign_top + 20, iw * scale, ih * scale, mask="auto")
            report.signature.close()
        except Exception:
            pass

    L.y = sign_top - 14
    L.at(
        f"Rapor No: {report.report_no} • Düzenleme: {report.updated_at.strftime('%d.%m.%Y')}",
        PAGE_W / 2, L.y, 7, False, MUTED, "center",
    )

    L.c.showPage()
    L.c.save()
    return buffer.getvalue()
