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


def build_finance_pdf(summary, company, overdue, accounts):
    """Aylık gelir-gider ve alacak raporunun PDF çıktısı."""
    if not REPORTLAB_AVAILABLE:
        return None

    buffer = io.BytesIO()
    L = _Layout(buffer, company)

    def fmt(value):
        return f"{float(value):,.2f} TL".replace(",", "@").replace(".", ",").replace("@", ".")

    # --------------------------------------------------------------- başlık
    top = L.y
    L.at(company.company_name or "Servis", MARGIN, top - 13, 14, True, INK)
    L.at("Aylık Finans Raporu", MARGIN, top - 27, 9, False, MUTED)
    L.at(summary["label"], PAGE_W - MARGIN, top - 13, 14, True, ACCENT, "right")
    L.y = top - 40
    L.c.setStrokeColorRGB(*BORDER)
    L.c.line(MARGIN, L.y, PAGE_W - MARGIN, L.y)
    L.y -= 12

    # ---------------------------------------------------------- özet kutular
    green = (0.11, 0.50, 0.29)
    red = (0.70, 0.15, 0.12)
    amber = (0.70, 0.42, 0.0)

    def tiles(items):
        gap = 8
        cell_w = (PAGE_W - 2 * MARGIN - gap * 3) / 4
        L.ensure(54)
        block_top = L.y - 46
        for i, (label, value, color) in enumerate(items):
            x = MARGIN + i * (cell_w + gap)
            L.box(x, block_top, cell_w, 46, SOFT)
            L.at(label, x + 10, block_top + 31, 6.5, False, MUTED)
            L.at(value, x + 10, block_top + 13, 11, True, color)
        L.y = block_top - 8

    tiles([
        ("HAKEDİŞ", fmt(summary["income_try"]), ACCENT),
        ("TAHSİLAT", fmt(summary["collected_try"]), green),
        ("MASRAF", fmt(summary["expense_try"]), amber),
        ("NET KÂR", fmt(summary["net_try"]), green if summary["net_try"] >= 0 else red),
    ])
    tiles([
        ("KASA AKIŞI", fmt(summary["cash_flow_try"]), green if summary["cash_flow_try"] >= 0 else red),
        ("SERVİS SAYISI", str(summary["service_count"]), INK),
        ("YAKIT", f"{float(summary['fuel_liters']):g} lt" if summary["fuel_liters"] else "-", INK),
        ("YANSITILACAK MASRAF", fmt(summary["billable_expense_try"]), MUTED),
    ])

    # ------------------------------------------------------ masraf dağılımı
    if summary["expense_by_category"]:
        L.section("MASRAF DAĞILIMI")
        max_value = max(float(row["amount"]) for row in summary["expense_by_category"]) or 1
        for row in summary["expense_by_category"]:
            L.ensure(20)
            row_top = L.y - 17
            L.at(row["label"], MARGIN, row_top + 5, 8.5, False, INK)
            bar_left = MARGIN + 150
            bar_width = PAGE_W - 2 * MARGIN - 150 - 90
            L.c.setFillColorRGB(*SOFT)
            L.c.roundRect(bar_left, row_top + 2, bar_width, 9, 4.5, stroke=0, fill=1)
            filled = max(2, bar_width * float(row["amount"]) / max_value)
            L.c.setFillColorRGB(*ACCENT)
            L.c.roundRect(bar_left, row_top + 2, filled, 9, 4.5, stroke=0, fill=1)
            L.at(fmt(row["amount"]), PAGE_W - MARGIN, row_top + 5, 8.5, True, INK, "right")
            L.y = row_top
        L.y -= 8

    # -------------------------------------------------------- müşteri bazında
    if summary["by_customer"]:
        L.section("MÜŞTERİ BAZINDA")
        L.ensure(20)
        header_top = L.y - 16
        L.c.setFillColorRGB(*SOFT)
        L.c.rect(MARGIN, header_top, PAGE_W - 2 * MARGIN, 16, stroke=0, fill=1)
        L.at("MÜŞTERİ", MARGIN + 6, header_top + 5, 7.5, True, MUTED)
        L.at("HAKEDİŞ", PAGE_W - MARGIN - 110, header_top + 5, 7.5, True, MUTED, "right")
        L.at("TAHSİLAT", PAGE_W - MARGIN, header_top + 5, 7.5, True, MUTED, "right")
        L.y = header_top
        for row in summary["by_customer"]:
            L.ensure(18)
            row_top = L.y - 16
            L.at(row["customer"], MARGIN + 6, row_top + 5, 8.5, True, INK)
            L.at(fmt(row["income"]), PAGE_W - MARGIN - 110, row_top + 5, 8.5, False, INK, "right")
            L.at(fmt(row["collected"]), PAGE_W - MARGIN, row_top + 5, 8.5, False, green, "right")
            L.c.setStrokeColorRGB(*BORDER)
            L.c.line(MARGIN, row_top, PAGE_W - MARGIN, row_top)
            L.y = row_top - 2
        L.y -= 6

    # --------------------------------------------------------- açık alacaklar
    open_accounts = [a for a in accounts if a["balance_try"] > 0]
    if open_accounts:
        L.section("AÇIK ALACAKLAR")
        for account in sorted(open_accounts, key=lambda a: a["balance_try"], reverse=True):
            L.ensure(16)
            row_top = L.y - 15
            L.at(account["customer"].name, MARGIN + 6, row_top + 4, 8.5, False, INK)
            L.at(fmt(account["balance_try"]), PAGE_W - MARGIN, row_top + 4, 8.5, True, amber, "right")
            L.c.setStrokeColorRGB(*BORDER)
            L.c.line(MARGIN, row_top, PAGE_W - MARGIN, row_top)
            L.y = row_top - 2
        L.ensure(18)
        total_top = L.y - 16
        L.at("TOPLAM", MARGIN + 6, total_top + 5, 9, True, INK)
        L.at(
            fmt(sum(a["balance_try"] for a in open_accounts)),
            PAGE_W - MARGIN, total_top + 5, 9, True, INK, "right",
        )
        L.y = total_top - 8

    # --------------------------------------------------------- vadesi geçen
    if overdue:
        L.section("ÖDEMESİ GECİKEN ALACAKLAR")
        for row in overdue:
            L.ensure(26)
            row_top = L.y - 24
            L.c.setFillColorRGB(*red)
            L.c.roundRect(MARGIN, row_top + 4, 3, 18, 1.5, stroke=0, fill=1)
            L.at(row["customer"].name, MARGIN + 10, row_top + 15, 8.5, True, INK)
            deadline = row["deadline"].strftime("%d.%m.%Y") if row["deadline"] else "-"
            prefix = "Söz verilen tarih: " if row["broken_promise"] else "Vade: "
            L.at(
                f"{prefix}{deadline}  •  {row['days_late']} gün gecikme",
                MARGIN + 10, row_top + 5, 7.5, False, red,
            )
            L.at(fmt(row["open_try"]), PAGE_W - MARGIN, row_top + 10, 9, True, red, "right")
            L.c.setStrokeColorRGB(*BORDER)
            L.c.line(MARGIN, row_top, PAGE_W - MARGIN, row_top)
            L.y = row_top - 2

    L.c.showPage()
    L.c.save()
    return buffer.getvalue()


def build_expense_pdf(report, company):
    """Masraf dökümü: kalem listesi, toplamlar ve tüm fiş fotoğrafları tek PDF'te."""
    if not REPORTLAB_AVAILABLE:
        return None

    from decimal import Decimal

    buffer = io.BytesIO()
    L = _Layout(buffer, company)
    green = (0.11, 0.50, 0.29)

    def fmt(value, symbol="TL"):
        text = f"{float(value):,.2f}".replace(",", "@").replace(".", ",").replace("@", ".")
        return f"{text} {symbol}"

    expenses = list(report.expenses.all().order_by("date", "id"))
    charge = report.service_charge
    total = sum((e.try_amount for e in expenses), Decimal("0"))
    billable = sum((e.try_amount for e in expenses if e.billable), Decimal("0"))
    service_fee = charge.try_amount if charge else Decimal("0")

    # --------------------------------------------------------------- başlık
    top = L.y
    L.at(company.company_name or "Masraf Dökümü", MARGIN, top - 13, 14, True, INK)
    info = [p for p in (company.company_address, company.company_phone, company.company_email) if p]
    for i, line in enumerate(info):
        L.at(line, MARGIN, top - 26 - i * 10, 8, False, MUTED)

    L.at("MASRAF DÖKÜMÜ", PAGE_W - MARGIN, top - 13, 14, True, ACCENT, "right")
    L.at(report.report_no, PAGE_W - MARGIN, top - 27, 10, True, INK, "right")
    L.at(report.service_date.strftime("%d.%m.%Y"), PAGE_W - MARGIN, top - 39, 9, False, MUTED, "right")

    L.y = top - max(52, 26 + len(info) * 10) - 6
    L.c.setStrokeColorRGB(*BORDER)
    L.c.line(MARGIN, L.y, PAGE_W - MARGIN, L.y)
    L.y -= 12

    machine = report.machine
    rows = [("Müşteri", report.customer.name)]
    if machine:
        rows.append(("Makine", " ".join(p for p in (machine.brand, machine.name) if p) or machine.model))
        if machine.serial_no:
            rows.append(("Seri No", machine.serial_no))
    card_h = 12 + len(rows) * 12 + 8
    L.ensure(card_h + 10)
    card_top = L.y - card_h
    L.box(MARGIN, card_top, PAGE_W - 2 * MARGIN, card_h, SOFT)
    for i, (label, value) in enumerate(rows):
        line_y = card_top + card_h - 20 - i * 12
        L.at(f"{label}:", MARGIN + 10, line_y, 8, False, MUTED)
        L.at(value, MARGIN + 78, line_y, 8, True, INK)
    L.y = card_top - 12

    # ---------------------------------------------------------- kalem tablosu
    L.section(f"MASRAF KALEMLERİ ({len(expenses)})")
    col_no = MARGIN + 6
    col_category = MARGIN + 26
    col_date = MARGIN + 0.42 * (PAGE_W - 2 * MARGIN)
    col_state = MARGIN + 0.58 * (PAGE_W - 2 * MARGIN)

    L.ensure(20)
    header_top = L.y - 16
    L.c.setFillColorRGB(*SOFT)
    L.c.rect(MARGIN, header_top, PAGE_W - 2 * MARGIN, 16, stroke=0, fill=1)
    L.at("#", col_no, header_top + 5, 7.5, True, MUTED)
    L.at("KALEM", col_category, header_top + 5, 7.5, True, MUTED)
    L.at("TARİH", col_date, header_top + 5, 7.5, True, MUTED)
    L.at("DURUM", col_state, header_top + 5, 7.5, True, MUTED)
    L.at("TUTAR", PAGE_W - MARGIN, header_top + 5, 7.5, True, MUTED, "right")
    L.y = header_top

    for index, expense in enumerate(expenses, start=1):
        detail_parts = [expense.description] if expense.description else []
        if expense.category == "YAKIT" and expense.quantity:
            detail_parts.append(f"{float(expense.quantity):g} lt")
        if expense.receipt:
            detail_parts.append("fiş ekli")
        detail = " • ".join(detail_parts)
        row_h = 28 if detail else 18

        L.ensure(row_h)
        row_top = L.y - row_h
        L.at(str(index), col_no, row_top + row_h - 12, 8.5, False, MUTED)
        L.at(expense.get_category_display(), col_category, row_top + row_h - 12, 8.5, True, INK)
        L.at(expense.date.strftime("%d.%m.%Y"), col_date, row_top + row_h - 12, 8.5, False, INK)
        L.at(
            "Yansıtıldı" if expense.billable else "Yansıtılmadı",
            col_state, row_top + row_h - 12, 8, False, green if expense.billable else MUTED,
        )
        symbol = {"TRY": "TL", "USD": "$", "EUR": "€"}.get(expense.currency, expense.currency)
        L.at(fmt(expense.amount, symbol), PAGE_W - MARGIN, row_top + row_h - 12, 8.5, True, INK, "right")
        if detail:
            L.at(detail, col_category, row_top + 4, 7.5, False, MUTED)
        L.c.setStrokeColorRGB(*BORDER)
        L.c.line(MARGIN, row_top, PAGE_W - MARGIN, row_top)
        L.y = row_top

    # ------------------------------------------------------------- toplamlar
    L.y -= 8
    summary = [("Toplam masraf", fmt(total), INK)]
    if billable != total:
        summary.append(("Müşteriye yansıtılan", fmt(billable), green))
    if service_fee:
        if charge and charge.currency != "TRY":
            symbol = {"USD": "$", "EUR": "€"}.get(charge.currency, charge.currency)
            summary.append(("Servis bedeli", f"{fmt(charge.amount, symbol)} = {fmt(service_fee)}", INK))
        else:
            summary.append(("Servis bedeli", fmt(service_fee), INK))

    L.ensure(len(summary) * 16 + 36)
    for label, value, color in summary:
        row_top = L.y - 16
        L.at(label, MARGIN + 6, row_top + 5, 9, False, MUTED)
        L.at(value, PAGE_W - MARGIN, row_top + 5, 9, True, color, "right")
        L.y = row_top

    band_top = L.y - 28
    L.c.setFillColorRGB(*ACCENT)
    L.c.roundRect(MARGIN, band_top, PAGE_W - 2 * MARGIN, 26, 6, stroke=0, fill=1)
    L.at("MÜŞTERİYE TOPLAM", MARGIN + 12, band_top + 9, 9, True, (1, 1, 1))
    L.at(fmt(service_fee + billable), PAGE_W - MARGIN - 12, band_top + 8, 12, True, (1, 1, 1), "right")
    L.y = band_top - 10

    # ---------------------------------------------------------------- fişler
    with_receipt = [e for e in expenses if e.receipt]
    if not with_receipt:
        L.y -= 6
        L.at("Bu servise ait masraf fişi fotoğrafı eklenmemiştir.", MARGIN, L.y, 8, False, MUTED)
    else:
        L.c.showPage()
        L._start_page()
        L.section(f"MASRAF FİŞLERİ ({len(with_receipt)})")
        image_h = 320
        for expense in with_receipt:
            index = expenses.index(expense) + 1
            L.ensure(image_h + 34)
            box_top = L.y - image_h
            L.box(MARGIN, box_top, PAGE_W - 2 * MARGIN, image_h, SOFT)
            try:
                expense.receipt.open("rb")
                reader = ImageReader(expense.receipt)
                iw, ih = reader.getSize()
                scale = min((PAGE_W - 2 * MARGIN) / iw, image_h / ih)
                L.c.drawImage(
                    reader,
                    MARGIN + ((PAGE_W - 2 * MARGIN) - iw * scale) / 2,
                    box_top + (image_h - ih * scale) / 2,
                    iw * scale, ih * scale, mask="auto",
                )
                expense.receipt.close()
            except Exception:
                pass

            caption_parts = [f"{index} — {expense.get_category_display()}", expense.date.strftime("%d.%m.%Y")]
            if expense.description:
                caption_parts.append(expense.description)
            L.at("  •  ".join(caption_parts), MARGIN, box_top - 13, 8.5, True, INK)
            symbol = {"TRY": "TL", "USD": "$", "EUR": "€"}.get(expense.currency, expense.currency)
            label = fmt(expense.amount, symbol)
            if expense.currency != "TRY":
                label += f"  ({fmt(expense.try_amount)})"
            L.at(label, PAGE_W - MARGIN, box_top - 13, 8.5, True, ACCENT, "right")
            L.y = box_top - 24

        missing = len(expenses) - len(with_receipt)
        if missing:
            L.y -= 6
            L.at(f"{missing} kalem için fiş fotoğrafı eklenmemiştir.", MARGIN, L.y, 7.5, False, MUTED)

    L.c.showPage()
    L.c.save()
    return buffer.getvalue()
