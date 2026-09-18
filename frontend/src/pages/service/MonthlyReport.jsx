import { useState, useEffect, useCallback } from "react";
import {
  Box, Typography, Card, CardContent, Grid, Stack, IconButton, Button, Table,
  TableHead, TableRow, TableCell, TableBody, LinearProgress, Alert, Dialog,
  DialogTitle, DialogContent, DialogActions, TextField,
} from "@mui/material";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import PictureAsPdfIcon from "@mui/icons-material/PictureAsPdf";
import MailIcon from "@mui/icons-material/Mail";
import api from "../../api/client";
import { money } from "./financeUtils";

export default function MonthlyReport() {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [summary, setSummary] = useState(null);
  const [overdue, setOverdue] = useState(null);
  const [mailOpen, setMailOpen] = useState(false);
  const [mailTo, setMailTo] = useState("");
  const [mailNote, setMailNote] = useState("");
  const [message, setMessage] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    api.get(`/service/monthly-report/?year=${year}&month=${month}`).then((r) => setSummary(r.data));
    api.get("/service/overdue/").then((r) => setOverdue(r.data));
  }, [year, month]);

  useEffect(() => { load(); }, [load]);

  const shift = (delta) => {
    let m = month + delta;
    let y = year;
    if (m < 1) { m = 12; y -= 1; }
    if (m > 12) { m = 1; y += 1; }
    setMonth(m);
    setYear(y);
  };

  const openPdf = () => {
    window.open(`${api.defaults.baseURL}/service/monthly-report/pdf/?year=${year}&month=${month}`, "_blank");
  };

  const sendMail = async () => {
    setBusy(true); setMessage(null);
    try {
      const res = await api.post(`/service/monthly-report/mail/?year=${year}&month=${month}`, {
        to: mailTo, note: mailNote,
      });
      setMessage({ type: "success", text: res.data.detail });
      setMailOpen(false);
    } catch (err) {
      setMessage({ type: "error", text: err.response?.data?.error || "Gönderilemedi." });
    } finally { setBusy(false); }
  };

  if (!summary) return <LinearProgress />;

  const maxExpense = Math.max(...summary.expense_by_category.map((r) => Number(r.amount)), 1);

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" mb={2}>
        <Typography variant="h5" fontWeight={700}>Aylık Finans Raporu</Typography>
        <Stack direction="row" spacing={1}>
          <Button startIcon={<PictureAsPdfIcon />} onClick={openPdf}>PDF</Button>
          <Button variant="contained" startIcon={<MailIcon />} onClick={() => setMailOpen(true)}>
            Mail Gönder
          </Button>
        </Stack>
      </Stack>

      {message && <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>{message.text}</Alert>}

      <Card sx={{ mb: 2 }}><CardContent>
        <Stack direction="row" alignItems="center" justifyContent="center" spacing={3}>
          <IconButton onClick={() => shift(-1)}><ChevronLeftIcon /></IconButton>
          <Typography variant="h6">{summary.label}</Typography>
          <IconButton onClick={() => shift(1)}><ChevronRightIcon /></IconButton>
        </Stack>
      </CardContent></Card>

      <Grid container spacing={2} mb={2}>
        {[
          ["Hakediş", summary.income_try, "primary.main"],
          ["Tahsilat", summary.collected_try, "success.main"],
          ["Masraf", summary.expense_try, "warning.main"],
          ["Net kâr", summary.net_try, Number(summary.net_try) >= 0 ? "success.main" : "error.main"],
        ].map(([label, value, color]) => (
          <Grid item xs={6} md={3} key={label}>
            <Card><CardContent>
              <Typography variant="body2" color="text.secondary">{label}</Typography>
              <Typography variant="h5" fontWeight={700} color={color}>{money(value)}</Typography>
            </CardContent></Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2} mb={2}>
        {[
          ["Kasa akışı", money(summary.cash_flow_try)],
          ["Servis sayısı", String(summary.service_count)],
          ["Yakıt", Number(summary.fuel_liters) > 0 ? `${Number(summary.fuel_liters)} lt` : "-"],
          ["Yansıtılacak masraf", money(summary.billable_expense_try)],
        ].map(([label, value]) => (
          <Grid item xs={6} md={3} key={label}>
            <Card><CardContent>
              <Typography variant="body2" color="text.secondary">{label}</Typography>
              <Typography variant="h6" fontWeight={600}>{value}</Typography>
            </CardContent></Card>
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} md={6}>
          <Card><CardContent>
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>Masraf dağılımı</Typography>
            {summary.expense_by_category.length === 0 && (
              <Typography variant="body2" color="text.secondary">Bu ay masraf kaydı yok.</Typography>
            )}
            {summary.expense_by_category.map((row) => (
              <Box key={row.category} mb={1.5}>
                <Stack direction="row" justifyContent="space-between">
                  <Typography variant="body2">{row.label}</Typography>
                  <Typography variant="body2" fontWeight={600}>{money(row.amount)}</Typography>
                </Stack>
                <Box sx={{ height: 6, bgcolor: "grey.200", borderRadius: 3, mt: 0.5 }}>
                  <Box sx={{
                    height: 6, borderRadius: 3, bgcolor: "primary.main",
                    width: `${Math.max(2, (Number(row.amount) / maxExpense) * 100)}%`,
                  }} />
                </Box>
              </Box>
            ))}
          </CardContent></Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card><CardContent>
            <Typography variant="subtitle1" fontWeight={600} gutterBottom>Müşteri bazında</Typography>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Müşteri</TableCell>
                  <TableCell align="right">Hakediş</TableCell>
                  <TableCell align="right">Tahsilat</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {summary.by_customer.map((row) => (
                  <TableRow key={row.customer_id}>
                    <TableCell>{row.customer}</TableCell>
                    <TableCell align="right">{money(row.income)}</TableCell>
                    <TableCell align="right">{money(row.collected)}</TableCell>
                  </TableRow>
                ))}
                {summary.by_customer.length === 0 && (
                  <TableRow><TableCell colSpan={3} sx={{ color: "text.secondary" }}>
                    Bu ay hareket yok.
                  </TableCell></TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent></Card>
        </Grid>

        {overdue?.count > 0 && (
          <Grid item xs={12}>
            <Card><CardContent>
              <Typography variant="subtitle1" fontWeight={600} gutterBottom color="error.main">
                Ödemesi geciken alacaklar ({money(overdue.total)})
              </Typography>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Müşteri</TableCell>
                    <TableCell>Telefon</TableCell>
                    <TableCell>Vade / Söz</TableCell>
                    <TableCell align="right">Gecikme</TableCell>
                    <TableCell align="right">Tutar</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {overdue.items.map((item, index) => (
                    <TableRow key={index}>
                      <TableCell><b>{item.customer}</b></TableCell>
                      <TableCell>{item.phone || "-"}</TableCell>
                      <TableCell>
                        {item.broken_promise ? "Söz: " : "Vade: "}
                        {item.deadline ? new Date(item.deadline).toLocaleDateString("tr-TR") : "-"}
                      </TableCell>
                      <TableCell align="right">{item.days_late} gün</TableCell>
                      <TableCell align="right"><b>{money(item.open_try)}</b></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent></Card>
          </Grid>
        )}
      </Grid>

      <Dialog open={mailOpen} onClose={() => setMailOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{summary.label} raporunu gönder</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2} mt={1}>
            <TextField label="Alıcı(lar)" value={mailTo} onChange={(e) => setMailTo(e.target.value)}
              helperText="Boş bırakılırsa mail ayarlarındaki varsayılan alıcıya gider" fullWidth />
            <TextField label="Not" value={mailNote} onChange={(e) => setMailNote(e.target.value)}
              multiline rows={3} fullWidth />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setMailOpen(false)}>Vazgeç</Button>
          <Button variant="contained" onClick={sendMail} disabled={busy}>Gönder</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
