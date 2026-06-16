"use client";

import { backendHttpUrl } from "@/lib/websocket";
import type {
  AppUser,
  AuthSession,
  CatalogItemPayload,
  CryptoCatalogItem,
  SignUpPayload,
  StockCatalogItem,
  SymbolValidationResult,
  UserPayload,
  WatchlistCatalogItem
} from "@/features/users/types";

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
    const session = JSON.parse(raw) as Partial<AuthSession>;
    if (isAuthSession(session)) {
      return session;
    }
    window.localStorage.removeItem(SESSION_KEY);
    return null;
  } catch {
    window.localStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export function storeSession(session: AuthSession) {
  if (!isAuthSession(session)) {
    throw new Error("Invalid authentication session");
  }
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
  return parseAuthSession(response);
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
  return parseAuthSession(response);
}

async function parseAuthSession(response: Response): Promise<AuthSession> {
  const session = await response.json() as Partial<AuthSession>;
  if (!isAuthSession(session)) {
    throw new Error("Invalid authentication response");
  }
  return session;
}

function isAuthSession(value: Partial<AuthSession> | null | undefined): value is AuthSession {
  return Boolean(
    value
    && typeof value.token === "string"
    && value.token.length > 0
    && value.user
    && typeof value.user.displayName === "string"
    && typeof value.user.role === "string"
  );
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

export async function fetchWatchlistCatalog(token: string, type: "stocks" | "crypto"): Promise<WatchlistCatalogItem[]> {
  const response = await authFetch(token, `/api/watchlists/${type}`);
  return response.json() as Promise<WatchlistCatalogItem[]>;
}

export async function validateWatchlistSymbol(token: string, type: "stocks" | "crypto", symbol: string): Promise<SymbolValidationResult> {
  const response = await authFetch(token, `/api/watchlists/${type}/validate?symbol=${encodeURIComponent(symbol)}`);
  return response.json() as Promise<SymbolValidationResult>;
}

export async function fetchStockCatalog(token: string): Promise<StockCatalogItem[]> {
  const response = await authFetch(token, "/api/catalog/stocks");
  return response.json() as Promise<StockCatalogItem[]>;
}

export async function fetchCryptoCatalog(token: string): Promise<CryptoCatalogItem[]> {
  const response = await authFetch(token, "/api/catalog/crypto");
  return response.json() as Promise<CryptoCatalogItem[]>;
}

export async function saveStockCatalogItem(token: string, payload: CatalogItemPayload, id?: number): Promise<StockCatalogItem> {
  const response = await authFetch(token, id ? `/api/catalog/stocks/${id}` : "/api/catalog/stocks", {
    method: id ? "PUT" : "POST",
    body: JSON.stringify(payload)
  });
  return response.json() as Promise<StockCatalogItem>;
}

export async function saveCryptoCatalogItem(token: string, payload: CatalogItemPayload, id?: number): Promise<CryptoCatalogItem> {
  const response = await authFetch(token, id ? `/api/catalog/crypto/${id}` : "/api/catalog/crypto", {
    method: id ? "PUT" : "POST",
    body: JSON.stringify(payload)
  });
  return response.json() as Promise<CryptoCatalogItem>;
}

export async function deleteStockCatalogItem(token: string, id: number): Promise<void> {
  await authFetch(token, `/api/catalog/stocks/${id}`, { method: "DELETE" });
}

export async function deleteCryptoCatalogItem(token: string, id: number): Promise<void> {
  await authFetch(token, `/api/catalog/crypto/${id}`, { method: "DELETE" });
}

export async function authFetch(token: string, path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("Authorization", `Bearer ${token}`);
  if (!(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${backendHttpUrl()}${path}`, {
    ...init,
    headers
  });

  if (!response.ok) {
    const message = await response.text();
    if (response.status === 401 || response.status === 403) {
      throw new Error(message || "Session expired or access denied. Please log out and sign in again.");
    }
    throw new Error(message || `Request failed with ${response.status}`);
  }

  return response;
}
