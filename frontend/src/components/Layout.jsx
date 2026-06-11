import { useState } from "react";
import { useNavigate, useLocation, Link } from "react-router-dom";
import {
  AppBar, Toolbar, Typography, Box, Drawer, List, ListItem,
  ListItemIcon, ListItemText, Divider, IconButton, Avatar, Chip
} from "@mui/material";
import MenuIcon from "@mui/icons-material/Menu";
import LogoutIcon from "@mui/icons-material/Logout";
import { useAuth } from "../context/AuthContext";

const ROLE_LABELS = { admin: "Yönetici", sales: "Satış", dealer: "Bayi", cnc: "CNC Op." };
const ROLE_COLORS = { admin: "error", sales: "primary", dealer: "success", cnc: "warning" };

const MENUS = {
  dealer: [
    { label: "Siparişlerim", path: "/dealer/orders", icon: "📋" },
    { label: "Yeni Sipariş", path: "/dealer/new-order", icon: "➕" },
  ],
  sales: [
    { label: "Onay Bekleyenler", path: "/sales/orders", icon: "⏳" },
    { label: "Tüm Siparişler", path: "/sales/all-orders", icon: "📋" },
  ],
  cnc: [
    { label: "CNC Kuyruğu", path: "/cnc/queue", icon: "🔧" },
    { label: "Nesting İşleri", path: "/cnc/jobs", icon: "📐" },
    { label: "Yeni Nesting", path: "/cnc/new-job", icon: "➕" },
  ],
  admin: [
    { label: "Kullanıcılar", path: "/admin/users", icon: "👥" },
    { label: "Markalar & Modeller", path: "/admin/catalog", icon: "🏷️" },
    { label: "Renkler", path: "/admin/colors", icon: "🎨" },
    { label: "Tüm Siparişler", path: "/sales/all-orders", icon: "📋" },
    { label: "CNC Kuyruğu", path: "/cnc/queue", icon: "🔧" },
  ],
};

export default function Layout({ children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [open, setOpen] = useState(true);
  const menuItems = MENUS[user?.role] || [];

  const handleLogout = () => { logout(); navigate("/login"); };

  return (
    <Box sx={{ display: "flex", minHeight: "100vh" }}>
      <AppBar position="fixed" sx={{ zIndex: 1201, bgcolor: "#1a237e" }}>
        <Toolbar>
          <IconButton color="inherit" onClick={() => setOpen(!open)} sx={{ mr: 2 }}>
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" fontWeight={700} sx={{ flexGrow: 1 }}>MemKap CNC</Typography>
          <Chip label={ROLE_LABELS[user?.role]} color={ROLE_COLORS[user?.role]} size="small" sx={{ mr: 2, color: "white" }} />
          <Typography variant="body2" sx={{ mr: 2 }}>{user?.company}</Typography>
          <IconButton color="inherit" onClick={handleLogout}><LogoutIcon /></IconButton>
        </Toolbar>
      </AppBar>
      <Drawer variant="permanent" open={open} sx={{
        width: open ? 240 : 60,
        "& .MuiDrawer-paper": { width: open ? 240 : 60, mt: 8, overflow: "hidden", transition: "width 0.2s" }
      }}>
        <List>
          {menuItems.map((item) => (
            <ListItem
              key={item.path}
              component={Link}
              to={item.path}
              selected={location.pathname === item.path}
              sx={{ color: "inherit", textDecoration: "none", borderRadius: 1, mx: 0.5,
                "&.Mui-selected": { bgcolor: "primary.light", color: "primary.contrastText" } }}
            >
              <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
              {open && <ListItemText primary={item.label} />}
            </ListItem>
          ))}
        </List>
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: 3, mt: 8, ml: open ? "240px" : "60px", transition: "margin 0.2s" }}>
        {children}
      </Box>
    </Box>
  );
}
