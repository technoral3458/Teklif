import { useState, useEffect } from "react";
import {
  Box, Typography, Table, TableHead, TableRow, TableCell, TableBody,
  Card, Button, Chip, IconButton, Tooltip
} from "@mui/material";
import DownloadIcon from "@mui/icons-material/Download";
import api from "../../api/client";

export default function NestingJobs() {
  const [jobs, setJobs] = useState([]);

  useEffect(() => { api.get("/cnc/jobs/").then((r) => setJobs(r.data)); }, []);

  const download = async (job) => {
    const res = await api.get(`/cnc/jobs/${job.id}/gcode/`, { responseType: "blob" });
    const url = URL.createObjectURL(res.data);
    const a = document.createElement("a");
    a.href = url; a.download = `NC_${job.name}_${job.id}.nc`; a.click();
  };

  return (
    <Box>
      <Typography variant="h5" fontWeight={700} mb={3}>Nesting İşleri</Typography>
      <Card>
        <Table>
          <TableHead>
            <TableRow sx={{ bgcolor: "grey.100" }}>
              <TableCell>İş Adı</TableCell>
              <TableCell>Plaka</TableCell>
              <TableCell>Parça Sayısı</TableCell>
              <TableCell>Verimlilik</TableCell>
              <TableCell>Tarih</TableCell>
              <TableCell>NC Kod</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {jobs.length === 0 && <TableRow><TableCell colSpan={6} align="center">Henüz nesting işi yok.</TableCell></TableRow>}
            {jobs.map((j) => (
              <TableRow key={j.id} hover>
                <TableCell><strong>{j.name}</strong></TableCell>
                <TableCell>{j.plate_width}×{j.plate_height}mm</TableCell>
                <TableCell>{j.placed_items?.length || 0} parça</TableCell>
                <TableCell>
                  <Chip
                    label={`%${j.efficiency}`}
                    color={j.efficiency >= 70 ? "success" : j.efficiency >= 50 ? "warning" : "error"}
                    size="small"
                  />
                </TableCell>
                <TableCell>{new Date(j.created_at).toLocaleDateString("tr-TR")}</TableCell>
                <TableCell>
                  <Tooltip title="NC Kodu İndir">
                    <IconButton size="small" color="primary" onClick={() => download(j)}><DownloadIcon /></IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Card>
    </Box>
  );
}
