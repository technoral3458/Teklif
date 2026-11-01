"""El yazısı ölçü listesi tarama - Anthropic Claude görüntü analizi."""
import base64
import json

from config import ANTHROPIC_API_KEY, ANTHROPIC_MODEL

PROMPT = """Bu görselde membran kapak ölçüleri var. Türkçe el yazısı olabilir.
Lütfen her satırdaki ölçüleri JSON dizisi olarak çıkart.
Kurallar:
- Format genellikle "Boy x En = Adet" veya "En x Boy = Adet" şeklindedir
- 2 haneli sayılar cm (×10 yaparak mm'ye çevir), 3 haneli sayılar mm
- "/" veya "." varsa ilk sayıyı al
- Renk bilgisi varsa door_name'e ekle
- Eğer adet yazılmamışsa 1 kabul et
SADECE JSON döndür, başka açıklama yok:
[{"width_mm": 450, "height_mm": 720, "quantity": 3, "door_name": "Beyaz"}]"""


def scan_image(image_bytes, image_content_type):
    """Görseli Claude'a gönderip kapı ölçülerini liste olarak döner. (senkron)"""
    if not ANTHROPIC_API_KEY:
        raise RuntimeError("ANTHROPIC_API_KEY tanımlı değil (.env dosyasına ekleyin).")

    import anthropic  # tembel import: paket yoksa diğer modüller etkilenmesin

    client = anthropic.Anthropic(api_key=ANTHROPIC_API_KEY)
    b64 = base64.standard_b64encode(image_bytes).decode()

    message = client.messages.create(
        model=ANTHROPIC_MODEL,
        max_tokens=1024,
        messages=[{
            "role": "user",
            "content": [
                {"type": "image", "source": {
                    "type": "base64", "media_type": image_content_type, "data": b64}},
                {"type": "text", "text": PROMPT},
            ],
        }],
    )

    text = message.content[0].text.strip()
    if "```" in text:
        # ```json ... ``` bloğunu ayıkla
        parts = text.split("```")
        text = parts[1] if len(parts) > 1 else text
        text = text.replace("json", "", 1).strip()

    data = json.loads(text)
    # Normalize
    doors = []
    for d in data:
        doors.append({
            "width_mm": float(d.get("width_mm", 0)),
            "height_mm": float(d.get("height_mm", 0)),
            "quantity": int(d.get("quantity", 1) or 1),
            "door_name": str(d.get("door_name", "")),
        })
    return doors
