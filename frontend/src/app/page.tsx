"use client";

import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { CryptoDashboardPage } from "@/features/dashboard/components/CryptoDashboardPage";
import { DashboardPage } from "@/features/dashboard/components/DashboardPage";
import { FuturesDashboardPage } from "@/features/dashboard/components/FuturesDashboardPage";
import { CatalogManagementPage } from "@/features/users/components/CatalogManagementPage";
import { LoginPage } from "@/features/users/components/LoginPage";
import { SignUpPage } from "@/features/users/components/SignUpPage";
import { SystemSettingsPage } from "@/features/users/components/SystemSettingsPage";
import { UserManagementPage } from "@/features/users/components/UserManagementPage";
import { UserSettingsPage } from "@/features/users/components/UserSettingsPage";
import { AnalyzePage } from "@/features/portfolio/components/AnalyzePage";
import { ImportDataPage } from "@/features/portfolio/components/ImportDataPage";
import { IbkrTradePage } from "@/features/trading/components/IbkrTradePage";
import { clearSession, fetchCurrentUser, loadStoredSession, storeSession } from "@/lib/auth";
import type { AuthSession } from "@/features/users/types";

type AppView = "dashboard" | "crypto" | "futures" | "ibkrTrade" | "import" | "analyze" | "users" | "settings" | "catalog" | "system";

export default function Home() {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [view, setView] = useState<AppView>("dashboard");
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

  if (!session.user) {
    clearSession();
    return <main className="dashboard">Session expired. Reload the page to sign in again.</main>;
  }

  const goToView = (nextView: AppView) => {
    setView(nextView);
    window.location.hash = nextView === "dashboard" ? "shares" : nextView;
  };

  return (
    <AppShell session={session} view={view} onNavigate={goToView} onLogout={logout}>
      {view === "settings" ? (
        <UserSettingsPage
          token={session.token}
          currentUser={session.user}
          onBack={() => goToView("dashboard")}
          onSave={(user) => {
            const refreshedSession = { token: session.token, user };
            storeSession(refreshedSession);
            setSession(refreshedSession);
          }}
        />
      ) : view === "crypto" ? (
        <CryptoDashboardPage
          token={session.token}
          currentUser={session.user}
          onBack={() => goToView("dashboard")}
          onLogout={logout}
          onOpenSettings={() => goToView("settings")}
        />
      ) : view === "futures" ? (
        <FuturesDashboardPage token={session.token} currentUser={session.user} />
      ) : view === "ibkrTrade" ? (
        <IbkrTradePage token={session.token} />
      ) : view === "import" ? (
        <ImportDataPage token={session.token} />
      ) : view === "analyze" ? (
        <AnalyzePage token={session.token} />
      ) : view === "users" ? (
        <main className="dashboard">
          <header className="dashboard-header">
            <div>
              <h1>{session.user.role === "ADMIN" ? "User Management" : "Account"}</h1>
              <p>{session.user.role === "ADMIN" ? "Create, update, and remove user access." : "Update your account and personal scanner settings."}</p>
            </div>
          </header>
          <UserManagementPage
            token={session.token}
            currentUser={session.user}
            onCurrentUserUpdated={(user) => {
              const refreshedSession = { token: session.token, user };
              storeSession(refreshedSession);
              setSession(refreshedSession);
            }}
          />
        </main>
      ) : view === "catalog" && session.user.role === "ADMIN" ? (
        <main className="dashboard">
          <header className="dashboard-header">
            <div>
              <h1>Catalog Management</h1>
              <p>Maintain the initial shares and crypto coin lists used by scanners and user settings.</p>
            </div>
          </header>
          <CatalogManagementPage token={session.token} />
        </main>
      ) : view === "system" && session.user.role === "ADMIN" ? (
        <main className="dashboard">
          <header className="dashboard-header">
            <div>
              <h1>System Settings</h1>
              <p>Manage provider symbol mappings and selectable API credentials.</p>
            </div>
          </header>
          <SystemSettingsPage token={session.token} />
        </main>
      ) : (
        <DashboardPage
          token={session.token}
          currentUser={session.user}
          onLogout={logout}
          onOpenUsers={() => goToView("users")}
          onOpenSettings={() => goToView("settings")}
          onOpenCrypto={() => goToView("crypto")}
        />
      )}
    </AppShell>
  );
}

function AppShell({
  session,
  view,
  onNavigate,
  onLogout,
  children
}: {
  session: AuthSession;
  view: AppView;
  onNavigate: (view: AppView) => void;
  onLogout: () => void;
  children: ReactNode;
}) {
  const links: Array<{ view: AppView; label: string }> = [
    { view: "dashboard", label: "Shares" },
    { view: "crypto", label: "Crypto" },
    { view: "futures", label: "Futures" },
    { view: "ibkrTrade", label: "IBKR Trade" },
    { view: "import", label: "Import data" },
    { view: "analyze", label: "Analyze" },
    { view: "settings", label: "Settings" },
    { view: "users", label: session.user.role === "ADMIN" ? "Users" : "Account" },
    ...(session.user.role === "ADMIN" ? [
      { view: "catalog" as AppView, label: "Catalog" },
      { view: "system" as AppView, label: "System settings" }
    ] : [])
  ];

  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="app-sidebar-header">
          <strong>Market Bot</strong>
          <span>{session.user.displayName}</span>
        </div>
        <nav className="app-nav" aria-label="Main navigation">
          {links
            .map((link) => (
              <a
                key={link.view}
                href={`#${link.view === "dashboard" ? "shares" : link.view}`}
                className={view === link.view ? "active" : ""}
                onClick={(event) => {
                  event.preventDefault();
                  onNavigate(link.view);
                }}
              >
                {link.label}
              </a>
            ))}
          <a
            href="#logout"
            className="logout-link"
            onClick={(event) => {
              event.preventDefault();
              onLogout();
            }}
          >
            Logout
          </a>
        </nav>
      </aside>
      <div className="app-content">{children}</div>
    </div>
  );
}
