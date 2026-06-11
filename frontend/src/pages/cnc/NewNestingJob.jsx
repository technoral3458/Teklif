import { useState, useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import {
  Box, Typography, Card, CardContent, TextField, Button,
  Alert, Grid, Divider, CircularProgress
} from "@mui/material";
import api from "../../api/client";

export default function NewNestingJob() {
  const location = useLocation();
  const navigate = useNavigate();
  const orderIds = location.state?.orderIds || [];

  const [form, setForm] = useState({
    name: `Nesting-${new Date().toLocaleDateString("tr-TR").replace(/\//g, "")}`,
    plate_width: 2440,
    plate_height: 1220,
    kerf: 4,
    tool_diameter: 6,
    feed_rate: 6000,
    spindle_speed: 18000,
    cut_depth: 18,
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);

  const set = (field, val) => setForm((prev) => ({ ...prev, [field]: val }));

  const handleRun = async () => {
    if (orderIds.length === 0) { setError("Sipariş seçilmedi"); return; }
    setLoading(true); setError("");
    try {
      const res = await api.post("/cnc/jobs/create/", { ...form, order_ids: orderIds });
      setResult(res.data);
    } catch (e) {
      setError(e.response?.data?.error || "Nesting hatası");
    } finally {
      setLoading(false);
    }
  };

  const downloadGCode = async () => {
    const res = await api.get(`/cnc/jobs/${result.id}/gcode/`, { responseType: "blob" });
    const url = URL.createObjectURL(res.data);
    const a = document.createElement("a");
    a.href = url;
    a.download = `NC_${form.name}.nc`;
    a.click();
  };

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} mb={3}>Yeni Nesting İşi</Typography>
      <Typography variant="body2" color="text.secondary" mb={3}>
        Seçili {orderIds.length} sipariş için plaka yerleşimi oluşturulacak.
      </Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      {!result ? (
        <>
          <Grid container spacing={3}>
            <Grid item xs={12}>
              <Card>
                <CardContent>
                  <Typography variant="h6" mb={2}>İş Adı</Typography>
                  <TextField label="İş Adı" fullWidth value={form.name} onChange={(e) => set("name", e.target.value)} />
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={6}>
              <Card>
                <CardContent>
                  <Typography variant="h6" mb={2}>Plaka Ölçüleri</Typography>
                  <Box sx={{ display: "flex", gap: 2 }}>
                    <TextField label="Plaka Genişliği (mm)" type="number" value={form.plate_width} onChange={(e) => set("plate_width", e.target.value)} fullWidth />
                    <TextField label="Plaka Yüksekliği (mm)" type="number" value={form.plate_height} onChange={(e) => set("plate_height", e.target.value)} fullWidth />
                  </Box>
                  <TextField label="Freze Boşluğu - Kerf (mm)" type="number" value={form.kerf} onChange={(e) => set("kerf", e.target.value)} fullWidth sx={{ mt: 2 }} />
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={6}>
              <Card>
                <CardContent>
                  <Typography variant="h6" mb={2}>CNC Parametreleri</Typography>
                  <Box sx={{ display: "flex", gap: 2, mb: 2 }}>
                    <TextField label="Takım Çapı (mm)" type="number" value={form.tool_diameter} onChange={(e) => set("tool_diameter", e.target.value)} fullWidth />
                    <TextField label="Kesim Derinliği (mm)" type="number" value={form.cut_depth} onChange={(e) => set("cut_depth", e.target.value)} fullWidth />
                  </Box>
                  <Box sx={{ display: "flex", gap: 2 }}>
                    <TextField label="İlerleme Hızı (mm/dak)" type="number" value={form.feed_rate} onChange={(e) => set("feed_rate", e.target.value)} fullWidth />
                    <TextField label="Devir (RPM)" type="number" value={form.spindle_speed} onChange={(e) => set("spindle_speed", e.target.value)} fullWidth />
                  </Box>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
          <Box sx={{ mt: 3, display: "flex", gap: 2 }}>
            <Button variant="contained" size="large" onClick={handleRun} disabled={loading}>
              {loading ? <><CircularProgress size={20} sx={{ mr: 1 }} />Nesting Hesaplanıyor...</> : "Nesting Çalıştır"}
            </Button>
            <Button variant="outlined" onClick={() => navigate("/cnc/queue")}>İptal</Button>
          </Box>
        </>
      ) : (
        <Card>
          <CardContent>
            <Alert severity="success" sx={{ mb: 3 }}>
              Nesting tamamlandı! {result.placed_items?.length} parça, {result.nesting_result ? Math.max(...result.nesting_result.map((p) => p.plate_index)) + 1 : 1} plakaya yerleştirildi.
              Verimlilik: <strong>%{result.efficiency}</strong>
            </Alert>
            <NestingPreview result={result} plateWidth={form.plate_width} plateHeight={form.plate_height} />
            <Box sx={{ mt: 3, display: "flex", gap: 2 }}>
              <Button variant="contained" color="success" size="large" onClick={downloadGCode}>
                NC Kodu İndir (.nc)
              </Button>
              <Button variant="outlined" onClick={() => navigate("/cnc/jobs")}>İşler Listesine Dön</Button>
            </Box>
          </CardContent>
        </Card>
      )}
    </Box>
  );
}

function NestingPreview({ result, plateWidth, plateHeight }) {
  if (!result.nesting_result) return null;
  const plates = {};
  result.nesting_result.forEach((p) => {
    if (!plates[p.plate_index]) plates[p.plate_index] = [];
    plates[p.plate_index].push(p);
  });

  const SCALE = Math.min(500 / plateWidth, 300 / plateHeight);

  const COLORS = ["#4CAF50","#2196F3","#FF9800","#E91E63","#9C27B0","#00BCD4","#FF5722","#795548"];

  return (
    <Box>
      <Typography variant="h6" mb={2}>Plaka Yerleşim Önizlemesi</Typography>
      {Object.entries(plates).map(([plateIdx, pieces]) => (
        <Box key={plateIdx} sx={{ mb: 4 }}>
          <Typography variant="subtitle2" mb={1}>Plaka {parseInt(plateIdx) + 1}</Typography>
          <Box sx={{
            position: "relative",
            width: plateWidth * SCALE,
            height: plateHeight * SCALE,
            bgcolor: "#f5f5dc",
            border: "2px solid #333",
            overflow: "hidden",
          }}>
            {pieces.map((p, i) => (
              <Box
                key={i}
                sx={{
                  position: "absolute",
                  left: p.x * SCALE,
                  top: p.y * SCALE,
                  width: p.width * SCALE,
                  height: p.height * SCALE,
                  bgcolor: COLORS[i % COLORS.length],
                  opacity: 0.7,
                  border: "1px solid rgba(0,0,0,0.3)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  overflow: "hidden",
                }}
                title={`${p.label} (${p.x.toFixed(0)},${p.y.toFixed(0)})`}
              >
                <Typography variant="caption" sx={{ color: "white", fontSize: "9px", textAlign: "center", fontWeight: "bold" }}>
                  {p.label}
                </Typography>
              </Box>
            ))}
          </Box>
        </Box>
      ))}
    </Box>
  );
}
