"use client";

import { useEffect, useState } from "react";
import { DashboardPage } from "@/features/dashboard/components/DashboardPage";
import { LoginPage } from "@/features/users/components/LoginPage";
import { SignUpPage } from "@/features/users/components/SignUpPage";
import { UserManagementPage } from "@/features/users/components/UserManagementPage";
import { clearSession, fetchCurrentUser, loadStoredSession, storeSession } from "@/lib/auth";
import type { AuthSession } from "@/features/users/types";

export default function Home() {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [view, setView] = useState<"dashboard" | "users">("dashboard");
  const [authView, setAuthView] = useState<"login" | "signup">("login");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const storedSession = loadStoredSession();
    if (!storedSession) {
      setLoading(false);
      return;
    }

    fetchCurrentUser(storedSession.token)
      .then((user) => {
        const refreshedSession = { token: storedSession.token, user };
        storeSession(refreshedSession);
        setSession(refreshedSession);
      })
      .catch(() => clearSession())
      .finally(() => setLoading(false));
  }, []);

  const logout = () => {
    clearSession();
    setSession(null);
    setView("dashboard");
  };

  if (loading) {
    return <main className="dashboard">Loading</main>;
  }

  if (!session) {
    return authView === "signup" ? (
      <SignUpPage onLogin={setSession} onShowLogin={() => setAuthView("login")} />
    ) : (
      <LoginPage onLogin={setSession} onShowSignUp={() => setAuthView("signup")} />
    );
  }

  return (
    <>
      {view === "users" && session.user.role === "ADMIN" ? (
        <main className="dashboard">
          <header className="dashboard-header">
            <div>
              <h1>User Management</h1>
              <p>Create, update, and remove user access.</p>
            </div>
            <div className="header-actions">
              <button type="button" className="secondary-button" onClick={() => setView("dashboard")}>
                Dashboard
              </button>
              <button type="button" className="secondary-button" onClick={logout}>
                Logout
              </button>
            </div>
          </header>
          <UserManagementPage token={session.token} />
        </main>
      ) : (
        <DashboardPage
          token={session.token}
          currentUser={session.user}
          onLogout={logout}
          onOpenUsers={() => setView("users")}
        />
      )}
    </>
  );
}
