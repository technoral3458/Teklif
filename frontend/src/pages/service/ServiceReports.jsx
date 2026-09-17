import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody, Chip, Button,
  Card, CardContent, Dialog, DialogTitle, DialogContent, DialogActions, TextField,
  Alert, MenuItem, IconButton, Grid, Divider, Stack, Tooltip, CircularProgress,
  FormControlLabel, Checkbox,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import MailIcon from "@mui/icons-material/Mail";
import PictureAsPdfIcon from "@mui/icons-material/PictureAsPdf";
import VisibilityIcon from "@mui/icons-material/Visibility";
import EditIcon from "@mui/icons-material/Edit";
import api from "../../api/client";

const STATUS_COLOR = {
  TASLAK: "default", ACIK: "info", COZULDU: "success",
  GECICI_COZUM: "warning", PARCA_BEKLIYOR: "warning", TEKRAR_ZIYARET: "error",
};

const STATUSES = [
  ["", "Tümü"], ["ACIK", "Açık"], ["COZULDU", "Çözüldü"],
  ["PARCA_BEKLIYOR", "Parça Bekliyor"], ["TEKRAR_ZIYARET", "Tekrar Ziyaret"],
  ["GECICI_COZUM", "Geçici Çözüm"], ["TASLAK", "Taslak"],
];

const fmtDate = (value) => (value ? new Date(value).toLocaleDateString("tr-TR") : "-");

export default function ServiceReports() {
  const [reports, setReports] = useState([]);
  const [stats, setStats] = useState(null);
  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState(null);
  const [mailTarget, setMailTarget] = useState(null);
  const navigate = useNavigate();

  const load = () => {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (search) params.set("search", search);
    api.get(`/service/reports/?${params}`).then((r) => setReports(r.data));
  };

  useEffect(() => { load(); }, [status]);
  useEffect(() => { api.get("/service/stats/").then((r) => setStats(r.data)).catch(() => {}); }, []);

  const openPdf = (report) => {
    window.open(`${api.defaults.baseURL}/service/reports/${report.id}/pdf/`, "_blank");
  };

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" mb={2}>
        <Typography variant="h5" fontWeight={700}>Servis Raporları</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => navigate("/service/new-report")}>
          Yeni Servis Raporu
        </Button>
      </Stack>

      {stats && (
        <Grid container spacing={2} mb={2}>
          {[
            ["Bu ay", stats.monthly_count, "servis"],
            ["Açık kayıt", stats.open_count, "rapor"],
            ["Parça bekleyen", stats.waiting_parts, "rapor"],
            ["Makine", stats.machine_count, "kayıtlı"],
          ].map(([label, value, unit]) => (
            <Grid item xs={6} md={3} key={label}>
              <Card><CardContent>
                <Typography variant="body2" color="text.secondary">{label}</Typography>
                <Typography variant="h4" fontWeight={700}>{value}</Typography>
                <Typography variant="caption" color="text.secondary">{unit}</Typography>
              </CardContent></Card>
            </Grid>
          ))}
        </Grid>
      )}

      {stats?.upcoming_maintenance?.length > 0 && (
        <Alert severity="info" sx={{ mb: 2 }}>
          <b>Yaklaşan bakımlar:</b>{" "}
          {stats.upcoming_maintenance.slice(0, 4).map((m) => (
            `${m.machine || m.customer} (${fmtDate(m.date)})`
          )).join(" • ")}
        </Alert>
      )}

      <Card sx={{ mb: 2 }}><CardContent>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
          <TextField
            select size="small" label="Durum" value={status}
            onChange={(e) => setStatus(e.target.value)} sx={{ minWidth: 200 }}
          >
            {STATUSES.map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
          </TextField>
          <TextField
            size="small" label="Ara" placeholder="Rapor no, müşteri, seri no, arıza…"
            value={search} onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && load()} sx={{ flexGrow: 1 }}
          />
          <Button variant="outlined" onClick={load}>Filtrele</Button>
        </Stack>
      </CardContent></Card>

      <Card><CardContent>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Rapor No</TableCell>
              <TableCell>Tarih</TableCell>
              <TableCell>Müşteri</TableCell>
              <TableCell>Makine</TableCell>
              <TableCell>Tip</TableCell>
              <TableCell>Durum</TableCell>
              <TableCell>Teknisyen</TableCell>
              <TableCell align="right">İşlem</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {reports.map((report) => (
              <TableRow key={report.id} hover>
                <TableCell><b>{report.report_no}</b></TableCell>
                <TableCell>{fmtDate(report.service_date)}</TableCell>
                <TableCell>{report.customer_name}</TableCell>
                <TableCell>{report.machine_name || "-"}</TableCell>
                <TableCell>{report.type_label}</TableCell>
                <TableCell>
                  <Chip size="small" label={report.status_label} color={STATUS_COLOR[report.status]} />
                </TableCell>
                <TableCell>{report.technician_name || "-"}</TableCell>
                <TableCell align="right">
                  <Tooltip title="Görüntüle">
                    <IconButton size="small" onClick={() => setSelected(report)}><VisibilityIcon fontSize="small" /></IconButton>
                  </Tooltip>
                  <Tooltip title="Düzenle">
                    <IconButton size="small" onClick={() => navigate(`/service/report/${report.id}`)}><EditIcon fontSize="small" /></IconButton>
                  </Tooltip>
                  <Tooltip title="PDF">
                    <IconButton size="small" onClick={() => openPdf(report)}><PictureAsPdfIcon fontSize="small" /></IconButton>
                  </Tooltip>
                  <Tooltip title={report.mailed_at ? `Gönderildi: ${fmtDate(report.mailed_at)}` : "Mail gönder"}>
                    <IconButton size="small" color={report.mailed_at ? "success" : "default"} onClick={() => setMailTarget(report)}>
                      <MailIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
            {reports.length === 0 && (
              <TableRow><TableCell colSpan={8} align="center" sx={{ py: 4, color: "text.secondary" }}>
                Kayıt bulunamadı.
              </TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent></Card>

      <ReportDetailDialog report={selected} onClose={() => setSelected(null)} onPdf={openPdf} />
      <MailDialog key={mailTarget?.id} report={mailTarget} onClose={() => setMailTarget(null)} onSent={load} />
    </Box>
  );
}

function ReportDetailDialog({ report, onClose, onPdf }) {
  if (!report) return null;
  const blocks = [
    ["Arıza / Talep Tanımı", report.fault_description],
    ["Arıza Nedeni", report.fault_cause],
    ["Yapılan İşlem / Çözüm", report.work_done],
    ["Öneriler", report.recommendations],
  ].filter(([, text]) => text);

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {report.report_no}
        <Chip size="small" sx={{ ml: 1 }} label={report.status_label} color={STATUS_COLOR[report.status]} />
      </DialogTitle>
      <DialogContent dividers>
        <Grid container spacing={2}>
          <Grid item xs={12} md={6}>
            <Typography variant="subtitle2" color="primary">MÜŞTERİ</Typography>
            <Typography>{report.customer_name}</Typography>
            <Typography variant="body2" color="text.secondary">{report.customer_email}</Typography>
          </Grid>
          <Grid item xs={12} md={6}>
            <Typography variant="subtitle2" color="primary">MAKİNE</Typography>
            <Typography>{report.machine_name || "-"}</Typography>
          </Grid>
        </Grid>
        <Divider sx={{ my: 2 }} />
        <Grid container spacing={2}>
          {[
            ["Servis Tarihi", fmtDate(report.service_date)],
            ["Servis Tipi", report.type_label],
            ["Öncelik", report.priority_label],
            ["Çalışma Süresi", report.duration_minutes ? `${Math.floor(report.duration_minutes / 60)} sa ${report.duration_minutes % 60} dk` : "-"],
            ["Yol", `${report.travel_km} km`],
            ["Teknisyen", report.technician_name || "-"],
            ["Sonraki Bakım", fmtDate(report.next_maintenance)],
            ["Müşteri Yetkilisi", report.customer_rep || "-"],
          ].map(([label, value]) => (
            <Grid item xs={6} md={3} key={label}>
              <Typography variant="caption" color="text.secondary">{label}</Typography>
              <Typography fontWeight={600}>{value}</Typography>
            </Grid>
          ))}
        </Grid>

        {report.departments?.length > 0 && (
          <>
            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" color="primary" gutterBottom>ÇALIŞILAN BÖLÜMLER</Typography>
            {report.departments.map((d) => (
              <Box key={d.id} mb={1}>
                <Chip size="small" label={d.department_label} color="primary" sx={{ mr: 1 }} />
                <Typography variant="body2" component="span">{d.work}</Typography>
              </Box>
            ))}
          </>
        )}

        {blocks.map(([title, text]) => (
          <Box key={title} mt={2}>
            <Typography variant="subtitle2" color="primary">{title.toUpperCase()}</Typography>
            <Typography variant="body2" sx={{ whiteSpace: "pre-wrap" }}>{text}</Typography>
          </Box>
        ))}

        {report.parts?.length > 0 && (
          <>
            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" color="primary" gutterBottom>YEDEK PARÇALAR</Typography>
            <Table size="small">
              <TableBody>
                {report.parts.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell><b>{p.name}</b></TableCell>
                    <TableCell>{p.code || "-"}</TableCell>
                    <TableCell>{Number(p.quantity)} {p.unit}</TableCell>
                    <TableCell>{p.status_label}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </>
        )}

        {report.photos?.length > 0 && (
          <>
            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" color="primary" gutterBottom>FOTOĞRAFLAR</Typography>
            <Grid container spacing={1}>
              {report.photos.map((photo) => (
                <Grid item xs={6} md={4} key={photo.id}>
                  <a href={photo.image} target="_blank" rel="noreferrer">
                    <Box component="img" src={photo.image} alt={photo.caption}
                      sx={{ width: "100%", height: 150, objectFit: "cover", borderRadius: 1 }} />
                  </a>
                  <Typography variant="caption" color="text.secondary">
                    {photo.tag_label}{photo.caption ? ` — ${photo.caption}` : ""}
                  </Typography>
                </Grid>
              ))}
            </Grid>
          </>
        )}

        {report.signature && (
          <>
            <Divider sx={{ my: 2 }} />
            <Typography variant="subtitle2" color="primary" gutterBottom>İMZA</Typography>
            <Box component="img" src={report.signature} alt="İmza"
              sx={{ maxHeight: 120, bgcolor: "#fff", borderRadius: 1, p: 1 }} />
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button startIcon={<PictureAsPdfIcon />} onClick={() => onPdf(report)}>PDF</Button>
        <Button onClick={onClose}>Kapat</Button>
      </DialogActions>
    </Dialog>
  );
}

function MailDialog({ report, onClose, onSent }) {
  const [to, setTo] = useState(report?.customer_email || "");
  const [cc, setCc] = useState("");
  const [note, setNote] = useState("");
  const [attachPhotos, setAttachPhotos] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (!report) return null;

  const send = async () => {
    setError(""); setBusy(true);
    try {
      await api.post(`/service/reports/${report.id}/send-mail/`, {
        to, cc, note, attach_photos: attachPhotos,
      });
      onSent();
      onClose();
    } catch (err) {
      setError(err.response?.data?.error || "Mail gönderilemedi.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Raporu Mail Gönder — {report.report_no}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} mt={1}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="Alıcı(lar)" value={to} onChange={(e) => setTo(e.target.value)}
            helperText="Birden çok adresi virgülle ayırın" fullWidth />
          <TextField label="Bilgi (CC)" value={cc} onChange={(e) => setCc(e.target.value)} fullWidth />
          <TextField label="Mail notu" value={note} onChange={(e) => setNote(e.target.value)}
            multiline rows={3} fullWidth />
          <FormControlLabel
            control={<Checkbox checked={attachPhotos} onChange={(e) => setAttachPhotos(e.target.checked)} />}
            label={`Fotoğrafları da ekle (${report.photos?.length || 0})`}
          />
          <Alert severity="info">Rapor PDF olarak da eklenir. Gönderen bilgileri Ayarlar &gt; Mail Ayarları'ndan alınır.</Alert>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Vazgeç</Button>
        <Button variant="contained" onClick={send} disabled={busy || !to}
          startIcon={busy ? <CircularProgress size={16} /> : <MailIcon />}>
          Gönder
        </Button>
      </DialogActions>
    </Dialog>
  );
}
