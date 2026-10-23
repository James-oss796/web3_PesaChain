const CACHE_NAME = "pesachain-static-v2";
const ICON_PATHS = new Set([
  "/icons/pesachain-192.png",
  "/icons/pesachain-512.png",
  "/icons/pesachain-512-maskable.png",
  "/icons/pesachain-symbol.svg",
]);
const MAX_CACHED_ASSETS = 80;

self.addEventListener("install", (event) => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys
          .filter((key) => key.startsWith("pesachain-static-") && key !== CACHE_NAME)
          .map((key) => caches.delete(key)),
      ),
    ).then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") return;

  const url = new URL(request.url);
  if (url.origin !== self.location.origin || url.pathname.includes("/api/")) return;

  const isHashedBuildAsset = /\/assets\/.+[-.]\w{6,}\.(js|css|woff2?|png|svg|webp)$/i.test(url.pathname);
  const isAppIcon = ICON_PATHS.has(url.pathname);
  if (!isHashedBuildAsset && !isAppIcon) return;

  event.respondWith(
    caches.open(CACHE_NAME).then(async (cache) => {
      const cached = await cache.match(request);
      if (cached) return cached;

      const response = await fetch(request);
      if (response.ok && response.type === "basic") {
        await cache.put(request, response.clone());
        const keys = await cache.keys();
        if (keys.length > MAX_CACHED_ASSETS) {
          await Promise.all(keys.slice(0, keys.length - MAX_CACHED_ASSETS).map((key) => cache.delete(key)));
        }
      }
      return response;
    }),
  );
});
