import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, Card, CardContent, Button, Select, MenuItem,
  FormControl, InputLabel, TextField, Table, TableHead, TableRow,
  TableCell, TableBody, IconButton, Alert, Divider
} from "@mui/material";
import DeleteIcon from "@mui/icons-material/Delete";
import AddIcon from "@mui/icons-material/Add";
import api from "../../api/client";

const emptyItem = () => ({ cap_model: "", color: "", width: "", height: "", quantity: 1, notes: "" });

export default function NewOrder() {
  const [brands, setBrands] = useState([]);
  const [models, setModels] = useState([]);
  const [colors, setColors] = useState([]);
  const [items, setItems] = useState([emptyItem()]);
  const [notes, setNotes] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    api.get("/catalog/brands/").then((r) => setBrands(r.data));
    api.get("/catalog/colors/").then((r) => setColors(r.data));
    api.get("/catalog/models/").then((r) => setModels(r.data));
  }, []);

  const updateItem = (idx, field, val) => {
    setItems((prev) => prev.map((it, i) => (i === idx ? { ...it, [field]: val } : it)));
  };

  const addItem = () => setItems((prev) => [...prev, emptyItem()]);
  const removeItem = (idx) => setItems((prev) => prev.filter((_, i) => i !== idx));

  const getModelDetails = (modelId) => models.find((m) => m.id === parseInt(modelId));

  const handleSubmit = async () => {
    setError("");
    for (const it of items) {
      if (!it.cap_model || !it.color || !it.width || !it.height) {
        setError("Tüm kalemlerde model, renk, genişlik ve yükseklik zorunludur.");
        return;
      }
      const model = getModelDetails(it.cap_model);
      if (model) {
        if (parseFloat(it.width) < parseFloat(model.min_width) || parseFloat(it.width) > parseFloat(model.max_width)) {
          setError(`"${model.name}" modeli için genişlik ${model.min_width}-${model.max_width}mm arası olmalı.`);
          return;
        }
        if (parseFloat(it.height) < parseFloat(model.min_height) || parseFloat(it.height) > parseFloat(model.max_height)) {
          setError(`"${model.name}" modeli için yükseklik ${model.min_height}-${model.max_height}mm arası olmalı.`);
          return;
        }
      }
    }
    setLoading(true);
    try {
      await api.post("/orders/", { notes, items });
      navigate("/dealer/orders");
    } catch (e) {
      setError("Sipariş oluşturulurken hata: " + JSON.stringify(e.response?.data));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} mb={3}>Yeni Sipariş Oluştur</Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" mb={2}>Sipariş Kalemleri</Typography>
          <Table size="small">
            <TableHead>
              <TableRow sx={{ bgcolor: "grey.100" }}>
                <TableCell>#</TableCell>
                <TableCell>Model</TableCell>
                <TableCell>Renk</TableCell>
                <TableCell>Genişlik (mm)</TableCell>
                <TableCell>Yükseklik (mm)</TableCell>
                <TableCell>Adet</TableCell>
                <TableCell>Not</TableCell>
                <TableCell></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((it, idx) => {
                const model = getModelDetails(it.cap_model);
                return (
                  <TableRow key={idx}>
                    <TableCell>{idx + 1}</TableCell>
                    <TableCell>
                      <FormControl size="small" sx={{ minWidth: 180 }}>
                        <Select value={it.cap_model} onChange={(e) => updateItem(idx, "cap_model", e.target.value)} displayEmpty>
                          <MenuItem value="">Seçin...</MenuItem>
                          {models.map((m) => <MenuItem key={m.id} value={m.id}>{m.brand_name} - {m.name}</MenuItem>)}
                        </Select>
                      </FormControl>
                      {model && <Typography variant="caption" display="block" color="text.secondary">
                        {model.min_width}-{model.max_width} x {model.min_height}-{model.max_height} mm
                      </Typography>}
                    </TableCell>
                    <TableCell>
                      <FormControl size="small" sx={{ minWidth: 140 }}>
                        <Select value={it.color} onChange={(e) => updateItem(idx, "color", e.target.value)} displayEmpty>
                          <MenuItem value="">Seçin...</MenuItem>
                          {colors.map((c) => (
                            <MenuItem key={c.id} value={c.id}>
                              <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                                <Box sx={{ width: 14, height: 14, borderRadius: "50%", bgcolor: c.hex_color, border: "1px solid #ccc" }} />
                                {c.name}
                              </Box>
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </TableCell>
                    <TableCell>
                      <TextField size="small" type="number" value={it.width} onChange={(e) => updateItem(idx, "width", e.target.value)} sx={{ width: 90 }} />
                    </TableCell>
                    <TableCell>
                      <TextField size="small" type="number" value={it.height} onChange={(e) => updateItem(idx, "height", e.target.value)} sx={{ width: 90 }} />
                    </TableCell>
                    <TableCell>
                      <TextField size="small" type="number" value={it.quantity} onChange={(e) => updateItem(idx, "quantity", e.target.value)} sx={{ width: 60 }} inputProps={{ min: 1 }} />
                    </TableCell>
                    <TableCell>
                      <TextField size="small" value={it.notes} onChange={(e) => updateItem(idx, "notes", e.target.value)} sx={{ width: 120 }} />
                    </TableCell>
                    <TableCell>
                      <IconButton onClick={() => removeItem(idx)} disabled={items.length === 1} size="small" color="error">
                        <DeleteIcon />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
          <Button startIcon={<AddIcon />} onClick={addItem} sx={{ mt: 2 }}>Kalem Ekle</Button>
        </CardContent>
      </Card>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <TextField label="Sipariş Notu" multiline rows={3} fullWidth value={notes} onChange={(e) => setNotes(e.target.value)} />
        </CardContent>
      </Card>

      <Box sx={{ display: "flex", gap: 2 }}>
        <Button variant="contained" size="large" onClick={handleSubmit} disabled={loading}>
          {loading ? "Gönderiliyor..." : "Siparişi Gönder"}
        </Button>
        <Button variant="outlined" onClick={() => navigate("/dealer/orders")}>İptal</Button>
      </Box>
    </Box>
  );
}
