from django.contrib.auth import get_user_model
from django.test import TestCase
from rest_framework.test import APIClient


class LoginApiTests(TestCase):
    """Giriş ucu access/refresh + user döndürmeli (frontend buna bağlı)."""

    def setUp(self):
        User = get_user_model()
        self.user = User.objects.create_user("admin", password="Ersan2026", role=User.ROLE_ADMIN)
        self.client = APIClient()

    def test_login_returns_user(self):
        resp = self.client.post("/api/auth/login/",
                                {"username": "admin", "password": "Ersan2026"}, format="json")
        self.assertEqual(resp.status_code, 200)
        self.assertIn("access", resp.data)
        self.assertIn("refresh", resp.data)
        self.assertIn("user", resp.data)              # ← kritik: frontend res.data.user bekliyor
        self.assertEqual(resp.data["user"]["username"], "admin")
        self.assertEqual(resp.data["user"]["role"], "admin")

    def test_login_wrong_password(self):
        resp = self.client.post("/api/auth/login/",
                                {"username": "admin", "password": "yanlis"}, format="json")
        self.assertEqual(resp.status_code, 401)
