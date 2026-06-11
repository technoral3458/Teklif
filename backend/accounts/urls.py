from django.urls import path
from . import views

urlpatterns = [
    path("me/", views.me),
    path("users/", views.UserListCreateView.as_view()),
    path("users/<int:pk>/", views.UserDetailView.as_view()),
    path("dealers/", views.dealers),
]
