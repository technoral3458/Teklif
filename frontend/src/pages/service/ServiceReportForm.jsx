import { useState, useEffect } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Box, Typography, Card, CardContent, TextField, MenuItem, Button, Grid, Stack,
  Chip, IconButton, Alert, Divider, Table, TableBody, TableRow, TableCell,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import SaveIcon from "@mui/icons-material/Save";
import PhotoCameraIcon from "@mui/icons-material/PhotoCamera";
import api from "../../api/client";

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

  const loadMachines = (customerId) => {
    if (!customerId) { setMachines([]); return; }
    api.get(`/service/machines/?customer=${customerId}`).then((r) => setMachines(r.data));
  };

  useEffect(() => {
    api.get("/service/customers/").then((r) => setCustomers(r.data));
  }, []);

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
    });
  }, [id]);

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

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

      <Divider sx={{ my: 2 }} />
      <Stack direction="row" spacing={2} justifyContent="flex-end">
        <Button onClick={() => navigate("/service/reports")}>Vazgeç</Button>
        <Button variant="contained" startIcon={<SaveIcon />} onClick={save}
          disabled={saving || !form.customer}>
          Raporu Kaydet
        </Button>
      </Stack>
    </Box>
  );
}
