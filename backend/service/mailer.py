"""Servis raporlarının müşteriye mail ile gönderilmesi.

SMTP bilgileri veritabanındaki MailSettings kaydından okunur; böylece sunucu
ayarları Django settings dosyasına dokunmadan arayüzden yönetilebilir.
"""

from django.core.mail import EmailMultiAlternatives, get_connection
from django.utils import timezone
from django.utils.html import escape

from .models import MailSettings
from .pdf import build_report_pdf


def build_connection(settings_obj: MailSettings):
    return get_connection(
        backend="django.core.mail.backends.smtp.EmailBackend",
        host=settings_obj.host,
        port=settings_obj.port,
        username=settings_obj.username or None,
        password=settings_obj.password or None,
        use_tls=settings_obj.security == "STARTTLS",
        use_ssl=settings_obj.security == "SSL",
        timeout=30,
        fail_silently=False,
    )


def split_addresses(raw: str):
    if not raw:
        return []
    for sep in (";", "\n"):
        raw = raw.replace(sep, ",")
    return [part.strip() for part in raw.split(",") if part.strip()]


def format_sender(settings_obj: MailSettings):
    address = settings_obj.from_address or settings_obj.username
    if settings_obj.from_name:
        return f"{settings_obj.from_name} <{address}>"
    return address


def build_subject(settings_obj: MailSettings, report):
    template = settings_obj.subject_template or "Servis Raporu {rapor_no} - {musteri}"
    machine = report.machine
    machine_label = ""
    if machine:
        machine_label = " ".join(p for p in (machine.brand, machine.name) if p) or machine.model
    return (
        template.replace("{rapor_no}", report.report_no)
        .replace("{musteri}", report.customer.name)
        .replace("{makine}", machine_label)
        .replace("{tarih}", report.service_date.strftime("%d.%m.%Y"))
        .replace("{durum}", report.get_status_display())
    )


def build_body(settings_obj: MailSettings, report, note=""):
    machine = report.machine
    machine_label = ""
    if machine:
        machine_label = " ".join(p for p in (machine.brand, machine.name) if p) or machine.model

    def row(label, value):
        if not value:
            return ""
        return (
            "<tr>"
            f'<td style="padding:7px 10px;background:#f8fafc;border:1px solid #e2e8f0;color:#64748b;width:42%">{escape(label)}</td>'
            f'<td style="padding:7px 10px;border:1px solid #e2e8f0;font-weight:600">{escape(str(value))}</td>'
            "</tr>"
        )

    def block(title, text):
        if not text:
            return ""
        body = escape(text).replace("\n", "<br>")
        return (
            f'<h3 style="margin:20px 0 8px;font-size:14px;color:#0f4c75;'
            f'text-transform:uppercase;letter-spacing:.4px">{escape(title)}</h3>'
            f'<p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#334155">{body}</p>'
        )

    duration = report.duration_minutes
    duration_label = ""
    if duration:
        hours, minutes = divmod(duration, 60)
        duration_label = (f"{hours} sa " if hours else "") + (f"{minutes} dk" if minutes else "")

    rows = "".join([
        row("Rapor No", report.report_no),
        row("Servis Tarihi", report.service_date.strftime("%d.%m.%Y")),
        row("Servis Tipi", report.get_type_display()),
        row("Durum", report.get_status_display()),
        row("Öncelik", report.get_priority_display()),
        row("Müşteri", report.customer.name),
        row("Makine", machine_label),
        row("Seri No", machine.serial_no if machine else ""),
        row("Çalışma Süresi", duration_label),
        row("Teknisyen", report.technician_name),
        row("Sonraki Bakım", report.next_maintenance.strftime("%d.%m.%Y") if report.next_maintenance else ""),
    ])

    departments = "".join(
        f"<li><b>{escape(d.get_department_display())}</b>"
        + (f": {escape(d.work)}" if d.work else "")
        + "</li>"
        for d in report.departments.all()
    )

    parts = report.parts.all()
    parts_html = ""
    if parts:
        items = "".join(
            f"<li>{escape(p.name)}"
            + (f' <span style="color:#64748b">({escape(p.code)})</span>' if p.code else "")
            + f" — {p.quantity:g} {escape(p.unit)} • <b>{escape(p.get_status_display())}</b></li>"
            for p in parts
        )
        parts_html = (
            '<h3 style="margin:20px 0 8px;font-size:14px;color:#0f4c75;text-transform:uppercase;'
            'letter-spacing:.4px">Yedek Parçalar</h3>'
            f'<ul style="margin:0 0 12px;padding-left:20px;font-size:14px;line-height:1.7;color:#334155">{items}</ul>'
        )

    note_html = ""
    if note:
        note_html = (
            '<div style="margin:0 0 16px;padding:12px 14px;background:#f1f5f9;border-left:3px solid #0f4c75;'
            f'font-size:14px;line-height:1.6;color:#334155">{escape(note).replace(chr(10), "<br>")}</div>'
        )

    company_line = " • ".join(
        p for p in (
            settings_obj.company_address,
            settings_obj.company_phone,
            settings_obj.company_email,
            settings_obj.company_web,
        ) if p
    )

    return f"""<!DOCTYPE html>
<html lang="tr"><head><meta charset="utf-8"></head>
<body style="margin:0;padding:24px;background:#f1f5f9;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#0f172a">
  <div style="max-width:640px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e2e8f0">
    <div style="background:#0f4c75;padding:20px 24px;color:#ffffff">
      <div style="font-size:18px;font-weight:700">{escape(settings_obj.company_name or "Servis Raporu")}</div>
      <div style="font-size:13px;opacity:.85;margin-top:4px">Servis Raporu • {escape(report.report_no)}</div>
    </div>
    <div style="padding:24px">
      <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#334155">Merhaba,</p>
      {note_html}
      <p style="margin:0 0 12px;font-size:14px;line-height:1.6;color:#334155">
        {escape(report.customer.name)} için gerçekleştirilen servis çalışmasının raporu aşağıdadır.
        Ayrıntılı rapor ekteki PDF dosyasındadır.
      </p>
      <table style="width:100%;border-collapse:collapse;margin:18px 0;font-size:14px">{rows}</table>
      {'<h3 style="margin:20px 0 8px;font-size:14px;color:#0f4c75;text-transform:uppercase;letter-spacing:.4px">Çalışılan Bölümler</h3><ul style="margin:0 0 12px;padding-left:20px;font-size:14px;line-height:1.7;color:#334155">' + departments + '</ul>' if departments else ''}
      {block("Arıza / Talep Tanımı", report.fault_description)}
      {block("Arıza Nedeni", report.fault_cause)}
      {block("Yapılan İşlem / Çözüm", report.work_done)}
      {block("Öneriler", report.recommendations)}
      {parts_html}
      <p style="margin:24px 0 0;font-size:14px;line-height:1.6;color:#334155">İyi çalışmalar dileriz.</p>
      <div style="border-top:1px solid #e2e8f0;margin-top:20px;padding-top:14px;font-size:12px;color:#64748b">
        {escape(settings_obj.company_name)}<br>{escape(company_line)}
      </div>
    </div>
  </div>
</body></html>"""


def send_report_mail(report, to, cc="", note="", attach_photos=None):
    """Raporu PDF eki ile gönderir. (başarılı, mesaj) döndürür."""
    settings_obj = MailSettings.load()
    if not settings_obj.is_configured:
        return False, "Mail ayarları eksik. Ayarlar > Mail Ayarları bölümünü doldurun."

    recipients = split_addresses(to)
    if not recipients:
        return False, "Alıcı e-posta adresi girilmedi."

    if attach_photos is None:
        attach_photos = settings_obj.attach_photos

    try:
        connection = build_connection(settings_obj)
        message = EmailMultiAlternatives(
            subject=build_subject(settings_obj, report),
            body="Servis raporu ektedir.",
            from_email=format_sender(settings_obj),
            to=recipients,
            cc=split_addresses(cc) or split_addresses(settings_obj.default_cc),
            connection=connection,
        )
        html = build_body(settings_obj, report, note)
        message.attach_alternative(html, "text/html")

        pdf_bytes = build_report_pdf(report, settings_obj)
        if pdf_bytes:
            message.attach(f"{report.report_no}.pdf", pdf_bytes, "application/pdf")

        if attach_photos:
            for photo in report.photos.all()[:12]:
                try:
                    photo.image.open("rb")
                    message.attach(photo.image.name.split("/")[-1], photo.image.read(), None)
                    photo.image.close()
                except (OSError, ValueError):
                    continue

        message.send()
    except Exception as exc:  # SMTP hataları kullanıcıya anlaşılır biçimde döner
        return False, readable_error(exc)

    report.mailed_to = ", ".join(recipients)
    report.mailed_at = timezone.now()
    report.save(update_fields=["mailed_to", "mailed_at"])
    return True, f"Mail gönderildi: {report.mailed_to}"


def send_test_mail(to):
    settings_obj = MailSettings.load()
    if not settings_obj.is_configured:
        return False, "Mail ayarları eksik."
    recipients = split_addresses(to)
    if not recipients:
        return False, "Alıcı adresi girilmedi."
    try:
        message = EmailMultiAlternatives(
            subject="Servis Sistemi • Mail ayarı testi",
            body="Mail ayarlarınız çalışıyor.",
            from_email=format_sender(settings_obj),
            to=recipients,
            connection=build_connection(settings_obj),
        )
        message.attach_alternative(
            "<h2 style='color:#0f4c75'>Mail ayarları çalışıyor ✔</h2>"
            "<p style='font-family:Segoe UI,Arial,sans-serif;color:#334155'>"
            f"Sunucu: {escape(settings_obj.host)}:{settings_obj.port} "
            f"({escape(settings_obj.security)})<br>"
            f"Gönderen: {escape(settings_obj.from_address)}</p>",
            "text/html",
        )
        message.send()
    except Exception as exc:
        return False, readable_error(exc)
    return True, f"Test maili gönderildi: {', '.join(recipients)}"


def readable_error(exc):
    raw = str(exc)
    lower = raw.lower()
    if "authentication" in lower or "535" in lower or "username and password" in lower:
        hint = (
            "Kullanıcı adı veya parola kabul edilmedi. Gmail/Outlook için normal hesap "
            "parolanız yerine \"uygulama parolası\" üretmelisiniz."
        )
    elif "timed out" in lower or "timeout" in lower or "connection refused" in lower:
        hint = "Sunucuya bağlanılamadı. Sunucu adı, port ve şifreleme ayarını kontrol edin."
    elif "name or service not known" in lower or "getaddrinfo" in lower:
        hint = "SMTP sunucu adresi bulunamadı. Yazımını kontrol edin."
    elif "ssl" in lower or "handshake" in lower or "wrong version number" in lower:
        hint = "Güvenli bağlantı kurulamadı. 465 için SSL, 587 için STARTTLS seçili olmalıdır."
    elif "relay" in lower or "not permitted" in lower or "sender" in lower:
        hint = "Sunucu bu adresten gönderime izin vermiyor. Gönderen adresi hesabınızla aynı olmalıdır."
    else:
        hint = "Gönderim başarısız."
    return f"{hint} (Teknik ayrıntı: {raw})"
