import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Card, Button, Chip, IconButton, Tooltip, Stack, Alert
} from "@mui/material";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import DownloadIcon from "@mui/icons-material/Download";
import VisibilityIcon from "@mui/icons-material/Visibility";
import DeleteIcon from "@mui/icons-material/Delete";
import api from "../../api/client";

export default function DrillPanels() {
  const [panels, setPanels] = useState([]);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const fileRef = useRef();
  const navigate = useNavigate();

  const load = () => api.get("/cnc/drill-panels/").then((r) => setPanels(r.data));
  useEffect(() => { load(); }, []);

  const handleUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setError(""); setInfo("");
    const form = new FormData();
    form.append("file", file);
    try {
      const res = await api.post("/cnc/drill-panels/import/", form, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      setInfo(`${res.data.length} panel içe aktarıldı.`);
      load();
    } catch (err) {
      setError(err.response?.data?.error || "Dosya yüklenemedi.");
    } finally {
      if (fileRef.current) fileRef.current.value = "";
    }
  };

  const download = async (panel) => {
    const res = await api.get(`/cnc/drill-panels/${panel.id}/export/`, { responseType: "blob" });
    const url = URL.createObjectURL(res.data);
    const a = document.createElement("a");
    a.href = url; a.download = `${panel.name || "panel"}.xml`; a.click();
  };

  const remove = async (panel) => {
    if (!window.confirm(`"${panel.name}" silinsin mi?`)) return;
    await api.delete(`/cnc/drill-panels/${panel.id}/`);
    load();
  };

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h5" fontWeight={700}>Delme Panelleri (Altı Kenar / DWD)</Typography>
        <Button variant="contained" startIcon={<UploadFileIcon />} component="label">
          DWD Dosyası Yükle
          <input ref={fileRef} hidden type="file" accept=".xml" onChange={handleUpload} />
        </Button>
      </Stack>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError("")}>{error}</Alert>}
      {info && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setInfo("")}>{info}</Alert>}

      <Card>
        <Table>
          <TableHead>
            <TableRow sx={{ bgcolor: "grey.100" }}>
              <TableCell>Parça Adı</TableCell>
              <TableCell>Ölçü (B×E×K)</TableCell>
              <TableCell>Operasyon</TableCell>
              <TableCell>Kaynak</TableCell>
              <TableCell>Tarih</TableCell>
              <TableCell align="right">İşlemler</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {panels.length === 0 && (
              <TableRow><TableCell colSpan={6} align="center">
                Henüz panel yok. "DWD Dosyası Yükle" ile bir .xml dosyası ekleyin.
              </TableCell></TableRow>
            )}
            {panels.map((p) => (
              <TableRow key={p.id} hover>
                <TableCell><strong>{p.name}</strong>{p.order_no && <Typography variant="caption" display="block" color="text.secondary">Sipariş: {p.order_no}</Typography>}</TableCell>
                <TableCell>{p.length}×{p.width}×{p.thickness} mm</TableCell>
                <TableCell><Chip size="small" label={`${p.operation_count} adet`} /></TableCell>
                <TableCell>{p.source === "imported" ? "İçe aktarıldı" : "Elle"}</TableCell>
                <TableCell>{new Date(p.created_at).toLocaleDateString("tr-TR")}</TableCell>
                <TableCell align="right">
                  <Tooltip title="Önizle / Düzenle">
                    <IconButton size="small" color="primary" onClick={() => navigate(`/cnc/drill-panels/${p.id}`)}><VisibilityIcon /></IconButton>
                  </Tooltip>
                  <Tooltip title="DWD (.xml) indir">
                    <IconButton size="small" color="success" onClick={() => download(p)}><DownloadIcon /></IconButton>
                  </Tooltip>
                  <Tooltip title="Sil">
                    <IconButton size="small" color="error" onClick={() => remove(p)}><DeleteIcon /></IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </Box>
  );
}
