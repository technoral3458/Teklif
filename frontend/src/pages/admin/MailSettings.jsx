import { useState, useEffect } from "react";
import {
  Box, Typography, Card, CardContent, TextField, MenuItem, Button, Grid, Stack,
  Alert, Divider, Chip, FormControlLabel, Checkbox, CircularProgress,
  Dialog, DialogTitle, DialogContent, DialogActions,
} from "@mui/material";
import SaveIcon from "@mui/icons-material/Save";
import SendIcon from "@mui/icons-material/Send";
import api from "../../api/client";

const SECURITY = [
  ["STARTTLS", "STARTTLS (genelde 587)"],
  ["SSL", "SSL/TLS (genelde 465)"],
  ["NONE", "Şifreleme yok (25)"],
];

const PRESETS = [
  { name: "Gmail", host: "smtp.gmail.com", port: 587, security: "STARTTLS",
    hint: "Google hesabınızda 2 adımlı doğrulamayı açıp \"uygulama parolası\" üretmeniz gerekir." },
  { name: "Outlook", host: "smtp-mail.outlook.com", port: 587, security: "STARTTLS",
    hint: "Microsoft hesabı için uygulama parolası gerekebilir." },
  { name: "Yandex", host: "smtp.yandex.com.tr", port: 465, security: "SSL",
    hint: "Yandex'te \"uygulama parolaları\" bölümünden parola üretin." },
];

export default function MailSettings() {
  const [form, setForm] = useState(null);
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState(null);
  const [hint, setHint] = useState("");
  const [testOpen, setTestOpen] = useState(false);
  const [testTo, setTestTo] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.get("/service/mail-settings/").then((r) => {
      setForm(r.data);
      setTestTo(r.data.default_to || r.data.from_address || "");
    });
  }, []);

  if (!form) return <CircularProgress />;

  const set = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const save = async () => {
    setBusy(true); setMessage(null);
    try {
      const payload = { ...form };
      if (password) payload.password = password; else delete payload.password;
      const res = await api.put("/service/mail-settings/", payload);
      setForm(res.data);
      setPassword("");
      setMessage({ type: "success", text: "Mail ayarları kaydedildi." });
    } catch (err) {
      setMessage({ type: "error", text: err.response?.data?.error || "Kaydedilemedi." });
    } finally { setBusy(false); }
  };

  const sendTest = async () => {
    setBusy(true); setMessage(null);
    try {
      const res = await api.post("/service/mail-settings/test/", { to: testTo });
      setMessage({ type: "success", text: res.data.detail });
      setTestOpen(false);
    } catch (err) {
      setMessage({ type: "error", text: err.response?.data?.error || "Test maili gönderilemedi." });
    } finally { setBusy(false); }
  };

  const applyPreset = (preset) => {
    setForm({ ...form, host: preset.host, port: preset.port, security: preset.security });
    setHint(preset.hint);
  };

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} mb={2}>Mail Ayarları</Typography>
      <Typography variant="body2" color="text.secondary" mb={2}>
        Servis raporları buradaki SMTP hesabı üzerinden gönderilir. Aynı ayarları mobil uygulamada
        <b> Ayarlar &gt; Mail Ayarları</b> bölümünden de girebilirsiniz.
      </Typography>

      {message && <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>{message.text}</Alert>}

      <Card sx={{ mb: 2 }}><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Hazır ayarlar</Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          {PRESETS.map((preset) => (
            <Chip key={preset.name} label={preset.name} onClick={() => applyPreset(preset)} variant="outlined" />
          ))}
        </Stack>
        {hint && <Alert severity="info" sx={{ mt: 2 }}>{hint}</Alert>}
      </CardContent></Card>

      <Card sx={{ mb: 2 }}><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Sunucu</Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <TextField label="SMTP sunucusu" value={form.host} onChange={set("host")}
              placeholder="smtp.firmaniz.com" fullWidth />
          </Grid>
          <Grid item xs={6} md={3}>
            <TextField label="Port" value={form.port} onChange={set("port")} fullWidth />
          </Grid>
          <Grid item xs={6} md={3}>
            <TextField select label="Şifreleme" value={form.security} onChange={set("security")} fullWidth>
              {SECURITY.map(([v, l]) => <MenuItem key={v} value={v}>{l}</MenuItem>)}
            </TextField>
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField label="Kullanıcı adı" value={form.username} onChange={set("username")}
              helperText="Genelde e-posta adresinizin tamamı" fullWidth />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField type="password" label="Parola" value={password}
              onChange={(e) => setPassword(e.target.value)} fullWidth
              helperText={form.has_password ? "Kayıtlı parola var — değiştirmek için yeni parolayı yazın" : "Parola girilmedi"} />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField label="Gönderen adresi" value={form.from_address} onChange={set("from_address")}
              placeholder="servis@firmaniz.com" fullWidth />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField label="Gönderen adı" value={form.from_name} onChange={set("from_name")}
              placeholder="Firmanız Teknik Servis" fullWidth />
          </Grid>
        </Grid>
      </CardContent></Card>

      <Card sx={{ mb: 2 }}><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Varsayılanlar</Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <TextField label="Her rapora eklenecek alıcı" value={form.default_to} onChange={set("default_to")}
              helperText="Müşterinin adresine ek olarak gönderilir" fullWidth />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField label="Bilgi (CC)" value={form.default_cc} onChange={set("default_cc")} fullWidth />
          </Grid>
          <Grid item xs={12}>
            <TextField label="Konu şablonu" value={form.subject_template} onChange={set("subject_template")}
              helperText="{rapor_no} {musteri} {makine} {tarih} {durum} yer tutucularını kullanabilirsiniz" fullWidth />
          </Grid>
          <Grid item xs={12}>
            <FormControlLabel
              control={<Checkbox checked={form.attach_photos}
                onChange={(e) => setForm({ ...form, attach_photos: e.target.checked })} />}
              label="Fotoğrafları da varsayılan olarak ekle"
            />
          </Grid>
        </Grid>
      </CardContent></Card>

      <Card sx={{ mb: 2 }}><CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>Firma Künyesi</Typography>
        <Typography variant="body2" color="text.secondary" mb={2}>
          PDF raporun üst kısmında ve mail imzasında görünür.
        </Typography>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <TextField label="Firma adı" value={form.company_name} onChange={set("company_name")} fullWidth />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField label="Telefon" value={form.company_phone} onChange={set("company_phone")} fullWidth />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField label="E-posta" value={form.company_email} onChange={set("company_email")} fullWidth />
          </Grid>
          <Grid item xs={12} md={6}>
            <TextField label="Web sitesi" value={form.company_web} onChange={set("company_web")} fullWidth />
          </Grid>
          <Grid item xs={12}>
            <TextField label="Adres" value={form.company_address} onChange={set("company_address")}
              multiline rows={2} fullWidth />
          </Grid>
        </Grid>
      </CardContent></Card>

      <Divider sx={{ my: 2 }} />
      <Stack direction="row" spacing={2} justifyContent="flex-end">
        <Button startIcon={<SendIcon />} onClick={() => setTestOpen(true)} disabled={!form.is_configured || busy}>
          Test Maili Gönder
        </Button>
        <Button variant="contained" startIcon={<SaveIcon />} onClick={save} disabled={busy}>
          Kaydet
        </Button>
      </Stack>

      <Dialog open={testOpen} onClose={() => setTestOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Test maili</DialogTitle>
        <DialogContent>
          <TextField label="Alıcı" value={testTo} onChange={(e) => setTestTo(e.target.value)}
            fullWidth sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTestOpen(false)}>Vazgeç</Button>
          <Button variant="contained" onClick={sendTest} disabled={busy || !testTo}>Gönder</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
