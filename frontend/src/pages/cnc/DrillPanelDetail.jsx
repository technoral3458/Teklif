import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Box, Typography, Card, Button, Stack, TextField, Alert,
  Table, TableHead, TableRow, TableCell, TableBody, Chip, Divider
} from "@mui/material";
import DownloadIcon from "@mui/icons-material/Download";
import SaveIcon from "@mui/icons-material/Save";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import api from "../../api/client";

const TYPE_LABELS = { 1: "Yatay delme", 2: "Dikey delme", 3: "Freze/kanal" };
const FACE_LABELS = { 1: "Ön", 2: "Arka", 3: "Sağ", 4: "Sol", 5: "Üst", 6: "Alt" };

// Panel + operasyonları ölçekli SVG olarak çizer (CNC Y ekseni aşağıdan yukarı).
function PanelPreview({ length, width, machinings }) {
  const L = Number(length) || 1;
  const W = Number(width) || 1;
  const pad = Math.max(L, W) * 0.08 + 10;
  const vbW = L + pad * 2;
  const vbH = W + pad * 2;
  const flipY = (y) => W - y; // CNC alt-sol orijin -> SVG üst-sol

  const num = (a, k) => parseFloat(a?.[k] ?? "0") || 0;

  return (
    <svg viewBox={`0 0 ${vbW} ${vbH}`} style={{ width: "100%", maxHeight: 460, background: "#fafafa", border: "1px solid #ddd", borderRadius: 8 }}>
      <g transform={`translate(${pad}, ${pad})`}>
        {/* panel gövdesi */}
        <rect x="0" y="0" width={L} height={W} fill="#d8b88a" stroke="#9c6b2f" strokeWidth={Math.max(L, W) * 0.003} />

        {machinings.map((m, i) => {
          const a = m.attrib || {};
          const type = parseInt(a.Type, 10);
          const face = parseInt(a.Face, 10);
          const x = num(a, "X");
          const y = num(a, "Y");

          if (type === 2) {
            // dikey delik (yüzeye)
            const r = Math.max(num(a, "Diameter") / 2, Math.max(L, W) * 0.006);
            return <circle key={i} cx={x} cy={flipY(y)} r={r} fill="#1565c0" stroke="#0d3c75" strokeWidth={r * 0.15} />;
          }
          if (type === 1) {
            // yatay delik (kenara) - kenardan içeri ok
            const r = Math.max(num(a, "Diameter") / 2, Math.max(L, W) * 0.006);
            return <circle key={i} cx={x} cy={flipY(y)} r={r} fill="#ef6c00" stroke="#a04b00" strokeWidth={r * 0.15} />;
          }
          if (type === 3) {
            // freze / kanal: her Line için segment
            const lines = m.lines || [];
            let px = x, py = y;
            const tw = Math.max(Math.max(L, W) * 0.01, 3);
            return (
              <g key={i}>
                {lines.map((ln, j) => {
                  const ex = num(ln.attrib, "EndX");
                  const ey = num(ln.attrib, "EndY");
                  const seg = (
                    <line key={j} x1={px} y1={flipY(py)} x2={ex} y2={flipY(ey)}
                      stroke="#2e7d32" strokeOpacity="0.6" strokeWidth={tw} strokeLinecap="round" />
                  );
                  px = ex; py = ey;
                  return seg;
                })}
              </g>
            );
          }
          return null;
        })}
      </g>
    </svg>
  );
}

export default function DrillPanelDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [panel, setPanel] = useState(null);
  const [form, setForm] = useState({});
  const [msg, setMsg] = useState("");

  const load = () => api.get(`/cnc/drill-panels/${id}/`).then((r) => {
    setPanel(r.data);
    setForm({ name: r.data.name, length: r.data.length, width: r.data.width, thickness: r.data.thickness });
  });
  useEffect(() => { load(); }, [id]);

  const save = async () => {
    setMsg("");
    await api.patch(`/cnc/drill-panels/${id}/`, form);
    setMsg("Kaydedildi.");
    load();
  };

  const download = async () => {
    const res = await api.get(`/cnc/drill-panels/${id}/export/`, { responseType: "blob" });
    const url = URL.createObjectURL(res.data);
    const a = document.createElement("a");
    a.href = url; a.download = `${form.name || "panel"}.xml`; a.click();
  };

  if (!panel) return <Typography>Yükleniyor...</Typography>;

  const machinings = panel.machinings || [];

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/cnc/drill-panels")}>Geri</Button>
        <Button variant="contained" color="success" startIcon={<DownloadIcon />} onClick={download}>DWD (.xml) İndir</Button>
      </Stack>

      {msg && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setMsg("")}>{msg}</Alert>}

      <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
        <Card sx={{ p: 2, flex: 2 }}>
          <Typography variant="subtitle1" fontWeight={700} mb={1}>Önizleme (üstten görünüm)</Typography>
          <PanelPreview length={form.length} width={form.width} machinings={machinings} />
          <Stack direction="row" spacing={2} mt={1} flexWrap="wrap">
            <Chip size="small" sx={{ bgcolor: "#1565c0", color: "white" }} label="Dikey delik" />
            <Chip size="small" sx={{ bgcolor: "#ef6c00", color: "white" }} label="Yatay (kenar) delik" />
            <Chip size="small" sx={{ bgcolor: "#2e7d32", color: "white" }} label="Freze / kanal" />
          </Stack>
        </Card>

        <Card sx={{ p: 2, flex: 1 }}>
          <Typography variant="subtitle1" fontWeight={700} mb={2}>Parça Bilgileri</Typography>
          <Stack spacing={2}>
            <TextField label="Parça Adı" size="small" value={form.name || ""} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            <TextField label="Boy (X / mm)" size="small" type="number" value={form.length || ""} onChange={(e) => setForm({ ...form, length: e.target.value })} />
            <TextField label="En (Y / mm)" size="small" type="number" value={form.width || ""} onChange={(e) => setForm({ ...form, width: e.target.value })} />
            <TextField label="Kalınlık (mm)" size="small" type="number" value={form.thickness || ""} onChange={(e) => setForm({ ...form, thickness: e.target.value })} />
            <Button variant="contained" startIcon={<SaveIcon />} onClick={save}>Kaydet</Button>
          </Stack>
        </Card>
      </Stack>

      <Card sx={{ p: 2, mt: 2 }}>
        <Typography variant="subtitle1" fontWeight={700} mb={1}>Operasyonlar ({machinings.length})</Typography>
        <Divider sx={{ mb: 1 }} />
        <Table size="small">
          <TableHead>
            <TableRow sx={{ bgcolor: "grey.100" }}>
              <TableCell>#</TableCell>
              <TableCell>Tip</TableCell>
              <TableCell>Yüz</TableCell>
              <TableCell>X</TableCell>
              <TableCell>Y</TableCell>
              <TableCell>Çap / Takım</TableCell>
              <TableCell>Derinlik</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {machinings.map((m, i) => {
              const a = m.attrib || {};
              const type = parseInt(a.Type, 10);
              return (
                <TableRow key={i} hover>
                  <TableCell>{i + 1}</TableCell>
                  <TableCell>{TYPE_LABELS[type] || `Tip ${type}`}</TableCell>
                  <TableCell>{FACE_LABELS[parseInt(a.Face, 10)] || a.Face}</TableCell>
                  <TableCell>{a.X}</TableCell>
                  <TableCell>{a.Y}</TableCell>
                  <TableCell>{type === 3 ? (a.ToolName || "-") : `Ø${a.Diameter}`}</TableCell>
                  <TableCell>{a.Depth}</TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Card>
    </Box>
  );
}
