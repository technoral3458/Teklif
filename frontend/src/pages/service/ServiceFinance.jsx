import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, Card, CardContent, Grid, Stack, Tabs, Tab, Table, TableHead,
  TableRow, TableCell, TableBody, Chip, Button, IconButton, Alert, Dialog,
  DialogTitle, DialogContent, DialogActions, TextField, MenuItem, FormControlLabel,
  Checkbox, Tooltip, LinearProgress,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import WarningIcon from "@mui/icons-material/Warning";
import InsertChartIcon from "@mui/icons-material/InsertChart";
import api from "../../api/client";

import {
  CURRENCIES, CURRENCY_SYMBOL, EXPENSE_CATEGORIES, LEDGER_TYPES,
  PAYMENT_METHODS as METHODS, fmtDate, money, todayISO as today,
} from "./financeUtils";

export default function ServiceFinance() {
  const [tab, setTab] = useState("accounts");
  const [accounts, setAccounts] = useState(null);
  const [overdue, setOverdue] = useState(null);
  const [entries, setEntries] = useState([]);
  const [expenses, setExpenses] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [rates, setRates] = useState(null);
  const [entryForm, setEntryForm] = useState(null);
  const [expenseForm, setExpenseForm] = useState(null);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const load = useCallback(() => {
    api.get("/service/accounts/").then((r) => setAccounts(r.data));
    api.get("/service/overdue/").then((r) => setOverdue(r.data));
    api.get("/service/ledger/").then((r) => setEntries(r.data));
    api.get("/service/expenses/").then((r) => setExpenses(r.data));
    api.get("/service/customers/").then((r) => setCustomers(r.data));
    api.get("/service/finance-settings/").then((r) => setRates(r.data)).catch(() => {});
  }, []);

  useEffect(() => { load(); }, [load]);

  const suggestedRate = (currency) => {
    if (currency === "TRY") return "1";
    if (!rates) return "";
    return String(currency === "USD" ? rates.usd_rate : rates.eur_rate);
  };

  const saveEntry = async () => {
    setError("");
    const payload = {
      ...entryForm,
      rate: entryForm.currency === "TRY" ? 1 : entryForm.rate,
      due_date: entryForm.due_date || null,
      promised_date: entryForm.promised_date || null,
      report: entryForm.report || null,
    };
    try {
      if (entryForm.id) await api.put(`/service/ledger/${entryForm.id}/`, payload);
      else await api.post("/service/ledger/", payload);
      setEntryForm(null);
      load();
    } catch (err) {
      setError(err.response?.data?.rate?.[0] || "Cari hareket kaydedilemedi.");
    }
  };

  const saveExpense = async () => {
    setError("");
    const payload = {
      ...expenseForm,
      rate: expenseForm.currency === "TRY" ? 1 : expenseForm.rate,
      customer: expenseForm.customer || null,
      report: expenseForm.report || null,
      quantity: expenseForm.quantity || 0,
    };
    try {
      if (expenseForm.id) await api.put(`/service/expenses/${expenseForm.id}/`, payload);
      else await api.post("/service/expenses/", payload);
      setExpenseForm(null);
      load();
    } catch (err) {
      setError(err.response?.data?.rate?.[0] || "Masraf kaydedilemedi.");
    }
  };

  const removeEntry = async (id) => {
    if (!window.confirm("Cari hareket silinsin mi?")) return;
    await api.delete(`/service/ledger/${id}/`);
    load();
  };

  const removeExpense = async (id) => {
    if (!window.confirm("Masraf silinsin mi?")) return;
    await api.delete(`/service/expenses/${id}/`);
    load();
  };

  if (!accounts) return <LinearProgress />;

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" mb={2}>
        <Typography variant="h5" fontWeight={700}>Cari Takip</Typography>
        <Stack direction="row" spacing={1}>
          <Button startIcon={<InsertChartIcon />} onClick={() => navigate("/service/monthly-report")}>
            Aylık Rapor
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => {
            if (tab === "expenses") {
              setExpenseForm({ category: "YAKIT", date: today(), amount: "", currency: "TRY", rate: "1", description: "", quantity: "", billable: false });
            } else {
              setEntryForm({ customer: "", type: "TAHSILAT", date: today(), amount: "", currency: "TRY", rate: "1", description: "", document_no: "", payment_method: "NAKIT" });
            }
          }}>
            {tab === "expenses" ? "Masraf Ekle" : "Hareket Ekle"}
          </Button>
        </Stack>
      </Stack>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError("")}>{error}</Alert>}

      <Grid container spacing={2} mb={2}>
        <Grid item xs={12} md={4}>
          <Card><CardContent>
            <Typography variant="body2" color="text.secondary">Toplam alacak</Typography>
            <Typography variant="h4" fontWeight={700}>{money(accounts.total_receivable)}</Typography>
          </CardContent></Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card sx={{ bgcolor: accounts.total_overdue > 0 ? "error.light" : undefined }}><CardContent>
            <Typography variant="body2" color="text.secondary">Vadesi geçen</Typography>
            <Typography variant="h4" fontWeight={700} color={accounts.total_overdue > 0 ? "error.dark" : "inherit"}>
              {money(accounts.total_overdue)}
            </Typography>
          </CardContent></Card>
        </Grid>
        <Grid item xs={12} md={4}>
          <Card><CardContent>
            <Typography variant="body2" color="text.secondary">Cari hesap sayısı</Typography>
            <Typography variant="h4" fontWeight={700}>{accounts.accounts.length}</Typography>
          </CardContent></Card>
        </Grid>
      </Grid>

      {overdue?.count > 0 && (
        <Alert severity="error" icon={<WarningIcon />} sx={{ mb: 2 }}>
          <b>Ödemesi geciken {overdue.count} alacak ({money(overdue.total)}).</b>
          <Box component="ul" sx={{ m: "6px 0 0", pl: 2.5 }}>
            {overdue.items.slice(0, 5).map((item, index) => (
              <li key={index}>
                {item.customer} — {money(item.open_try)} •{" "}
                {item.broken_promise ? "söz verilen tarih" : "vade"} {fmtDate(item.deadline)},{" "}
                <b>{item.days_late} gün gecikme</b>
                {item.phone ? ` • ${item.phone}` : ""}
              </li>
            ))}
          </Box>
        </Alert>
      )}

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab value="accounts" label={`Hesaplar (${accounts.accounts.length})`} />
        <Tab value="entries" label={`Hareketler (${entries.length})`} />
        <Tab value="expenses" label={`Masraflar (${expenses.length})`} />
      </Tabs>

      <Card><CardContent>
        {tab === "accounts" && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Müşteri</TableCell>
                <TableCell align="right">Hakediş</TableCell>
                <TableCell align="right">Tahsilat</TableCell>
                <TableCell align="right">Bakiye</TableCell>
                <TableCell align="right">Gecikmiş</TableCell>
                <TableCell>Son hareket</TableCell>
                <TableCell align="right">İşlem</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {accounts.accounts.map((account) => (
                <TableRow key={account.customer_id} hover>
                  <TableCell><b>{account.customer}</b></TableCell>
                  <TableCell align="right">{money(account.debit_try)}</TableCell>
                  <TableCell align="right">{money(account.credit_try)}</TableCell>
                  <TableCell align="right">
                    <Typography fontWeight={700}
                      color={account.balance_try > 0 ? "warning.dark" : account.balance_try < 0 ? "info.dark" : "success.dark"}>
                      {money(account.balance_try)}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">
                    {account.has_overdue
                      ? <Chip size="small" color="error" label={money(account.overdue_try)} />
                      : "-"}
                  </TableCell>
                  <TableCell>{fmtDate(account.last_activity)}</TableCell>
                  <TableCell align="right">
                    <Tooltip title="Ekstre">
                      <IconButton size="small" onClick={() => navigate(`/service/account/${account.customer_id}`)}>
                        <InsertChartIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Tahsilat ekle">
                      <IconButton size="small" onClick={() => setEntryForm({
                        customer: account.customer_id, type: "TAHSILAT", date: today(),
                        amount: "", currency: "TRY", rate: "1", description: "",
                        document_no: "", payment_method: "HAVALE",
                      })}>
                        <AddIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
              {accounts.accounts.length === 0 && (
                <TableRow><TableCell colSpan={7} align="center" sx={{ py: 4, color: "text.secondary" }}>
                  Cari hesap yok. Önce müşteri ekleyin.
                </TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        )}

        {tab === "entries" && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Tarih</TableCell>
                <TableCell>Müşteri</TableCell>
                <TableCell>Tip</TableCell>
                <TableCell>Açıklama</TableCell>
                <TableCell align="right">Tutar</TableCell>
                <TableCell align="right">TL karşılığı</TableCell>
                <TableCell>Vade / Söz</TableCell>
                <TableCell align="right">İşlem</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {entries.map((entry) => (
                <TableRow key={entry.id} hover>
                  <TableCell>{fmtDate(entry.date)}</TableCell>
                  <TableCell>{entry.customer_name}</TableCell>
                  <TableCell>
                    <Chip size="small" label={entry.type_label}
                      color={entry.type === "BORC" ? "warning" : "success"} />
                  </TableCell>
                  <TableCell>
                    {entry.description || "-"}
                    {entry.report_no ? <Typography variant="caption" display="block" color="text.secondary">{entry.report_no}</Typography> : null}
                  </TableCell>
                  <TableCell align="right">{money(entry.amount, CURRENCY_SYMBOL[entry.currency])}</TableCell>
                  <TableCell align="right">{money(entry.try_amount)}</TableCell>
                  <TableCell>
                    {entry.promised_date ? `Söz: ${fmtDate(entry.promised_date)}` : entry.due_date ? fmtDate(entry.due_date) : "-"}
                  </TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={() => setEntryForm({
                      ...entry, due_date: entry.due_date || "", promised_date: entry.promised_date || "",
                    })}><EditIcon fontSize="small" /></IconButton>
                    <IconButton size="small" onClick={() => removeEntry(entry.id)}><DeleteIcon fontSize="small" /></IconButton>
                  </TableCell>
                </TableRow>
              ))}
              {entries.length === 0 && (
                <TableRow><TableCell colSpan={8} align="center" sx={{ py: 4, color: "text.secondary" }}>
                  Cari hareket yok.
                </TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        )}

        {tab === "expenses" && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Tarih</TableCell>
                <TableCell>Kategori</TableCell>
                <TableCell>Açıklama</TableCell>
                <TableCell>Servis / Müşteri</TableCell>
                <TableCell align="right">Tutar</TableCell>
                <TableCell align="right">TL</TableCell>
                <TableCell align="right">İşlem</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {expenses.map((expense) => (
                <TableRow key={expense.id} hover>
                  <TableCell>{fmtDate(expense.date)}</TableCell>
                  <TableCell>
                    {expense.category_label}
                    {expense.category === "YAKIT" && Number(expense.quantity) > 0 && (
                      <Typography variant="caption" display="block" color="text.secondary">
                        {Number(expense.quantity)} lt
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{expense.description || "-"}</TableCell>
                  <TableCell>
                    {expense.report_no || expense.customer_name || "-"}
                    {expense.billable && <Chip size="small" sx={{ ml: 1 }} label="yansıtılacak" color="info" />}
                  </TableCell>
                  <TableCell align="right">{money(expense.amount, CURRENCY_SYMBOL[expense.currency])}</TableCell>
                  <TableCell align="right">{money(expense.try_amount)}</TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={() => setExpenseForm({ ...expense })}><EditIcon fontSize="small" /></IconButton>
                    <IconButton size="small" onClick={() => removeExpense(expense.id)}><DeleteIcon fontSize="small" /></IconButton>
                  </TableCell>
                </TableRow>
              ))}
              {expenses.length === 0 && (
                <TableRow><TableCell colSpan={7} align="center" sx={{ py: 4, color: "text.secondary" }}>
                  Masraf kaydı yok. Yakıt, otel, yemek, otoyol gibi harcamaları buraya girin.
                </TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        )}
      </CardContent></Card>

      {entryForm && (
        <Dialog open onClose={() => setEntryForm(null)} maxWidth="sm" fullWidth>
          <DialogTitle>{entryForm.id ? "Cari Hareketi Düzenle" : "Yeni Cari Hareket"}</DialogTitle>
          <DialogContent dividers>
            <Grid container spacing={2} mt={0}>
              <Grid item xs={12}>
                <TextField select label="Müşteri *" value={entryForm.customer || ""} fullWidth
                  onChange={(e) => setEntryForm({ ...entryForm, customer: e.target.value })}>
                  {customers.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
                </TextField>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField select label="Hareket tipi" value={entryForm.type} fullWidth
                  onChange={(e) => setEntryForm({ ...entryForm, type: e.target.value })}>
                  {LEDGER_TYPES.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                </TextField>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField type="date" label="Tarih" InputLabelProps={{ shrink: true }} fullWidth
                  value={entryForm.date} onChange={(e) => setEntryForm({ ...entryForm, date: e.target.value })} />
              </Grid>
              <Grid item xs={6} md={4}>
                <TextField label="Tutar *" value={entryForm.amount} fullWidth
                  onChange={(e) => setEntryForm({ ...entryForm, amount: e.target.value })} />
              </Grid>
              <Grid item xs={6} md={4}>
                <TextField select label="Para birimi" value={entryForm.currency} fullWidth
                  onChange={(e) => setEntryForm({
                    ...entryForm, currency: e.target.value, rate: suggestedRate(e.target.value),
                  })}>
                  {CURRENCIES.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                </TextField>
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField label="Kur (TL)" value={entryForm.rate} fullWidth
                  disabled={entryForm.currency === "TRY"}
                  helperText={entryForm.currency !== "TRY" && entryForm.amount && entryForm.rate
                    ? `TL karşılığı: ${money(Number(entryForm.amount) * Number(entryForm.rate))}` : " "}
                  onChange={(e) => setEntryForm({ ...entryForm, rate: e.target.value })} />
              </Grid>
              <Grid item xs={12}>
                <TextField label="Açıklama" value={entryForm.description || ""} fullWidth
                  onChange={(e) => setEntryForm({ ...entryForm, description: e.target.value })} />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField label="Belge / fatura no" value={entryForm.document_no || ""} fullWidth
                  onChange={(e) => setEntryForm({ ...entryForm, document_no: e.target.value })} />
              </Grid>
              {entryForm.type === "BORC" ? (
                <>
                  <Grid item xs={12} md={6}>
                    <TextField type="date" label="Vade tarihi" InputLabelProps={{ shrink: true }} fullWidth
                      value={entryForm.due_date || ""}
                      onChange={(e) => setEntryForm({ ...entryForm, due_date: e.target.value })} />
                  </Grid>
                  <Grid item xs={12} md={6}>
                    <TextField type="date" label="Müşterinin söz verdiği tarih"
                      InputLabelProps={{ shrink: true }} fullWidth
                      helperText="Vadesi geçtikten sonra verilen söz — geciktiğinde ayrıca uyarılır"
                      value={entryForm.promised_date || ""}
                      onChange={(e) => setEntryForm({ ...entryForm, promised_date: e.target.value })} />
                  </Grid>
                </>
              ) : (
                <Grid item xs={12} md={6}>
                  <TextField select label="Ödeme şekli" value={entryForm.payment_method} fullWidth
                    onChange={(e) => setEntryForm({ ...entryForm, payment_method: e.target.value })}>
                    {METHODS.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                  </TextField>
                </Grid>
              )}
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setEntryForm(null)}>Vazgeç</Button>
            <Button variant="contained" onClick={saveEntry}
              disabled={!entryForm.customer || !entryForm.amount}>Kaydet</Button>
          </DialogActions>
        </Dialog>
      )}

      {expenseForm && (
        <Dialog open onClose={() => setExpenseForm(null)} maxWidth="sm" fullWidth>
          <DialogTitle>{expenseForm.id ? "Masrafı Düzenle" : "Yeni Masraf"}</DialogTitle>
          <DialogContent dividers>
            <Grid container spacing={2} mt={0}>
              <Grid item xs={12} md={6}>
                <TextField select label="Kategori" value={expenseForm.category} fullWidth
                  onChange={(e) => setExpenseForm({ ...expenseForm, category: e.target.value })}>
                  {EXPENSE_CATEGORIES.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                </TextField>
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField type="date" label="Tarih" InputLabelProps={{ shrink: true }} fullWidth
                  value={expenseForm.date} onChange={(e) => setExpenseForm({ ...expenseForm, date: e.target.value })} />
              </Grid>
              <Grid item xs={6} md={4}>
                <TextField label="Tutar *" value={expenseForm.amount} fullWidth
                  onChange={(e) => setExpenseForm({ ...expenseForm, amount: e.target.value })} />
              </Grid>
              <Grid item xs={6} md={4}>
                <TextField select label="Para birimi" value={expenseForm.currency} fullWidth
                  onChange={(e) => setExpenseForm({
                    ...expenseForm, currency: e.target.value, rate: suggestedRate(e.target.value),
                  })}>
                  {CURRENCIES.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                </TextField>
              </Grid>
              <Grid item xs={12} md={4}>
                <TextField label="Kur (TL)" value={expenseForm.rate} fullWidth
                  disabled={expenseForm.currency === "TRY"}
                  onChange={(e) => setExpenseForm({ ...expenseForm, rate: e.target.value })} />
              </Grid>
              {expenseForm.category === "YAKIT" && (
                <Grid item xs={12} md={6}>
                  <TextField label="Litre" value={expenseForm.quantity || ""} fullWidth
                    helperText="Aylık raporda toplam yakıt olarak görünür"
                    onChange={(e) => setExpenseForm({ ...expenseForm, quantity: e.target.value })} />
                </Grid>
              )}
              <Grid item xs={12}>
                <TextField label="Açıklama" value={expenseForm.description || ""} fullWidth
                  onChange={(e) => setExpenseForm({ ...expenseForm, description: e.target.value })} />
              </Grid>
              <Grid item xs={12} md={6}>
                <TextField select label="Müşteri (opsiyonel)" value={expenseForm.customer || ""} fullWidth
                  onChange={(e) => setExpenseForm({ ...expenseForm, customer: e.target.value })}>
                  <MenuItem value="">-</MenuItem>
                  {customers.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
                </TextField>
              </Grid>
              <Grid item xs={12}>
                <FormControlLabel
                  control={<Checkbox checked={!!expenseForm.billable}
                    onChange={(e) => setExpenseForm({ ...expenseForm, billable: e.target.checked })} />}
                  label="Müşteriye yansıtılacak"
                />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setExpenseForm(null)}>Vazgeç</Button>
            <Button variant="contained" onClick={saveExpense} disabled={!expenseForm.amount}>Kaydet</Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  );
}
