import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Chip, Button, Card, Checkbox
} from "@mui/material";
import api from "../../api/client";

export default function CNCQueue() {
  const [orders, setOrders] = useState([]);
  const [selected, setSelected] = useState([]);
  const navigate = useNavigate();

  useEffect(() => { api.get("/cnc/queue/").then((r) => setOrders(r.data)); }, []);

  const toggle = (id) => setSelected((prev) => prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]);

  return (
    <Box>
      <Box sx={{ display: "flex", justifyContent: "space-between", mb: 3 }}>
        <Typography variant="h5" fontWeight={700}>CNC Kuyruğu</Typography>
        <Button
          variant="contained" color="warning"
          disabled={selected.length === 0}
          onClick={() => navigate("/cnc/new-job", { state: { orderIds: selected } })}
        >
          Seçilenleri Nesting'e Al ({selected.length})
        </Button>
      </Box>
      <Card>
        <Table>
          <TableHead>
            <TableRow sx={{ bgcolor: "grey.100" }}>
              <TableCell padding="checkbox"></TableCell>
              <TableCell>Sipariş No</TableCell>
              <TableCell>Bayi</TableCell>
              <TableCell>Tarih</TableCell>
              <TableCell>Parçalar</TableCell>
              <TableCell>Toplam Adet</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {orders.length === 0 && <TableRow><TableCell colSpan={6} align="center">CNC kuyruğunda sipariş yok.</TableCell></TableRow>}
            {orders.map((o) => {
              const totalQty = o.items?.reduce((s, it) => s + it.quantity, 0) || 0;
              return (
                <TableRow key={o.id} hover selected={selected.includes(o.id)}>
                  <TableCell padding="checkbox">
                    <Checkbox checked={selected.includes(o.id)} onChange={() => toggle(o.id)} />
                  </TableCell>
                  <TableCell><strong>{o.order_number}</strong></TableCell>
                  <TableCell>{o.dealer_name}</TableCell>
                  <TableCell>{new Date(o.created_at).toLocaleDateString("tr-TR")}</TableCell>
                  <TableCell>
                    {o.items?.map((it) => (
                      <Typography key={it.id} variant="caption" display="block">
                        {it.cap_model_detail?.code} {it.final_width}×{it.final_height}mm ×{it.quantity}
                      </Typography>
                    ))}
                  </TableCell>
                  <TableCell><Chip label={`${totalQty} adet`} size="small" color="primary" /></TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Card>
    </Box>
  );
}
