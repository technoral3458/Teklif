/*
 * Teklif uygulamasinin servis calisani.
 *
 * Amaci uygulamayi "yuklenebilir" hale getirmek ve arayuz dosyalarini
 * onbellege almak. Teklif/siparis verileri ASLA onbellege alinmaz; /api/
 * altindaki her istek dogrudan sunucuya gider, boylece ekranda hicbir zaman
 * eski veri gorunmez.
 */

const CACHE = "teklif-shell-v1";
const ROOT = new URL("./", self.registration.scope);
const INDEX = new URL("index.html", ROOT).href;

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) => cache.addAll([ROOT.href, INDEX]))
      .then(() => self.skipWaiting())
      .catch(() => self.skipWaiting()),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (url.pathname.includes("/api/")) return;

  // Sayfa gecisleri: once sunucu, baglanti yoksa onbellekteki arayuz.
  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(INDEX, copy));
          return response;
        })
        .catch(() => caches.match(INDEX).then((hit) => hit || Response.error())),
    );
    return;
  }

  // Surum damgali arayuz dosyalari degismedigi icin once onbellekten verilir.
  if (!url.pathname.includes("/assets/")) return;

  event.respondWith(
    caches.match(request).then((hit) => {
      if (hit) return hit;
      return fetch(request).then((response) => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(request, copy));
        }
        return response;
      });
    }),
  );
});
