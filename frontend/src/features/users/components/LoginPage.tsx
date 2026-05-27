"use client";

import { FormEvent, useState } from "react";
import { login, storeSession } from "@/lib/auth";
import type { AuthSession } from "../types";

type Props = {
  onLogin: (session: AuthSession) => void;
  onShowSignUp: () => void;
};

export function LoginPage({ onLogin, onShowSignUp }: Props) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const session = await login(username, password);
      storeSession(session);
      onLogin(session);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Login failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-page">
      <form className="auth-panel" onSubmit={submit}>
        <h1>Market Bot</h1>
        <label>
          Username
          <input value={username} onChange={(event) => setUsername(event.target.value)} autoComplete="username" />
        </label>
        <label>
          Password
          <input
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            type="password"
            autoComplete="current-password"
          />
        </label>
        {error ? <div className="error-banner">{error}</div> : null}
        <button type="submit" disabled={busy || !username || !password}>
          {busy ? "Signing in" : "Sign In"}
        </button>
        <p className="auth-switch">
          Need an account?{" "}
          <button type="button" className="link-button" onClick={onShowSignUp}>
            Sign Up
          </button>
        </p>
      </form>
    </main>
  );
}
