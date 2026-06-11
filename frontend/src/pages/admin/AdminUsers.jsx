import { useState, useEffect } from "react";
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Card, Button, Chip, Dialog, DialogTitle, DialogContent, DialogActions,
  TextField, Select, MenuItem, FormControl, InputLabel, Alert
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import api from "../../api/client";

const ROLES = [
  { value: "dealer", label: "Bayi" },
  { value: "sales", label: "Satış Ekibi" },
  { value: "cnc", label: "CNC Operatörü" },
  { value: "admin", label: "Yönetici" },
];
const ROLE_COLOR = { dealer: "success", sales: "primary", cnc: "warning", admin: "error" };

const emptyForm = { username: "", email: "", password: "", first_name: "", last_name: "", role: "dealer", phone: "", company: "", city: "" };

export default function AdminUsers() {
  const [users, setUsers] = useState([]);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [error, setError] = useState("");

  const load = () => api.get("/accounts/users/").then((r) => setUsers(r.data));
  useEffect(() => { load(); }, []);

  const set = (f, v) => setForm((p) => ({ ...p, [f]: v }));

  const save = async () => {
    setError("");
    try {
      await api.post("/accounts/users/", form);
      setOpen(false); setForm(emptyForm); load();
    } catch (e) {
      setError(JSON.stringify(e.response?.data));
    }
  };

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 3 }}>
        <Typography variant="h5" fontWeight={700}>Kullanıcı Yönetimi</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setOpen(true)}>Kullanıcı Ekle</Button>
      </Box>
      <Card>
        <Table>
          <TableHead>
            <TableRow sx={{ bgcolor: "grey.100" }}>
              <TableCell>Kullanıcı</TableCell>
              <TableCell>Firma / Şehir</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Telefon</TableCell>
              <TableCell>Rol</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {users.map((u) => (
              <TableRow key={u.id} hover>
                <TableCell><strong>{u.first_name} {u.last_name}</strong><br /><small>@{u.username}</small></TableCell>
                <TableCell>{u.company}<br /><small>{u.city}</small></TableCell>
                <TableCell>{u.email}</TableCell>
                <TableCell>{u.phone}</TableCell>
                <TableCell><Chip label={ROLES.find((r) => r.value === u.role)?.label || u.role} color={ROLE_COLOR[u.role] || "default"} size="small" /></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>

      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Yeni Kullanıcı</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <Box sx={{ display: "flex", gap: 2, mt: 1, mb: 2 }}>
            <TextField label="Ad" value={form.first_name} onChange={(e) => set("first_name", e.target.value)} fullWidth />
            <TextField label="Soyad" value={form.last_name} onChange={(e) => set("last_name", e.target.value)} fullWidth />
          </Box>
          <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
            <TextField label="Kullanıcı Adı" value={form.username} onChange={(e) => set("username", e.target.value)} fullWidth required />
            <TextField label="Şifre" type="password" value={form.password} onChange={(e) => set("password", e.target.value)} fullWidth required />
          </Box>
          <TextField label="Email" value={form.email} onChange={(e) => set("email", e.target.value)} fullWidth sx={{ mb: 2 }} />
          <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
            <TextField label="Firma" value={form.company} onChange={(e) => set("company", e.target.value)} fullWidth />
            <TextField label="Şehir" value={form.city} onChange={(e) => set("city", e.target.value)} fullWidth />
          </Box>
          <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
            <TextField label="Telefon" value={form.phone} onChange={(e) => set("phone", e.target.value)} fullWidth />
            <FormControl fullWidth>
              <InputLabel>Rol</InputLabel>
              <Select value={form.role} onChange={(e) => set("role", e.target.value)} label="Rol">
                {ROLES.map((r) => <MenuItem key={r.value} value={r.value}>{r.label}</MenuItem>)}
              </Select>
            </FormControl>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>İptal</Button>
          <Button variant="contained" onClick={save}>Kaydet</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
