import axios from "axios";

// Göreli yol: geliştirmede Vite proxy, canlıda nginx /api'yi backend'e iletir.
// Böylece hem localhost hem de gerçek alan adı/IP üzerinde çalışır.
const api = axios.create({ baseURL: "/api" });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("access_token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  async (err) => {
    if (err.response?.status === 401) {
      const refresh = localStorage.getItem("refresh_token");
      if (refresh) {
        try {
          const res = await axios.post("/api/auth/refresh/", { refresh });
          localStorage.setItem("access_token", res.data.access);
          err.config.headers.Authorization = `Bearer ${res.data.access}`;
          return api(err.config);
        } catch {
          localStorage.clear();
          window.location.href = "/login";
        }
      }
    }
    return Promise.reject(err);
  }
);

export default api;
