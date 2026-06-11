import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Chip, Button, Card, CardContent, Dialog, DialogTitle, DialogContent,
  DialogActions, TextField, Alert, Tabs, Tab, Tooltip, IconButton
} from "@mui/material";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import CancelIcon from "@mui/icons-material/Cancel";
import VisibilityIcon from "@mui/icons-material/Visibility";
import api from "../../api/client";

const STATUS_COLOR = {
  pending: "warning", approved: "success", rejected: "error",
  cnc_queue: "info", in_production: "primary", done: "success"
};
const STATUS_LABEL = {
  pending: "Onay Bekliyor", approved: "Onaylandı", rejected: "Reddedildi",
  cnc_queue: "CNC Kuyruğu", in_production: "Üretimde", done: "Tamamlandı"
};

export default function SalesOrders() {
  const [orders, setOrders] = useState([]);
  const [tab, setTab] = useState("pending");
  const [selected, setSelected] = useState(null);
  const [reviewNotes, setReviewNotes] = useState("");
  const [editItems, setEditItems] = useState([]);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const load = () => {
    const params = tab !== "all" ? `?status=${tab}` : "";
    api.get(`/orders/${params}`).then((r) => setOrders(r.data));
  };

  useEffect(() => { load(); }, [tab]);

  const openOrder = (order) => {
    setSelected(order);
    setReviewNotes("");
    setEditItems(order.items.map((it) => ({
      id: it.id,
      approved_width: it.final_width,
      approved_height: it.final_height,
    })));
  };

  const approve = async () => {
    setError("");
    try {
      await api.post(`/orders/${selected.id}/approve/`, { review_notes: reviewNotes, items: editItems });
      setSelected(null);
      load();
    } catch { setError("Onaylama hatası"); }
  };

  const reject = async () => {
    if (!reviewNotes) { setError("Red nedeni yazın"); return; }
    try {
      await api.post(`/orders/${selected.id}/reject/`, { review_notes: reviewNotes });
      setSelected(null);
      load();
    } catch { setError("Red hatası"); }
  };

  const updateEditItem = (id, field, val) => {
    setEditItems((prev) => prev.map((it) => (it.id === id ? { ...it, [field]: val } : it)));
  };

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} mb={2}>Sipariş Yönetimi</Typography>
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab label="Onay Bekleyenler" value="pending" />
        <Tab label="CNC Kuyruğu" value="cnc_queue" />
        <Tab label="Tüm Siparişler" value="all" />
      </Tabs>
      <Card>
        <Table>
          <TableHead>
            <TableRow sx={{ bgcolor: "grey.100" }}>
              <TableCell>Sipariş No</TableCell>
              <TableCell>Bayi</TableCell>
              <TableCell>Tarih</TableCell>
              <TableCell>Kalemleri</TableCell>
              <TableCell>Durum</TableCell>
              <TableCell>İşlem</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {orders.length === 0 && (
              <TableRow><TableCell colSpan={6} align="center">Sipariş yok.</TableCell></TableRow>
            )}
            {orders.map((o) => (
              <TableRow key={o.id} hover>
                <TableCell><strong>{o.order_number}</strong></TableCell>
                <TableCell>{o.dealer_name}</TableCell>
                <TableCell>{new Date(o.created_at).toLocaleDateString("tr-TR")}</TableCell>
                <TableCell>
                  {o.items?.slice(0, 2).map((it) => (
                    <Typography key={it.id} variant="caption" display="block">
                      {it.cap_model_detail?.name} {it.width}x{it.height}mm ×{it.quantity}
                    </Typography>
                  ))}
                  {o.items?.length > 2 && <Typography variant="caption">+{o.items.length - 2} daha...</Typography>}
                </TableCell>
                <TableCell>
                  <Chip label={STATUS_LABEL[o.status] || o.status} color={STATUS_COLOR[o.status] || "default"} size="small" />
                </TableCell>
                <TableCell>
                  <Tooltip title="İncele / Onayla">
                    <IconButton size="small" onClick={() => openOrder(o)}><VisibilityIcon /></IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>

      <Dialog open={!!selected} onClose={() => setSelected(null)} maxWidth="md" fullWidth>
        {selected && <>
          <DialogTitle>Sipariş: {selected.order_number} — {selected.dealer_name}</DialogTitle>
          <DialogContent>
            {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
            <Typography variant="subtitle2" mb={1}>Kalemleri (ölçü düzeltebilirsiniz):</Typography>
            <Table size="small" sx={{ mb: 2 }}>
              <TableHead>
                <TableRow sx={{ bgcolor: "grey.50" }}>
                  <TableCell>Model</TableCell>
                  <TableCell>Renk</TableCell>
                  <TableCell>İstenen G×Y</TableCell>
                  <TableCell>Onaylanan Genişlik</TableCell>
                  <TableCell>Onaylanan Yükseklik</TableCell>
                  <TableCell>Adet</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {selected.items.map((it) => {
                  const edit = editItems.find((e) => e.id === it.id) || {};
                  return (
                    <TableRow key={it.id}>
                      <TableCell>{it.cap_model_detail?.code}<br /><small>{it.cap_model_detail?.name}</small></TableCell>
                      <TableCell>
                        <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                          <Box sx={{ width: 12, height: 12, borderRadius: "50%", bgcolor: it.color_detail?.hex_color, border: "1px solid #ccc" }} />
                          {it.color_detail?.name}
                        </Box>
                      </TableCell>
                      <TableCell>{it.width}×{it.height}</TableCell>
                      <TableCell>
                        <TextField size="small" type="number" value={edit.approved_width || ""} onChange={(e) => updateEditItem(it.id, "approved_width", e.target.value)} sx={{ width: 80 }} />
                      </TableCell>
                      <TableCell>
                        <TextField size="small" type="number" value={edit.approved_height || ""} onChange={(e) => updateEditItem(it.id, "approved_height", e.target.value)} sx={{ width: 80 }} />
                      </TableCell>
                      <TableCell>{it.quantity}</TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
            <TextField
              label="İnceleme Notu (red nedeni veya açıklama)"
              multiline rows={2} fullWidth
              value={reviewNotes} onChange={(e) => setReviewNotes(e.target.value)}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setSelected(null)}>Kapat</Button>
            <Button color="error" startIcon={<CancelIcon />} onClick={reject} disabled={selected.status !== "pending"}>Reddet</Button>
            <Button color="success" variant="contained" startIcon={<CheckCircleIcon />} onClick={approve} disabled={selected.status !== "pending"}>Onayla & CNC Kuyruğuna Al</Button>
          </DialogActions>
        </>}
      </Dialog>
    </Box>
  );
}
