"use client";

import { backendHttpUrl } from "@/lib/websocket";
import type { AppUser, AuthSession, SignUpPayload, UserPayload } from "@/features/users/types";

const SESSION_KEY = "market-bot-session";

export function loadStoredSession(): AuthSession | null {
  if (typeof window === "undefined") {
    return null;
  }

  const raw = window.localStorage.getItem(SESSION_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as AuthSession;
  } catch {
    window.localStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function storeSession(session: AuthSession) {
  window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
}

export function clearSession() {
  window.localStorage.removeItem(SESSION_KEY);
}

export async function login(username: string, password: string): Promise<AuthSession> {
  const response = await fetch(`${backendHttpUrl()}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  });
  if (!response.ok) {
    throw new Error("Invalid username or password");
  }
  return response.json() as Promise<AuthSession>;
}

export async function signUp(payload: SignUpPayload): Promise<AuthSession> {
  const response = await fetch(`${backendHttpUrl()}/api/auth/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || "Registration failed");
  }
  return response.json() as Promise<AuthSession>;
}

export async function fetchCurrentUser(token: string): Promise<AppUser> {
  const response = await authFetch(token, "/api/auth/me");
  return response.json() as Promise<AppUser>;
}

export async function fetchUsers(token: string): Promise<AppUser[]> {
  const response = await authFetch(token, "/api/users");
  return response.json() as Promise<AppUser[]>;
}

export async function createUser(token: string, payload: UserPayload): Promise<AppUser> {
  const response = await authFetch(token, "/api/users", {
    method: "POST",
    body: JSON.stringify(payload)
  });
  return response.json() as Promise<AppUser>;
}

export async function updateUser(token: string, userId: number, payload: UserPayload): Promise<AppUser> {
  const response = await authFetch(token, `/api/users/${userId}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
  return response.json() as Promise<AppUser>;
}

export async function updateCurrentUser(token: string, payload: UserPayload): Promise<AppUser> {
  const response = await authFetch(token, "/api/users/me", {
    method: "PUT",
    body: JSON.stringify(payload)
  });
  return response.json() as Promise<AppUser>;
}

export async function deleteUser(token: string, userId: number): Promise<void> {
  await authFetch(token, `/api/users/${userId}`, { method: "DELETE" });
}

export async function authFetch(token: string, path: string, init: RequestInit = {}): Promise<Response> {
  const response = await fetch(`${backendHttpUrl()}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      ...init.headers
    }
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed with ${response.status}`);
  }

  return response;
}
