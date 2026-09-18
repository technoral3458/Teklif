import { useState, useEffect } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Box, Typography, Card, CardContent, TextField, MenuItem, Button, Grid, Stack,
  Chip, IconButton, Alert, Divider, Table, TableBody, TableRow, TableCell,
  Dialog, DialogTitle, DialogContent, DialogActions, FormControlLabel, Checkbox,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import SaveIcon from "@mui/icons-material/Save";
import PhotoCameraIcon from "@mui/icons-material/PhotoCamera";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import api from "../../api/client";
import { CURRENCIES, EXPENSE_CATEGORIES, fmtDate, money, todayISO } from "./financeUtils";

const TYPES = [
  ["ARIZA", "Arıza"], ["PERIYODIK_BAKIM", "Periyodik Bakım"], ["KURULUM", "Kurulum"],
  ["DEVREYE_ALMA", "Devreye Alma"], ["REVIZYON", "Revizyon"], ["KESIF", "Keşif"],
  ["EGITIM", "Eğitim"], ["GARANTI", "Garanti"],
];
const STATUSES = [
  ["TASLAK", "Taslak"], ["ACIK", "Açık"], ["COZULDU", "Çözüldü"],
  ["GECICI_COZUM", "Geçici Çözüm"], ["PARCA_BEKLIYOR", "Parça Bekliyor"],
  ["TEKRAR_ZIYARET", "Tekrar Ziyaret Gerekli"],
];
const PRIORITIES = [["DUSUK", "Düşük"], ["NORMAL", "Normal"], ["YUKSEK", "Yüksek"], ["KRITIK", "Kritik / Duruş"]];
const DEPARTMENTS = [
  ["MEKANIK", "Mekanik"], ["ELEKTRIK", "Elektrik"], ["ELEKTRONIK", "Elektronik"],
  ["PNOMATIK", "Pnömatik"], ["HIDROLIK", "Hidrolik"], ["YAZILIM", "Yazılım / PLC"],
  ["KALIBRASYON", "Kalibrasyon"], ["OTOMASYON", "Otomasyon"],
  ["TEMIZLIK", "Temizlik / Yağlama"], ["DIGER", "Diğer"],
];
const PART_STATUSES = [
  ["TAKILDI", "Takıldı"], ["GEREKLI", "Gerekli"],
  ["SIPARIS", "Sipariş Edilecek"], ["TEKLIF", "Teklif Verilecek"],
];
const PHOTO_TAGS = [
  ["ARIZA", "Arıza"], ["ONCESI", "İşlem Öncesi"], ["SONRASI", "İşlem Sonrası"],
  ["PARCA", "Parça"], ["ETIKET", "Makine Etiketi"], ["DIGER", "Diğer"],
];

const emptyReport = {
  customer: "", machine: "", type: "ARIZA", status: "ACIK", priority: "NORMAL",
  service_date: new Date().toISOString().slice(0, 10),
  start_time: "", end_time: "", travel_km: "0",
  fault_description: "", fault_cause: "", work_done: "", recommendations: "",
  technician_name: "", customer_rep: "", next_maintenance: "",
};

export default function ServiceReportForm() {
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [form, setForm] = useState(emptyReport);
  const [departments, setDepartments] = useState([]);
  const [parts, setParts] = useState([]);
  const [photos, setPhotos] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [machines, setMachines] = useState([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [charge, setCharge] = useState({ amount: "", currency: "TRY", rate: "1", due_date: "" });
  const [expenses, setExpenses] = useState([]);
  const [expenseForm, setExpenseForm] = useState(null);
  const [rates, setRates] = useState(null);
  const [rateBusy, setRateBusy] = useState(false);

  const loadMachines = (customerId) => {
    if (!customerId) { setMachines([]); return; }
    api.get(`/service/machines/?customer=${customerId}`).then((r) => setMachines(r.data));
  };

  useEffect(() => {
    api.get("/service/customers/").then((r) => setCustomers(r.data));
    api.get("/service/finance-settings/").then((r) => setRates(r.data)).catch(() => {});
  }, []);

  const loadExpenses = (reportId) => {
    api.get(`/service/expenses/?report=${reportId}`).then((r) => setExpenses(r.data));
  };

  /** Ayarlardaki kuru döndürür; yoksa internetten çekmeyi dener. */
  const ensureRate = async (currency, apply) => {
    if (currency === "TRY") { apply("1"); return; }
    const known = rates && Number(currency === "USD" ? rates.usd_rate : rates.eur_rate);
    if (known > 0) { apply(String(known)); return; }
    setRateBusy(true);
    try {
      const res = await api.post("/service/finance-settings/refresh-rates/");
      setRates(res.data);
      apply(String(currency === "USD" ? res.data.usd_rate : res.data.eur_rate));
    } catch {
      setError("Kur alınamadı. Kuru elle girin, yoksa TL karşılığı yanlış hesaplanır.");
      apply("");
    } finally {
      setRateBusy(false);
    }
  };

  // Makine listesinden "servis aç" ile gelindiyse makine ve müşterisi hazır seçilir
  useEffect(() => {
    const machineId = searchParams.get("machine");
    if (!machineId || id) return;
    api.get(`/service/machines/${machineId}/`).then((r) => {
      setForm((prev) => ({ ...prev, customer: r.data.customer, machine: r.data.id }));
      loadMachines(r.data.customer);
    });
  }, [searchParams, id]);

  useEffect(() => {
    if (!id) return;
    api.get(`/service/reports/${id}/`).then((r) => {
      const d = r.data;
      setForm({
        customer: d.customer || "", machine: d.machine || "", type: d.type,
        status: d.status, priority: d.priority, service_date: d.service_date,
        start_time: d.start_time?.slice(0, 5) || "", end_time: d.end_time?.slice(0, 5) || "",
        travel_km: d.travel_km, fault_description: d.fault_description,
        fault_cause: d.fault_cause, work_done: d.work_done,
        recommendations: d.recommendations, technician_name: d.technician_name || "",
        customer_rep: d.customer_rep || "", next_maintenance: d.next_maintenance || "",
      });
      setDepartments(d.departments.map((x) => ({ department: x.department, work: x.work })));
      setParts(d.parts.map((x) => ({
        name: x.name, code: x.code, quantity: x.quantity, unit: x.unit, status: x.status, note: x.note,
      })));
      setPhotos(d.photos);
      if (d.customer) loadMachines(d.customer);
      if (d.charge) {
        setCharge({
          amount: String(d.charge.amount),
          currency: d.charge.currency,
          rate: String(d.charge.rate),
          due_date: d.charge.due_date || "",
        });
      }
      loadExpenses(d.id);
    });
  }, [id]);

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const billableTotal = expenses
    .filter((e) => e.billable)
    .reduce((sum, e) => sum + Number(e.try_amount), 0);
  const feeTry = Number(charge.amount || 0) * Number(charge.rate || 0);

  // Müşteri değişince makine listesi yenilenir, seçili makine sıfırlanır
  const selectCustomer = (event) => {
    const customerId = event.target.value;
    setForm({ ...form, customer: customerId, machine: "" });
    loadMachines(customerId);
  };

  const toggleDepartment = (code) => {
    setDepartments((prev) =>
      prev.some((d) => d.department === code)
        ? prev.filter((d) => d.department !== code)
        : [...prev, { department: code, work: "" }]
    );
  };

  const save = async () => {
    setError(""); setSaving(true);
    const payload = {
      ...form,
      machine: form.machine || null,
      start_time: form.start_time || null,
      end_time: form.end_time || null,
      next_maintenance: form.next_maintenance || null,
      travel_km: form.travel_km || 0,
      departments, parts,
      charge_amount: charge.amount || null,
      charge_currency: charge.currency,
      charge_rate: charge.currency === "TRY" ? 1 : charge.rate || null,
      charge_due_date: charge.due_date || null,
    };
    try {
      const res = id
        ? await api.put(`/service/reports/${id}/`, payload)
        : await api.post("/service/reports/", payload);
      navigate(`/service/report/${res.data.id}`);
    } catch (err) {
      setError(JSON.stringify(err.response?.data || "Kayıt başarısız"));
    } finally {
      setSaving(false);
    }
  };

  const saveExpense = async () => {
    const payload = {
      ...expenseForm,
      report: id,
      customer: form.customer || null,
      rate: expenseForm.currency === "TRY" ? 1 : expenseForm.rate,
      quantity: expenseForm.quantity || 0,
    };
    try {
      if (expenseForm.id) await api.put(`/service/expenses/${expenseForm.id}/`, payload);
      else await api.post("/service/expenses/", payload);
      setExpenseForm(null);
      loadExpenses(id);
    } catch {
      setError("Masraf kaydedilemedi. Döviz girdiyseniz kur alanını doldurun.");
    }
  };

  const removeExpense = async (expenseId) => {
    if (!window.confirm("Masraf silinsin mi?")) return;
    await api.delete(`/service/expenses/${expenseId}/`);
    loadExpenses(id);
  };

  const uploadPhoto = async (event) => {
    const file = event.target.files?.[0];
    if (!file || !id) return;
    const data = new FormData();
    data.append("image", file);
    data.append("tag", "ARIZA");
    const res = await api.post(`/service/reports/${id}/photos/`, data, {
      headers: { "Content-Type": "multipart/form-data" },
    });
    setPhotos([...photos, res.data]);
    event.target.value = "";
  };

  const deletePhoto = async (photoId) => {
    await api.delete(`/service/reports/${id}/photos/${photoId}/`);
    setPhotos(photos.filter((p) => p.id !== photoId));
  };

  const updatePhotoMeta = async (photo, field, value) => {
    setPhotos(photos.map((p) => (p.id === photo.id ? { ...p, [field]: value } : p)));
    await api.patch(`/service/reports/${id}/photos/${photo.id}/`, { [field]: value });
  };

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} mb={2}>
        {id ? "Servis Raporunu Düzenle" : "Yeni Servis Raporu"}
      </Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Card sx={{ mb: 2 }}><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Müşteri ve Makine</Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <TextField select label="Müşteri *" value={form.customer} onChange={selectCustomer} fullWidth>
              {customers.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField select label="Makine" value={form.machine} onChange={set("machine")} fullWidth
              disabled={!form.customer}>
              <MenuItem value="">-</MenuItem>
              {machines.map((m) => (
                <MenuItem key={m.id} value={m.id}>
                  {m.display_name}{m.serial_no ? ` (${m.serial_no})` : ""}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
        </Grid>
      </CardContent></Card>

      <Card sx={{ mb: 2 }}><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Servis Bilgileri</Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={4}>
            <TextField select label="Servis tipi" value={form.type} onChange={set("type")} fullWidth>
              {TYPES.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField select label="Durum" value={form.status} onChange={set("status")} fullWidth>
              {STATUSES.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={4}>
            <TextField select label="Öncelik" value={form.priority} onChange={set("priority")} fullWidth>
              {PRIORITIES.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={3}>
            <TextField type="date" label="Servis tarihi" value={form.service_date} onChange={set("service_date")}
              InputLabelProps={{ shrink: true }} fullWidth />
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField type="time" label="Başlangıç" value={form.start_time} onChange={set("start_time")}
              InputLabelProps={{ shrink: true }} fullWidth />
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField type="time" label="Bitiş" value={form.end_time} onChange={set("end_time")}
              InputLabelProps={{ shrink: true }} fullWidth />
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField label="Yol (km)" value={form.travel_km} onChange={set("travel_km")} fullWidth />
          </Grid>
          <Grid item xs={6} md={3}>
            <TextField label="Teknisyen" value={form.technician_name} onChange={set("technician_name")} fullWidth />
          </Grid>
        </Grid>
      </CardContent></Card>

      <Card sx={{ mb: 2 }}><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Çalışılan Bölümler</Typography>
        <Stack direction="row" flexWrap="wrap" gap={1} mb={2}>
          {DEPARTMENTS.map(([code, label]) => {
            const active = departments.some((d) => d.department === code);
            return (
              <Chip key={code} label={label} color={active ? "primary" : "default"}
                variant={active ? "filled" : "outlined"} onClick={() => toggleDepartment(code)} />
            );
          })}
        </Stack>
        <Stack spacing={2}>
          {departments.map((d, index) => (
            <TextField key={d.department} label={`${DEPARTMENTS.find(([c]) => c === d.department)?.[1]} — yapılan işlem`}
              value={d.work} multiline rows={2} fullWidth
              onChange={(e) => {
                const next = [...departments];
                next[index] = { ...d, work: e.target.value };
                setDepartments(next);
              }} />
          ))}
        </Stack>
      </CardContent></Card>

      <Card sx={{ mb: 2 }}><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Arıza ve Çözüm</Typography>
        <Stack spacing={2}>
          <TextField label="Arıza / talep tanımı" value={form.fault_description} onChange={set("fault_description")}
            multiline rows={3} fullWidth />
          <TextField label="Arıza nedeni (kök neden)" value={form.fault_cause} onChange={set("fault_cause")}
            multiline rows={3} fullWidth />
          <TextField label="Yapılan işlem / çözüm" value={form.work_done} onChange={set("work_done")}
            multiline rows={4} fullWidth />
          <TextField label="Öneriler" value={form.recommendations} onChange={set("recommendations")}
            multiline rows={2} fullWidth />
        </Stack>
      </CardContent></Card>

      <Card sx={{ mb: 2 }}><CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
          <Typography variant="subtitle1" fontWeight={600}>Yedek Parçalar</Typography>
          <Button size="small" startIcon={<AddIcon />}
            onClick={() => setParts([...parts, { name: "", code: "", quantity: 1, unit: "adet", status: "TAKILDI", note: "" }])}>
            Parça Ekle
          </Button>
        </Stack>
        <Table size="small">
          <TableBody>
            {parts.map((part, index) => (
              <TableRow key={index}>
                <TableCell sx={{ width: "32%" }}>
                  <TextField size="small" placeholder="Parça adı" value={part.name} fullWidth
                    onChange={(e) => { const n = [...parts]; n[index] = { ...part, name: e.target.value }; setParts(n); }} />
                </TableCell>
                <TableCell>
                  <TextField size="small" placeholder="Kod" value={part.code} fullWidth
                    onChange={(e) => { const n = [...parts]; n[index] = { ...part, code: e.target.value }; setParts(n); }} />
                </TableCell>
                <TableCell sx={{ width: 90 }}>
                  <TextField size="small" placeholder="Adet" value={part.quantity} fullWidth
                    onChange={(e) => { const n = [...parts]; n[index] = { ...part, quantity: e.target.value }; setParts(n); }} />
                </TableCell>
                <TableCell sx={{ width: 90 }}>
                  <TextField size="small" placeholder="Birim" value={part.unit} fullWidth
                    onChange={(e) => { const n = [...parts]; n[index] = { ...part, unit: e.target.value }; setParts(n); }} />
                </TableCell>
                <TableCell sx={{ width: 160 }}>
                  <TextField select size="small" value={part.status} fullWidth
                    onChange={(e) => { const n = [...parts]; n[index] = { ...part, status: e.target.value }; setParts(n); }}>
                    {PART_STATUSES.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                  </TextField>
                </TableCell>
                <TableCell align="right" sx={{ width: 50 }}>
                  <IconButton size="small" onClick={() => setParts(parts.filter((_, i) => i !== index))}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
            {parts.length === 0 && (
              <TableRow><TableCell colSpan={6} sx={{ color: "text.secondary" }}>
                Kullanılan veya temin edilmesi gereken parçaları ekleyin.
              </TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent></Card>

      <Card sx={{ mb: 2 }}><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Servis Bedeli</Typography>
        <Typography variant="body2" color="text.secondary" mb={2}>
          Girilen tutar müşterinin carisine borç olarak işlenir.
        </Typography>
        <Grid container spacing={2}>
          <Grid item xs={6} md={3}>
            <TextField label="Tutar" value={charge.amount} fullWidth
              onChange={(e) => setCharge({ ...charge, amount: e.target.value })} />
          </Grid>
          <Grid item xs={6} md={3}>
            <TextField select label="Para birimi" value={charge.currency} fullWidth
              onChange={(e) => {
                const currency = e.target.value;
                setCharge((prev) => ({ ...prev, currency, rate: currency === "TRY" ? "1" : "" }));
                ensureRate(currency, (rate) => setCharge((prev) => ({ ...prev, rate })));
              }}>
              {CURRENCIES.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={6} md={3}>
            <TextField label="Kur (TL)" value={charge.rate} fullWidth
              disabled={charge.currency === "TRY" || rateBusy}
              error={charge.currency !== "TRY" && !!charge.amount && !(Number(charge.rate) > 0)}
              helperText={
                rateBusy ? "Güncel kur alınıyor…"
                  : charge.currency !== "TRY" && !(Number(charge.rate) > 0) ? "Kur zorunlu"
                    : charge.currency !== "TRY" && charge.amount && charge.rate
                      ? `Cariye işlenecek: ${money(Number(charge.amount) * Number(charge.rate))}` : " "
              }
              onChange={(e) => setCharge({ ...charge, rate: e.target.value })} />
          </Grid>
          <Grid item xs={6} md={3}>
            <TextField type="date" label="Vade tarihi" InputLabelProps={{ shrink: true }} fullWidth
              value={charge.due_date}
              onChange={(e) => setCharge({ ...charge, due_date: e.target.value })} />
          </Grid>
        </Grid>
      </CardContent></Card>

      <Card sx={{ mb: 2 }}><CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
          <Typography variant="subtitle1" fontWeight={600}>
            Servis Masrafları
            {expenses.length > 0 && (
              <Typography component="span" variant="body2" color="text.secondary" sx={{ ml: 1 }}>
                toplam {money(expenses.reduce((sum, e) => sum + Number(e.try_amount), 0))} •
                yansıtılan {money(billableTotal)}
              </Typography>
            )}
          </Typography>
          <Button size="small" startIcon={<AddIcon />} disabled={!id}
            onClick={() => setExpenseForm({
              category: "YAKIT", date: todayISO(), amount: "", currency: "TRY",
              rate: "1", description: "", quantity: "", billable: false,
            })}>
            Masraf Ekle
          </Button>
        </Stack>
        {!id ? (
          <Alert severity="info">Masraf eklemek için önce raporu kaydedin.</Alert>
        ) : expenses.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            Yakıt, otel, yemek, otoyol gibi harcamaları ekleyin. &quot;Müşteriye yansıtılacak&quot;
            işaretli kalemler servis bedeline eklenir ve müşterinin carisine borç yazılır.
          </Typography>
        ) : (
          <Table size="small">
            <TableBody>
              {expenses.map((expense) => (
                <TableRow key={expense.id}>
                  <TableCell>{expense.category_label}</TableCell>
                  <TableCell>{fmtDate(expense.date)}</TableCell>
                  <TableCell>
                    {expense.description || "-"}
                    <Chip size="small" sx={{ ml: 1 }}
                      color={expense.billable ? "success" : "default"}
                      label={expense.billable ? "yansıtıldı" : "yansıtılmadı"} />
                    {expense.receipt && <Chip size="small" sx={{ ml: 0.5 }} label="fiş" variant="outlined" />}
                  </TableCell>
                  <TableCell align="right">{money(expense.try_amount)}</TableCell>
                  <TableCell align="right" sx={{ width: 90 }}>
                    <IconButton size="small" onClick={() => setExpenseForm({ ...expense })}>
                      <EditIcon fontSize="small" />
                    </IconButton>
                    <IconButton size="small" onClick={() => removeExpense(expense.id)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent></Card>

      {(Number(charge.amount) > 0 || expenses.length > 0) && (
        <Card sx={{ mb: 2 }}><CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Box>
              <Typography variant="body2" color="text.secondary">Müşteriye toplam</Typography>
              <Typography variant="h5" fontWeight={700} color="primary.main">
                {money(feeTry + billableTotal)}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                servis {money(feeTry)} + yansıtılan masraf {money(billableTotal)}
              </Typography>
            </Box>
            {id && expenses.length > 0 && (
              <Button variant="outlined" startIcon={<ReceiptLongIcon />}
                onClick={() => window.open(
                  `${api.defaults.baseURL}/service/reports/${id}/expense-pdf/`, "_blank"
                )}>
                Masraf Dökümü ve Fişler (PDF)
              </Button>
            )}
          </Stack>
        </CardContent></Card>
      )}

      <Card sx={{ mb: 2 }}><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Teslim</Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <TextField label="Müşteri yetkilisi" value={form.customer_rep} onChange={set("customer_rep")} fullWidth />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField type="date" label="Sonraki bakım tarihi" value={form.next_maintenance}
              onChange={set("next_maintenance")} InputLabelProps={{ shrink: true }} fullWidth />
          </Grid>
        </Grid>
      </CardContent></Card>

      <Card sx={{ mb: 2 }}><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Fotoğraflar</Typography>
        {!id ? (
          <Alert severity="info">Fotoğraf eklemek için önce raporu kaydedin.</Alert>
        ) : (
          <>
            <Button component="label" variant="outlined" startIcon={<PhotoCameraIcon />} sx={{ mb: 2 }}>
              Fotoğraf Ekle
              <input hidden type="file" accept="image/*" onChange={uploadPhoto} />
            </Button>
            <Grid container spacing={2}>
              {photos.map((photo) => (
                <Grid item xs={12} sm={6} md={4} key={photo.id}>
                  <Box component="img" src={photo.image} alt={photo.caption}
                    sx={{ width: "100%", height: 160, objectFit: "cover", borderRadius: 1 }} />
                  <Stack direction="row" spacing={1} mt={1}>
                    <TextField select size="small" value={photo.tag} sx={{ minWidth: 120 }}
                      onChange={(e) => updatePhotoMeta(photo, "tag", e.target.value)}>
                      {PHOTO_TAGS.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
                    </TextField>
                    <IconButton size="small" onClick={() => deletePhoto(photo.id)}><DeleteIcon fontSize="small" /></IconButton>
                  </Stack>
                </Grid>
              ))}
            </Grid>
          </>
        )}
      </CardContent></Card>

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
                  value={expenseForm.date}
                  onChange={(e) => setExpenseForm({ ...expenseForm, date: e.target.value })} />
              </Grid>
              <Grid item xs={6} md={4}>
                <TextField label="Tutar *" value={expenseForm.amount} fullWidth
                  onChange={(e) => setExpenseForm({ ...expenseForm, amount: e.target.value })} />
              </Grid>
              <Grid item xs={6} md={4}>
                <TextField select label="Para birimi" value={expenseForm.currency} fullWidth
                  onChange={(e) => {
                    const currency = e.target.value;
                    setExpenseForm((prev) => ({ ...prev, currency, rate: currency === "TRY" ? "1" : "" }));
                    ensureRate(currency, (rate) => setExpenseForm((prev) => (prev ? { ...prev, rate } : prev)));
                  }}>
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
                    onChange={(e) => setExpenseForm({ ...expenseForm, quantity: e.target.value })} />
                </Grid>
              )}
              <Grid item xs={12}>
                <TextField label="Açıklama" value={expenseForm.description || ""} fullWidth
                  onChange={(e) => setExpenseForm({ ...expenseForm, description: e.target.value })} />
              </Grid>
              <Grid item xs={12}>
                <FormControlLabel
                  control={<Checkbox checked={!!expenseForm.billable}
                    onChange={(e) => setExpenseForm({ ...expenseForm, billable: e.target.checked })} />}
                  label="Müşteriye yansıtılacak (servis bedeline eklenir)"
                />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setExpenseForm(null)}>Vazgeç</Button>
            <Button variant="contained" onClick={saveExpense}
              disabled={!expenseForm.amount || rateBusy ||
                (expenseForm.currency !== "TRY" && !(Number(expenseForm.rate) > 0))}>
              Kaydet
            </Button>
          </DialogActions>
        </Dialog>
      )}

      <Divider sx={{ my: 2 }} />
      <Stack direction="row" spacing={2} justifyContent="flex-end">
        <Button onClick={() => navigate("/service/reports")}>Vazgeç</Button>
        <Button variant="contained" startIcon={<SaveIcon />} onClick={save}
          disabled={saving || !form.customer || rateBusy ||
            (!!charge.amount && charge.currency !== "TRY" && !(Number(charge.rate) > 0))}>
          Raporu Kaydet
        </Button>
      </Stack>
    </Box>
  );
}
