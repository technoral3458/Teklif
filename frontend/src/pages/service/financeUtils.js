/** Cari sayfalarında ortak kullanılan sabitler ve biçimlendiriciler. */

export const CURRENCIES = [["TRY", "₺ TL"], ["USD", "$ Dolar"], ["EUR", "€ Euro"]];
export const CURRENCY_SYMBOL = { TRY: "₺", USD: "$", EUR: "€" };

export const LEDGER_TYPES = [
  ["BORC", "Borç / Hakediş"],
  ["TAHSILAT", "Tahsilat"],
  ["IADE", "İade / İskonto"],
];

export const PAYMENT_METHODS = [
  ["NAKIT", "Nakit"], ["HAVALE", "Havale / EFT"], ["KREDI_KARTI", "Kredi Kartı"],
  ["CEK", "Çek"], ["SENET", "Senet"], ["DIGER", "Diğer"],
];

export const EXPENSE_CATEGORIES = [
  ["YAKIT", "Yakıt"], ["KONAKLAMA", "Konaklama / Otel"], ["YEMEK", "Yemek"],
  ["YOL", "Otoyol / Köprü"], ["OTOPARK", "Otopark"], ["ULASIM", "Ulaşım (uçak, otobüs)"],
  ["MALZEME", "Malzeme / Sarf"], ["KARGO", "Kargo"], ["ARAC", "Araç Bakım / Lastik"],
  ["DIGER", "Diğer"],
];

export const money = (value, symbol = "₺") =>
  `${Number(value || 0).toLocaleString("tr-TR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })} ${symbol}`;

export const fmtDate = (value) => (value ? new Date(value).toLocaleDateString("tr-TR") : "-");

export const todayISO = () => new Date().toISOString().slice(0, 10);
