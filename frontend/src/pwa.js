/**
 * Servis calisanini kaydeder. Uygulamanin telefona kurulabilmesi ve
 * baglanti koptugunda beyaz ekran yerine arayuzun acilmasi buna bagli.
 *
 * Servis calisani yalnizca HTTPS uzerinde (veya localhost'ta) calisir.
 */
export function registerServiceWorker() {
  if (!("serviceWorker" in navigator)) return;

  window.addEventListener("load", () => {
    const url = `${import.meta.env.BASE_URL}sw.js`;
    navigator.serviceWorker.register(url, { scope: import.meta.env.BASE_URL }).catch(() => {
      // Kayit basarisiz olursa uygulama normal web sitesi gibi calismaya devam eder.
    });
  });
}
