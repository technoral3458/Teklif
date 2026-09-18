"""Güncel döviz kurunu internetten çeker.

Birincil kaynak TCMB'nin günlük kur bülteni (döviz satış kuru). Bülten hafta
sonu ve resmî tatillerde yayımlanmadığı için son yayımlanan güne kadar geriye
gidilir. TCMB'ye ulaşılamazsa ECB verisini sunan frankfurter.app kullanılır.
"""

import datetime
import json
import re
import urllib.request
from decimal import Decimal, InvalidOperation

TIMEOUT = 15
TCMB_TODAY = "https://www.tcmb.gov.tr/kurlar/today.xml"
USER_AGENT = "TeknoServis/1.0"


def _download(url, charset):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        if response.status != 200:
            return None
        return response.read().decode(charset, errors="replace")


def _to_decimal(value):
    try:
        return Decimal(value.strip().replace(",", "."))
    except (InvalidOperation, AttributeError):
        return None


def parse_tcmb_rate(xml, code):
    """Bir para biriminin TL satış kurunu döndürür; `Unit` alanına göre normalize eder."""
    match = re.search(r'<Currency[^>]*Kod="%s"[^>]*>(.*?)</Currency>' % code, xml, re.S)
    if not match:
        return None
    block = match.group(1)

    unit_match = re.search(r"<Unit>\s*([\d.,]+)\s*</Unit>", block)
    unit = _to_decimal(unit_match.group(1)) if unit_match else Decimal("1")
    if not unit or unit <= 0:
        unit = Decimal("1")

    for tag in ("ForexSelling", "BanknoteSelling", "ForexBuying", "BanknoteBuying"):
        value_match = re.search(r"<%s>\s*([\d.,]+)\s*</%s>" % (tag, tag), block)
        if value_match:
            value = _to_decimal(value_match.group(1))
            if value and value > 0:
                return value / unit
    return None


def _tcmb():
    urls = [TCMB_TODAY]
    day = datetime.date.today()
    for _ in range(10):
        urls.append(
            f"https://www.tcmb.gov.tr/kurlar/{day.strftime('%Y%m')}/{day.strftime('%d%m%Y')}.xml"
        )
        day -= datetime.timedelta(days=1)

    for url in urls:
        try:
            xml = _download(url, "iso-8859-9")
        except Exception:
            continue
        if not xml:
            continue
        usd = parse_tcmb_rate(xml, "USD")
        eur = parse_tcmb_rate(xml, "EUR")
        if usd and eur:
            date_match = re.search(r'Tarih="([^"]+)"', xml)
            return {
                "usd": usd,
                "eur": eur,
                "date": date_match.group(1) if date_match else "",
                "source": "TCMB döviz satış",
            }
    return None


def _frankfurter():
    try:
        raw = _download("https://api.frankfurter.app/latest?from=TRY&to=USD,EUR", "utf-8")
    except Exception:
        return None
    if not raw:
        return None
    try:
        data = json.loads(raw)
    except ValueError:
        return None
    rates = data.get("rates", {})
    usd_per_try = rates.get("USD")
    eur_per_try = rates.get("EUR")
    if not usd_per_try or not eur_per_try:
        return None
    return {
        # TRY bazlı geldiği için ters çevriliyor: 1 USD = 1 / (USD/TRY)
        "usd": Decimal("1") / Decimal(str(usd_per_try)),
        "eur": Decimal("1") / Decimal(str(eur_per_try)),
        "date": data.get("date", ""),
        "source": "ECB (frankfurter.app)",
    }


def fetch_rates():
    """(kurlar, hata) döndürür."""
    quote = _tcmb() or _frankfurter()
    if quote is None:
        return None, "Kur bilgisi alınamadı. Sunucunun internet erişimini kontrol edin."
    return quote, None
