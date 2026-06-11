import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Box, Card, CardContent, TextField, Button, Typography, Alert } from "@mui/material";
import { useAuth } from "../context/AuthContext";

export default function Login() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const ROLE_ROUTES = {
    admin: "/admin/users",
    sales: "/sales/orders",
    dealer: "/dealer/orders",
    cnc: "/cnc/queue",
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError("");
    try {
      const user = await login(username, password);
      navigate(ROLE_ROUTES[user.role] || "/");
    } catch {
      setError("Kullanıcı adı veya şifre hatalı");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", bgcolor: "#f0f4f8" }}>
      <Card sx={{ width: 400, p: 2 }}>
        <CardContent>
          <Typography variant="h5" fontWeight={700} mb={1} color="primary">MemKap CNC</Typography>
          <Typography variant="body2" color="text.secondary" mb={3}>
            Membran Kapak Üretim Yönetim Sistemi
          </Typography>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <form onSubmit={handleSubmit}>
            <TextField label="Kullanıcı Adı" fullWidth value={username} onChange={(e) => setUsername(e.target.value)} sx={{ mb: 2 }} required />
            <TextField label="Şifre" type="password" fullWidth value={password} onChange={(e) => setPassword(e.target.value)} sx={{ mb: 3 }} required />
            <Button type="submit" variant="contained" fullWidth size="large" disabled={loading}>
              {loading ? "Giriş yapılıyor..." : "Giriş Yap"}
            </Button>
          </form>
        </CardContent>
      </Card>
    </Box>
  );
}
