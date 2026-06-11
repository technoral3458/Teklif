from django.contrib.auth.models import AbstractUser
from django.db import models


class User(AbstractUser):
    ROLE_ADMIN = "admin"
    ROLE_SALES = "sales"
    ROLE_DEALER = "dealer"
    ROLE_CNC = "cnc"

    ROLES = [
        (ROLE_ADMIN, "Yönetici"),
        (ROLE_SALES, "Satış Ekibi"),
        (ROLE_DEALER, "Bayi"),
        (ROLE_CNC, "CNC Operatörü"),
    ]

    role = models.CharField(max_length=20, choices=ROLES, default=ROLE_DEALER)
    phone = models.CharField(max_length=20, blank=True)
    company = models.CharField(max_length=200, blank=True)
    city = models.CharField(max_length=100, blank=True)

    def is_admin(self):
        return self.role == self.ROLE_ADMIN

    def is_sales(self):
        return self.role == self.ROLE_SALES

    def is_dealer(self):
        return self.role == self.ROLE_DEALER

    def is_cnc(self):
        return self.role == self.ROLE_CNC

    def can_approve_orders(self):
        return self.role in (self.ROLE_ADMIN, self.ROLE_SALES)
