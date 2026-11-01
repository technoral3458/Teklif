# TitaniumCam API — Online Kabin siparişleri

TitaniumCam, web sitesinde **onaylanmış** dolap siparişlerini bu API ile çeker, "aldım" der ve üretim durumunu bildirir.

- **Adres:** `https://kapak.ersanmakina.net/api/titanium/v1`
- **Yalnız HTTPS.** HTTP isteği `403` alır.
- **Başlıklar (her istekte):**
  - `Authorization: Bearer <anahtar>` — yönetici panelinde **Kabin Siparişleri → 🔑 TitaniumCam API** ekranında üretilir. Anahtar yalnız oluşturulurken bir kez gösterilir; sunucuda yalnız SHA-256 özeti durur. İptal edilen anahtar `401` alır.
  - `X-Titanium-Station: CNC-1` — istasyon adı.
- **Hız sınırı:** anahtar başına dakikada 120 istek; aşılırsa `429`.
- **Hata biçimi:** `{"ok": false, "error": "mesaj"}` (claim çakışmasında ek olarak `"claimed_by"`).
- **Saatler:** ISO 8601, Türkiye saati (`+03:00`).

| Kod | Anlamı |
|---|---|
| 200 | Tamam |
| 400 | Geçersiz istek (JSON, `status` değeri, eksik `station`) |
| 401 | Anahtar yok, yanlış ya da iptal |
| 403 | HTTPS değil ya da anahtarın kabin izni yok |
| 404 | Sipariş yok ya da onaylı değil |
| 409 | Sipariş başka istasyonda |
| 429 | Dakikada 120 istek aşıldı |

## GET /cabinet-orders

`?status=approved&unclaimed=1&station=CNC-1`

Onaylı ve alınmamış siparişler, eskiden yeniye. `unclaimed=0&station=CNC-1` verilirse bu istasyonun aldıkları da gelir.

```json
{"orders": [
  {"id": "K-12", "order_no": "KB100012", "customer": "MÜŞTERİ ADI", "project": "PROJE",
   "approved_at": "2026-11-01T14:03:00+03:00", "note": "",
   "claimed_by": null, "production_status": null, "changed_after_claim": false,
   "lines": [
     {"line_no": 1, "cabinet_no": "K-1", "name": "Alt Dolap", "qty": 2,
      "width_mm": 800.0, "height_mm": 860.0, "depth_mm": 560.0,
      "engine_version": "9e3358582431", "definitions_version": "6dba5d0644dd", "note": "",
      "tkabin": {"format": "tkabin", "schema": 1, "kabin": {"...": "...", "adet": 2}}}
   ]}
]}
```

- `tkabin` TitaniumCam'in kendi `.tkabin` biçimidir; olduğu gibi **Kabin → Dosya → Aç** ile açılabilir.
- `tkabin.kabin.adet` her zaman `qty` ile aynıdır. Ölçüler sayıdır. `tkabin.kabin.uid` satır başına tekildir.
- `engine_version` / `definitions_version`: siparişin donduruldüğü motor ve tanım dosyası sürümü (dosya özetinin ilk 12 karakteri).

`GET /cabinet-orders/{id}` tek siparişi aynı biçimde döner.

## POST /cabinet-orders/{id}/claim

```json
{"station": "CNC-1", "app": "TitaniumCam", "version": "2.0"}
```

- İlk alan: `200 {"ok": true, "id": "K-12", "claimed_by": "CNC-1", "claimed_at": "..."}`
- Aynı istasyon tekrar: `200`
- Başka istasyon: `409 {"ok": false, "error": "Başka istasyon aldı", "claimed_by": "CNC-1"}`
- Sipariş yok ya da onaysız: `404`

Alma işlemi tek bir veritabanı ifadesiyle yapılır; iki istasyon aynı anda isterse biri `200`, diğeri `409` alır.

## POST /cabinet-orders/{id}/status

```json
{"station": "CNC-1", "status": "in_production", "note": ""}
```

`status`: `nested`, `in_production`, `done`, `released`. Yalnız siparişi alan istasyon değiştirebilir (yoksa `409`).
`released` siparişi havuza geri koyar: alındı işareti silinir, sipariş yeniden listelenir.

## Yönetici tarafı

- Sipariş ekranında `claimed_by` ve üretim durumu görünür; **Havuza geri al** düğmesi vardır.
- Havuza alınmış siparişin satırı yönetici tarafından değişirse alındı işareti silinmez, sipariş `changed_after_claim: true` olur ve panelde "üretimdeki sipariş değişti" uyarısı çıkar.

## Örnek (curl)

```bash
A="Authorization: Bearer tc_..."; S="X-Titanium-Station: CNC-1"; U=https://kapak.ersanmakina.net/api/titanium/v1
curl -s -H "$A" -H "$S" "$U/cabinet-orders?status=approved&unclaimed=1&station=CNC-1"
curl -s -H "$A" -H "$S" -H "Content-Type: application/json" -d '{"station":"CNC-1","app":"TitaniumCam","version":"2.0"}' $U/cabinet-orders/K-12/claim
curl -s -H "$A" -H "$S" -H "Content-Type: application/json" -d '{"station":"CNC-1","status":"in_production"}' $U/cabinet-orders/K-12/status
```

## Örnek (Python, TitaniumCam tarafı)

```python
import json, urllib.request
U = "https://kapak.ersanmakina.net/api/titanium/v1"
H = {"Authorization": "Bearer tc_...", "X-Titanium-Station": "CNC-1", "Content-Type": "application/json"}

def istek(yol, govde=None):
    r = urllib.request.Request(U + yol, headers=H, method="POST" if govde else "GET",
                               data=json.dumps(govde).encode() if govde else None)
    return json.load(urllib.request.urlopen(r, timeout=20))

for s in istek("/cabinet-orders?status=approved&unclaimed=1&station=CNC-1")["orders"]:
    istek(f"/cabinet-orders/{s['id']}/claim", {"station": "CNC-1", "app": "TitaniumCam", "version": "2.0"})
    for satir in s["lines"]:
        with open(f"{s['order_no']}_{satir['line_no']}.tkabin", "w", encoding="utf-8") as f:
            json.dump(satir["tkabin"], f, ensure_ascii=False, indent=1)   # Kabin → Dosya → Aç
```

Kapak siparişleri için uçlar bu aşamada yok; anahtarın "kapak" izni ileride kullanılacak.
