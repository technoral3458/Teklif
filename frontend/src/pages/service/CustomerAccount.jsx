import { useState, useEffect, useCallback } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Box, Typography, Card, CardContent, Grid, Stack, Table, TableHead, TableRow,
  TableCell, TableBody, Chip, Button, Alert, LinearProgress, Divider,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import WarningIcon from "@mui/icons-material/Warning";
import api from "../../api/client";
import { CURRENCY_SYMBOL, fmtDate, money } from "./financeUtils";


export default function CustomerAccount() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState(null);

  const load = useCallback(() => {
    api.get(`/service/accounts/${id}/`).then((r) => setData(r.data));
  }, [id]);

  useEffect(() => { load(); }, [load]);

  if (!data) return <LinearProgress />;

  const overdueDebts = data.open_debts.filter((d) => d.is_overdue);

  return (
    <Box>
      <Stack direction="row" alignItems="center" spacing={2} mb={2}>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate("/service/finance")}>Geri</Button>
        <Typography variant="h5" fontWeight={700}>{data.customer}</Typography>
      </Stack>

      <Grid container spacing={2} mb={2}>
        <Grid item xs={12} md={4}>
          <Card><CardContent>
            <Typography variant="body2" color="text.secondary">Bakiye</Typography>
            <Typography variant="h4" fontWeight={700}
              color={data.balance_try > 0 ? "warning.dark" : data.balance_try < 0 ? "info.dark" : "success.dark"}>
              {money(data.balance_try)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {data.balance_try > 0 ? "Müşteriden alacağımız"
                : data.balance_try < 0 ? "Fazla tahsilat" : "Hesap kapalı"}
            </Typography>
          </CardContent></Card>
        </Grid>
        <Grid item xs={6} md={4}>
          <Card><CardContent>
            <Typography variant="body2" color="text.secondary">Toplam hakediş</Typography>
            <Typography variant="h5" fontWeight={700}>{money(data.debit_try)}</Typography>
          </CardContent></Card>
        </Grid>
        <Grid item xs={6} md={4}>
          <Card><CardContent>
            <Typography variant="body2" color="text.secondary">Toplam tahsilat</Typography>
            <Typography variant="h5" fontWeight={700}>{money(data.credit_try)}</Typography>
          </CardContent></Card>
        </Grid>
      </Grid>

      {overdueDebts.length > 0 && (
        <Alert severity="error" icon={<WarningIcon />} sx={{ mb: 2 }}>
          <b>{overdueDebts.length} alacağın ödemesi gecikti ({money(data.overdue_try)}).</b>
          <Box component="ul" sx={{ m: "6px 0 0", pl: 2.5 }}>
            {overdueDebts.map((debt) => (
              <li key={debt.entry_id}>
                {money(debt.open_try)} — {debt.broken_promise ? "söz verilen tarih" : "vade"}{" "}
                {fmtDate(debt.deadline)}, <b>{debt.days_late} gün gecikme</b>
              </li>
            ))}
          </Box>
        </Alert>
      )}

      {data.open_debts.length > 0 && (
        <Card sx={{ mb: 2 }}><CardContent>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            Açık alacaklar ({data.open_debts.length})
          </Typography>
          <Table size="small">
            <TableBody>
              {data.open_debts.map((debt) => (
                <TableRow key={debt.entry_id}>
                  <TableCell>{fmtDate(debt.date)}</TableCell>
                  <TableCell>{debt.description || "Hakediş"}</TableCell>
                  <TableCell>
                    {debt.deadline
                      ? `${debt.broken_promise ? "Söz" : "Vade"}: ${fmtDate(debt.deadline)}`
                      : "Vade yok"}
                  </TableCell>
                  <TableCell>
                    {debt.is_overdue
                      ? <Chip size="small" color="error" label={`${debt.days_late} gün gecikme`} />
                      : <Chip size="small" color="default" label="vadesinde" />}
                  </TableCell>
                  <TableCell align="right"><b>{money(debt.open_try)}</b></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent></Card>
      )}

      <Card><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>
          Hareketler ({data.entries.length})
        </Typography>
        <Divider sx={{ mb: 1 }} />
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Tarih</TableCell>
              <TableCell>Tip</TableCell>
              <TableCell>Açıklama</TableCell>
              <TableCell>Belge</TableCell>
              <TableCell align="right">Tutar</TableCell>
              <TableCell align="right">TL karşılığı</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.entries.map((entry) => (
              <TableRow key={entry.id} hover>
                <TableCell>{fmtDate(entry.date)}</TableCell>
                <TableCell>
                  <Chip size="small" label={entry.type_label}
                    color={entry.type === "BORC" ? "warning" : "success"} />
                </TableCell>
                <TableCell>
                  {entry.description || "-"}
                  {entry.report_no ? (
                    <Typography variant="caption" display="block" color="text.secondary">
                      {entry.report_no}
                    </Typography>
                  ) : null}
                </TableCell>
                <TableCell>{entry.document_no || "-"}</TableCell>
                <TableCell align="right">{money(entry.amount, CURRENCY_SYMBOL[entry.currency])}</TableCell>
                <TableCell align="right">{money(entry.try_amount)}</TableCell>
              </TableRow>
            ))}
            {data.entries.length === 0 && (
              <TableRow><TableCell colSpan={6} align="center" sx={{ py: 4, color: "text.secondary" }}>
                Hareket yok.
              </TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent></Card>
    </Box>
  );
}
