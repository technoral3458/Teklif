import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Chip, Button, Card, CardContent, IconButton, Tooltip
} from "@mui/material";
import VisibilityIcon from "@mui/icons-material/Visibility";
import AddIcon from "@mui/icons-material/Add";
import api from "../../api/client";

const STATUS_COLOR = {
  pending: "warning", approved: "success", rejected: "error",
  cnc_queue: "info", in_production: "primary", done: "success", draft: "default"
};
const STATUS_LABEL = {
  pending: "Onay Bekliyor", approved: "Onaylandı", rejected: "Reddedildi",
  cnc_queue: "CNC Kuyruğu", in_production: "Üretimde", done: "Tamamlandı", draft: "Taslak"
};

export default function DealerOrders() {
  const [orders, setOrders] = useState([]);
  const navigate = useNavigate();

  useEffect(() => { api.get("/orders/").then((r) => setOrders(r.data)); }, []);

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 3 }}>
        <Typography variant="h5" fontWeight={700}>Siparişlerim</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate("/dealer/new-order")}>
          Yeni Sipariş
        </Button>
      </Box>
      <Card>
        <CardContent sx={{ p: 0 }}>
          <Table>
            <TableHead>
              <TableRow sx={{ bgcolor: "grey.100" }}>
                <TableCell>Sipariş No</TableCell>
                <TableCell>Tarih</TableCell>
                <TableCell>Kalem Sayısı</TableCell>
                <TableCell>Durum</TableCell>
                <TableCell>Not</TableCell>
                <TableCell></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {orders.length === 0 && (
                <TableRow><TableCell colSpan={6} align="center">Henüz sipariş yok.</TableCell></TableRow>
              )}
              {orders.map((o) => (
                <TableRow key={o.id} hover>
                  <TableCell fontWeight={700}><strong>{o.order_number}</strong></TableCell>
                  <TableCell>{new Date(o.created_at).toLocaleDateString("tr-TR")}</TableCell>
                  <TableCell>{o.items?.length || 0} kalem</TableCell>
                  <TableCell>
                    <Chip label={STATUS_LABEL[o.status] || o.status} color={STATUS_COLOR[o.status] || "default"} size="small" />
                  </TableCell>
                  <TableCell>{o.review_notes || o.notes || "-"}</TableCell>
                  <TableCell>
                    <Tooltip title="Detay">
                      <IconButton size="small" onClick={() => navigate(`/dealer/orders/${o.id}`)}>
                        <VisibilityIcon />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </Box>
  );
}
