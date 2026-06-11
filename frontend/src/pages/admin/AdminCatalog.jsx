import { useState, useEffect } from "react";
import {
  Box, Typography, Card, Table, TableHead, TableRow, TableCell, TableBody,
  Button, Tabs, Tab, Dialog, DialogTitle, DialogContent, DialogActions,
  TextField, Select, MenuItem, FormControl, InputLabel, Alert
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import api from "../../api/client";

export default function AdminCatalog() {
  const [tab, setTab] = useState("brands");
  const [brands, setBrands] = useState([]);
  const [models, setModels] = useState([]);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({});
  const [error, setError] = useState("");

  const load = () => {
    api.get("/catalog/brands/").then((r) => setBrands(r.data));
    api.get("/catalog/models/").then((r) => setModels(r.data));
  };
  useEffect(() => { load(); }, []);

  const set = (f, v) => setForm((p) => ({ ...p, [f]: v }));

  const openNew = () => {
    setForm(tab === "brands" ? { name: "" } : {
      brand: "", name: "", code: "", min_width: 200, max_width: 1200,
      min_height: 200, max_height: 2400, milling_offset: 5
    });
    setOpen(true);
  };

  const save = async () => {
    setError("");
    try {
      const url = tab === "brands" ? "/catalog/brands/" : "/catalog/models/";
      await api.post(url, form);
      setOpen(false); load();
    } catch (e) { setError(JSON.stringify(e.response?.data)); }
  };

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 2 }}>
        <Typography variant="h5" fontWeight={700}>Katalog Yönetimi</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openNew}>Ekle</Button>
      </Box>
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label="Markalar" value="brands" />
        <Tab label="Kapak Modelleri" value="models" />
      </Tabs>
      {tab === "brands" && (
        <Card>
          <Table>
            <TableHead><TableRow sx={{ bgcolor: "grey.100" }}><TableCell>Marka</TableCell><TableCell>Aktif</TableCell></TableRow></TableHead>
            <TableBody>
              {brands.map((b) => <TableRow key={b.id}><TableCell>{b.name}</TableCell><TableCell>{b.is_active ? "✓" : "✗"}</TableCell></TableRow>)}
            </TableBody>
          </Table>
        </Card>
      )}
      {tab === "models" && (
        <Card>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: "grey.100" }}>
                <TableCell>Kod</TableCell><TableCell>Model Adı</TableCell><TableCell>Marka</TableCell>
                <TableCell>Min G</TableCell><TableCell>Max G</TableCell>
                <TableCell>Min Y</TableCell><TableCell>Max Y</TableCell>
                <TableCell>Freze Payı</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {models.map((m) => (
                <TableRow key={m.id}>
                  <TableCell><strong>{m.code}</strong></TableCell>
                  <TableCell>{m.name}</TableCell>
                  <TableCell>{m.brand_name}</TableCell>
                  <TableCell>{m.min_width}mm</TableCell><TableCell>{m.max_width}mm</TableCell>
                  <TableCell>{m.min_height}mm</TableCell><TableCell>{m.max_height}mm</TableCell>
                  <TableCell>{m.milling_offset}mm</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      <Dialog open={open} onClose={() => setOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{tab === "brands" ? "Yeni Marka" : "Yeni Kapak Modeli"}</DialogTitle>
        <DialogContent>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          {tab === "brands" && (
            <TextField label="Marka Adı" fullWidth value={form.name || ""} onChange={(e) => set("name", e.target.value)} sx={{ mt: 1 }} />
          )}
          {tab === "models" && (
            <Box sx={{ mt: 1 }}>
              <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
                <FormControl fullWidth>
                  <InputLabel>Marka</InputLabel>
                  <Select value={form.brand || ""} onChange={(e) => set("brand", e.target.value)} label="Marka">
                    {brands.map((b) => <MenuItem key={b.id} value={b.id}>{b.name}</MenuItem>)}
                  </Select>
                </FormControl>
                <TextField label="Model Kodu" value={form.code || ""} onChange={(e) => set("code", e.target.value)} fullWidth />
              </Box>
              <TextField label="Model Adı" fullWidth value={form.name || ""} onChange={(e) => set("name", e.target.value)} sx={{ mb: 2 }} />
              <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
                <TextField label="Min Genişlik" type="number" value={form.min_width || ""} onChange={(e) => set("min_width", e.target.value)} fullWidth />
                <TextField label="Max Genişlik" type="number" value={form.max_width || ""} onChange={(e) => set("max_width", e.target.value)} fullWidth />
              </Box>
              <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
                <TextField label="Min Yükseklik" type="number" value={form.min_height || ""} onChange={(e) => set("min_height", e.target.value)} fullWidth />
                <TextField label="Max Yükseklik" type="number" value={form.max_height || ""} onChange={(e) => set("max_height", e.target.value)} fullWidth />
              </Box>
              <TextField label="Freze Payı (mm)" type="number" value={form.milling_offset || ""} onChange={(e) => set("milling_offset", e.target.value)} fullWidth />
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)}>İptal</Button>
          <Button variant="contained" onClick={save}>Kaydet</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
