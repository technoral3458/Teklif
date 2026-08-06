import axios from "axios";

// Telefondan acildiginda "localhost" telefonun kendisi demek oldugu icin
// yayindaki adres VITE_API_BASE_URL ile verilmelidir (bkz. .env.example).
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8000/api";

const api = axios.create({ baseURL: API_BASE_URL });

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
          const res = await axios.post(`${API_BASE_URL}/auth/refresh/`, { refresh });
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
