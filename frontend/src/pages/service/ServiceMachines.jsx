import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, Card, CardContent, Table, TableHead, TableRow, TableCell,
  TableBody, Button, Dialog, DialogTitle, DialogContent, DialogActions, TextField,
  Stack, IconButton, Tabs, Tab, Chip, MenuItem, Grid, Alert,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import BuildIcon from "@mui/icons-material/Build";
import api from "../../api/client";

const emptyCustomer = { name: "", contact_name: "", phone: "", email: "", address: "", city: "", notes: "" };
const emptyMachine = {
  customer: "", name: "", brand: "", model: "", serial_no: "",
  year: "", location: "", install_date: "", warranty_end: "", notes: "",
};

export default function ServiceMachines() {
  const [tab, setTab] = useState("machines");
  const [customers, setCustomers] = useState([]);
  const [machines, setMachines] = useState([]);
  const [customerForm, setCustomerForm] = useState(null);
  const [machineForm, setMachineForm] = useState(null);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const load = () => {
    api.get("/service/customers/").then((r) => setCustomers(r.data));
    api.get("/service/machines/").then((r) => setMachines(r.data));
  };

  useEffect(() => { load(); }, []);

  const saveCustomer = async () => {
    setError("");
    try {
      if (customerForm.id) await api.put(`/service/customers/${customerForm.id}/`, customerForm);
      else await api.post("/service/customers/", customerForm);
      setCustomerForm(null);
      load();
    } catch { setError("Müşteri kaydedilemedi. Firma adı zorunludur."); }
  };

  const saveMachine = async () => {
    setError("");
    const payload = {
      ...machineForm,
      install_date: machineForm.install_date || null,
      warranty_end: machineForm.warranty_end || null,
    };
    try {
      if (machineForm.id) await api.put(`/service/machines/${machineForm.id}/`, payload);
      else await api.post("/service/machines/", payload);
      setMachineForm(null);
      load();
    } catch { setError("Makine kaydedilemedi. Müşteri ve makine adı zorunludur."); }
  };

  const removeCustomer = async (id) => {
    if (!window.confirm("Müşteri silinsin mi?")) return;
    try { await api.delete(`/service/customers/${id}/`); load(); }
    catch { setError("Servis raporu bulunan müşteri silinemez."); }
  };

  const removeMachine = async (id) => {
    if (!window.confirm("Makine silinsin mi?")) return;
    await api.delete(`/service/machines/${id}/`);
    load();
  };

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" mb={2}>
        <Typography variant="h5" fontWeight={700}>Makine ve Müşteri Kayıtları</Typography>
        <Button variant="contained" startIcon={<AddIcon />}
          onClick={() => (tab === "machines" ? setMachineForm(emptyMachine) : setCustomerForm(emptyCustomer))}>
          {tab === "machines" ? "Makine Ekle" : "Müşteri Ekle"}
        </Button>
      </Stack>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError("")}>{error}</Alert>}

      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 2 }}>
        <Tab value="machines" label={`Makineler (${machines.length})`} />
        <Tab value="customers" label={`Müşteriler (${customers.length})`} />
      </Tabs>

      <Card><CardContent>
        {tab === "machines" ? (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Makine</TableCell>
                <TableCell>Müşteri</TableCell>
                <TableCell>Seri No</TableCell>
                <TableCell>Konum</TableCell>
                <TableCell>Garanti</TableCell>
                <TableCell align="center">Servis</TableCell>
                <TableCell align="right">İşlem</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {machines.map((m) => (
                <TableRow key={m.id} hover>
                  <TableCell><b>{m.display_name}</b><br />
                    <Typography variant="caption" color="text.secondary">{m.model}</Typography>
                  </TableCell>
                  <TableCell>{m.customer_name}</TableCell>
                  <TableCell>{m.serial_no || "-"}</TableCell>
                  <TableCell>{m.location || "-"}</TableCell>
                  <TableCell>{m.warranty_end ? new Date(m.warranty_end).toLocaleDateString("tr-TR") : "-"}</TableCell>
                  <TableCell align="center"><Chip size="small" label={m.report_count} /></TableCell>
                  <TableCell align="right">
                    <IconButton size="small" title="Servis aç"
                      onClick={() => navigate(`/service/new-report?machine=${m.id}`)}><BuildIcon fontSize="small" /></IconButton>
                    <IconButton size="small" onClick={() => setMachineForm({
                      ...m, install_date: m.install_date || "", warranty_end: m.warranty_end || "",
                    })}><EditIcon fontSize="small" /></IconButton>
                    <IconButton size="small" onClick={() => removeMachine(m.id)}><DeleteIcon fontSize="small" /></IconButton>
                  </TableCell>
                </TableRow>
              ))}
              {machines.length === 0 && (
                <TableRow><TableCell colSpan={7} align="center" sx={{ py: 4, color: "text.secondary" }}>
                  Henüz makine kaydı yok.
                </TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Firma</TableCell>
                <TableCell>Yetkili</TableCell>
                <TableCell>Telefon</TableCell>
                <TableCell>E-posta</TableCell>
                <TableCell>Şehir</TableCell>
                <TableCell align="center">Makine</TableCell>
                <TableCell align="right">İşlem</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {customers.map((c) => (
                <TableRow key={c.id} hover>
                  <TableCell><b>{c.name}</b></TableCell>
                  <TableCell>{c.contact_name || "-"}</TableCell>
                  <TableCell>{c.phone || "-"}</TableCell>
                  <TableCell>{c.email || "-"}</TableCell>
                  <TableCell>{c.city || "-"}</TableCell>
                  <TableCell align="center"><Chip size="small" label={c.machine_count} /></TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={() => setCustomerForm(c)}><EditIcon fontSize="small" /></IconButton>
                    <IconButton size="small" onClick={() => removeCustomer(c.id)}><DeleteIcon fontSize="small" /></IconButton>
                  </TableCell>
                </TableRow>
              ))}
              {customers.length === 0 && (
                <TableRow><TableCell colSpan={7} align="center" sx={{ py: 4, color: "text.secondary" }}>
                  Henüz müşteri kaydı yok.
                </TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        )}
      </CardContent></Card>

      {customerForm && (
        <Dialog open onClose={() => setCustomerForm(null)} maxWidth="sm" fullWidth>
          <DialogTitle>{customerForm.id ? "Müşteriyi Düzenle" : "Yeni Müşteri"}</DialogTitle>
          <DialogContent dividers>
            <Stack spacing={2} mt={1}>
              {[["name", "Firma adı *"], ["contact_name", "Yetkili kişi"], ["phone", "Telefon"],
                ["email", "E-posta"], ["city", "Şehir"]].map(([field, label]) => (
                <TextField key={field} label={label} value={customerForm[field] || ""} fullWidth
                  onChange={(e) => setCustomerForm({ ...customerForm, [field]: e.target.value })} />
              ))}
              <TextField label="Adres" value={customerForm.address || ""} multiline rows={2} fullWidth
                onChange={(e) => setCustomerForm({ ...customerForm, address: e.target.value })} />
              <TextField label="Not" value={customerForm.notes || ""} multiline rows={2} fullWidth
                onChange={(e) => setCustomerForm({ ...customerForm, notes: e.target.value })} />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setCustomerForm(null)}>Vazgeç</Button>
            <Button variant="contained" onClick={saveCustomer} disabled={!customerForm.name}>Kaydet</Button>
          </DialogActions>
        </Dialog>
      )}

      {machineForm && (
        <Dialog open onClose={() => setMachineForm(null)} maxWidth="sm" fullWidth>
          <DialogTitle>{machineForm.id ? "Makineyi Düzenle" : "Yeni Makine"}</DialogTitle>
          <DialogContent dividers>
            <Grid container spacing={2} mt={0}>
              <Grid item xs={12}>
                <TextField select label="Müşteri *" value={machineForm.customer || ""} fullWidth
                  onChange={(e) => setMachineForm({ ...machineForm, customer: e.target.value })}>
                  {customers.map((c) => <MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
                </TextField>
              </Grid>
              {[["name", "Makine adı / tipi *", 12], ["brand", "Marka", 6], ["model", "Model", 6],
                ["serial_no", "Seri no", 6], ["year", "Üretim yılı", 6], ["location", "Konum / hat", 12]]
                .map(([field, label, size]) => (
                  <Grid item xs={12} md={size} key={field}>
                    <TextField label={label} value={machineForm[field] || ""} fullWidth
                      onChange={(e) => setMachineForm({ ...machineForm, [field]: e.target.value })} />
                  </Grid>
                ))}
              <Grid item xs={6}>
                <TextField type="date" label="Kurulum tarihi" InputLabelProps={{ shrink: true }} fullWidth
                  value={machineForm.install_date || ""}
                  onChange={(e) => setMachineForm({ ...machineForm, install_date: e.target.value })} />
              </Grid>
              <Grid item xs={6}>
                <TextField type="date" label="Garanti bitişi" InputLabelProps={{ shrink: true }} fullWidth
                  value={machineForm.warranty_end || ""}
                  onChange={(e) => setMachineForm({ ...machineForm, warranty_end: e.target.value })} />
              </Grid>
              <Grid item xs={12}>
                <TextField label="Not" value={machineForm.notes || ""} multiline rows={2} fullWidth
                  onChange={(e) => setMachineForm({ ...machineForm, notes: e.target.value })} />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setMachineForm(null)}>Vazgeç</Button>
            <Button variant="contained" onClick={saveMachine}
              disabled={!machineForm.name || !machineForm.customer}>Kaydet</Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  );
}
