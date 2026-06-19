import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import Layout from "./components/Layout";
import Login from "./pages/Login";
import DealerOrders from "./pages/dealer/DealerOrders";
import NewOrder from "./pages/dealer/NewOrder";
import SalesOrders from "./pages/sales/SalesOrders";
import CNCQueue from "./pages/cnc/CNCQueue";
import NestingJobs from "./pages/cnc/NestingJobs";
import NewNestingJob from "./pages/cnc/NewNestingJob";
import DrillPanels from "./pages/cnc/DrillPanels";
import DrillPanelDetail from "./pages/cnc/DrillPanelDetail";
import AdminUsers from "./pages/admin/AdminUsers";
import AdminCatalog from "./pages/admin/AdminCatalog";

function ProtectedRoute({ children }) {
  const { user, loading } = useAuth();
  if (loading) return <div>Yükleniyor...</div>;
  if (!user) return <Navigate to="/login" />;
  return <Layout>{children}</Layout>;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/dealer/orders" element={<ProtectedRoute><DealerOrders /></ProtectedRoute>} />
          <Route path="/dealer/new-order" element={<ProtectedRoute><NewOrder /></ProtectedRoute>} />
          <Route path="/sales/orders" element={<ProtectedRoute><SalesOrders /></ProtectedRoute>} />
          <Route path="/sales/all-orders" element={<ProtectedRoute><SalesOrders /></ProtectedRoute>} />
          <Route path="/cnc/queue" element={<ProtectedRoute><CNCQueue /></ProtectedRoute>} />
          <Route path="/cnc/jobs" element={<ProtectedRoute><NestingJobs /></ProtectedRoute>} />
          <Route path="/cnc/new-job" element={<ProtectedRoute><NewNestingJob /></ProtectedRoute>} />
          <Route path="/cnc/drill-panels" element={<ProtectedRoute><DrillPanels /></ProtectedRoute>} />
          <Route path="/cnc/drill-panels/:id" element={<ProtectedRoute><DrillPanelDetail /></ProtectedRoute>} />
          <Route path="/admin/users" element={<ProtectedRoute><AdminUsers /></ProtectedRoute>} />
          <Route path="/admin/catalog" element={<ProtectedRoute><AdminCatalog /></ProtectedRoute>} />
          <Route path="*" element={<Navigate to="/login" />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
