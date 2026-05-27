"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { createUser, deleteUser, fetchUsers, updateUser } from "@/lib/auth";
import type { AppUser, UserPayload, UserProperty, UserRole } from "../types";
import { WatchlistEditor, watchlistProperties, watchlistToRows, type WatchlistRow } from "./WatchlistEditor";

type Props = {
  token: string;
};

type FormState = {
  id: number | null;
  username: string;
  password: string;
  displayName: string;
  email: string;
  role: UserRole;
  enabled: boolean;
  metadataText: string;
  watchlistRows: WatchlistRow[];
  cryptoCoinsText: string;
  rollingThreshold: string;
  deltaThreshold: string;
  telegramEnabled: boolean;
  telegramBotToken: string;
  telegramChatId: string;
  whatsAppEnabled: boolean;
  whatsAppChatId: string;
};

const emptyForm: FormState = {
  id: null,
  username: "",
  password: "",
  displayName: "",
  email: "",
  role: "USER",
  enabled: true,
  metadataText: "environment=production\nregion=EU",
  watchlistRows: [
    { propertyName: "AAPL", propertyValue: "Apple", enabled: true },
    { propertyName: "NVDA", propertyValue: "NVIDIA", enabled: true }
  ],
  cryptoCoinsText: "BTC",
  rollingThreshold: "0.8",
  deltaThreshold: "0.0001",
  telegramEnabled: true,
  telegramBotToken: "",
  telegramChatId: "",
  whatsAppEnabled: false,
  whatsAppChatId: ""
};

export function UserManagementPage({ token }: Props) {
  const [users, setUsers] = useState<AppUser[]>([]);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const selectedUser = useMemo(() => users.find((user) => user.id === form.id) ?? null, [form.id, users]);

  const loadUsers = async () => {
    setError(null);
    setUsers(await fetchUsers(token));
  };

  useEffect(() => {
    loadUsers().catch((err) => setError(err instanceof Error ? err.message : "Could not load users"));
  }, [token]);

  const selectUser = (user: AppUser) => {
    setForm({
      id: user.id,
      username: user.username,
      password: "",
      displayName: user.displayName,
      email: user.email,
      role: user.role,
      enabled: user.enabled,
      metadataText: metadataToText(user.metadata),
      watchlistRows: watchlistToRows(user.properties),
      cryptoCoinsText: cryptoCoinsToText(user.properties),
      rollingThreshold: propertyValue(user.properties, "ALERT_SETTING", "alert-rolling-threshold") ?? "0.8",
      deltaThreshold: propertyValue(user.properties, "ALERT_SETTING", "alert-delta-threshold") ?? "0.0001",
      telegramEnabled: propertyValue(user.properties, "BOT", "telegram-enabled") !== "false",
      telegramBotToken: propertyValue(user.properties, "BOT", "telegram-bot-token") ?? "",
      telegramChatId: propertyValue(user.properties, "BOT", "telegram-chat-id") ?? "",
      whatsAppEnabled: propertyValue(user.properties, "BOT", "whatsapp-enabled") === "true",
      whatsAppChatId: propertyValue(user.properties, "BOT", "whatsapp-chat-id") ?? ""
    });
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const payload = toPayload(form);
      if (form.id) {
        await updateUser(token, form.id, payload);
      } else {
        await createUser(token, payload);
      }
      setForm(emptyForm);
      await loadUsers();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save user");
    } finally {
      setBusy(false);
    }
  };

  const removeSelected = async () => {
    if (!form.id || !selectedUser) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await deleteUser(token, form.id);
      setForm(emptyForm);
      await loadUsers();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete user");
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="users-layout">
      <div className="users-list">
        <div className="section-header">
          <h2>Users</h2>
          <button type="button" className="secondary-button" onClick={() => setForm(emptyForm)}>
            New User
          </button>
        </div>
        <div className="table-frame">
          <table className="users-table">
            <thead>
              <tr>
                <th>Username</th>
                <th>Name</th>
                <th>Role</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id} className={form.id === user.id ? "selected-row" : ""} onClick={() => selectUser(user)}>
                  <td>{user.username}</td>
                  <td>{user.displayName}</td>
                  <td>{user.role}</td>
                  <td>{user.enabled ? "Enabled" : "Disabled"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <form className="user-form" onSubmit={submit}>
        <h2>{form.id ? "Update User" : "Create User"}</h2>
        <label>
          Username
          <input value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} required />
        </label>
        <label>
          Display name
          <input value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} required />
        </label>
        <label>
          Email
          <input value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} type="email" required />
        </label>
        <label>
          Password {form.id ? <span className="label-note">leave empty to keep current</span> : null}
          <input
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
            type="password"
            minLength={form.id ? undefined : 8}
            required={!form.id}
          />
        </label>
        <div className="form-row">
          <label>
            Role
            <select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value as UserRole })}>
              <option value="USER">User</option>
              <option value="ADMIN">Admin</option>
            </select>
          </label>
          <label className="checkbox-label">
            <input
              checked={form.enabled}
              onChange={(event) => setForm({ ...form, enabled: event.target.checked })}
              type="checkbox"
            />
            Enabled
          </label>
        </div>
        <label>
          Environment metadata
          <textarea
            value={form.metadataText}
            onChange={(event) => setForm({ ...form, metadataText: event.target.value })}
            rows={6}
          />
        </label>
        <fieldset>
          <legend>Stock Watchlist</legend>
          <WatchlistEditor rows={form.watchlistRows} setRows={(update) => setForm((current) => ({
            ...current,
            watchlistRows: typeof update === "function" ? update(current.watchlistRows) : update
          }))} />
        </fieldset>
        <fieldset>
          <legend>Crypto Coins</legend>
          <textarea
            value={form.cryptoCoinsText}
            onChange={(event) => setForm({ ...form, cryptoCoinsText: event.target.value })}
            rows={3}
          />
        </fieldset>
        <fieldset>
          <legend>Alert Settings</legend>
          <label>
            Rolling threshold
            <input value={form.rollingThreshold} onChange={(event) => setForm({ ...form, rollingThreshold: event.target.value })} inputMode="decimal" />
          </label>
          <label>
            Delta threshold
            <input value={form.deltaThreshold} onChange={(event) => setForm({ ...form, deltaThreshold: event.target.value })} inputMode="decimal" />
          </label>
        </fieldset>
        <fieldset>
          <legend>Messengers</legend>
          <label className="checkbox-label">
            <input checked={form.telegramEnabled} onChange={(event) => setForm({ ...form, telegramEnabled: event.target.checked })} type="checkbox" />
            Telegram enabled
          </label>
          <label>
            Telegram bot token
            <input value={form.telegramBotToken} onChange={(event) => setForm({ ...form, telegramBotToken: event.target.value })} />
          </label>
          <label>
            Telegram chat id
            <input value={form.telegramChatId} onChange={(event) => setForm({ ...form, telegramChatId: event.target.value })} />
          </label>
          <label className="checkbox-label">
            <input checked={form.whatsAppEnabled} onChange={(event) => setForm({ ...form, whatsAppEnabled: event.target.checked })} type="checkbox" />
            WhatsApp enabled
          </label>
          <label>
            WhatsApp chat id
            <input value={form.whatsAppChatId} onChange={(event) => setForm({ ...form, whatsAppChatId: event.target.value })} />
          </label>
        </fieldset>
        {error ? <div className="error-banner">{error}</div> : null}
        <div className="form-actions">
          <button type="submit" disabled={busy}>
            {busy ? "Saving" : form.id ? "Update" : "Create"}
          </button>
          {form.id ? (
            <button type="button" className="danger-button" onClick={removeSelected} disabled={busy}>
              Delete
            </button>
          ) : null}
        </div>
      </form>
    </section>
  );
}

function toPayload(form: FormState): UserPayload {
  return {
    username: form.username.trim(),
    password: form.password || undefined,
    displayName: form.displayName.trim(),
    email: form.email.trim(),
    role: form.role,
    enabled: form.enabled,
    metadata: textToMetadata(form.metadataText),
    properties: [
      ...botProperties(form),
      ...alertProperties(form),
      ...watchlistProperties(form.watchlistRows),
      ...cryptoCoinProperties(form.cryptoCoinsText)
    ]
  };
}

function textToMetadata(value: string): Record<string, string> {
  return Object.fromEntries(
    value.split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const [key, ...rest] = line.split("=");
        return [key.trim(), rest.join("=").trim()];
      })
      .filter(([key, val]) => key && val)
  );
}

function metadataToText(metadata: Record<string, string>): string {
  return Object.entries(metadata).map(([key, value]) => `${key}=${value}`).join("\n");
}

function propertyValue(properties: AppUser["properties"], type: string, name: string): string | null {
  return properties.find((property) => property.propertyType === type && property.propertyName === name)?.propertyValue ?? null;
}

function cryptoCoinsToText(properties: AppUser["properties"]): string {
  return properties
    .filter((property) => property.propertyType === "CRYPTO_COIN")
    .map((property) => property.propertyName)
    .join("\n");
}

function botProperties(form: FormState): UserPayload["properties"] {
  const properties: UserProperty[] = [
    { propertyType: "BOT", propertyName: "telegram-enabled", propertyValue: String(form.telegramEnabled), enabled: true, description: "Telegram messenger enabled", propertyValueType: "TEXT" },
    { propertyType: "BOT", propertyName: "telegram-bot-token", propertyValue: form.telegramBotToken.trim(), enabled: form.telegramEnabled, description: "Telegram bot token", propertyValueType: "SECRET" },
    { propertyType: "BOT", propertyName: "telegram-chat-id", propertyValue: form.telegramChatId.trim(), enabled: form.telegramEnabled, description: "Telegram chat id", propertyValueType: "TEXT" },
    { propertyType: "BOT", propertyName: "whatsapp-enabled", propertyValue: String(form.whatsAppEnabled), enabled: true, description: "WhatsApp messenger enabled", propertyValueType: "TEXT" },
    { propertyType: "BOT", propertyName: "whatsapp-chat-id", propertyValue: form.whatsAppChatId.trim(), enabled: form.whatsAppEnabled, description: "WhatsApp chat id", propertyValueType: "TEXT" }
  ];

  return properties.filter((property) => property.propertyValue);
}

function alertProperties(form: FormState): UserPayload["properties"] {
  const properties: UserProperty[] = [
    { propertyType: "ALERT_SETTING", propertyName: "alert-rolling-threshold", propertyValue: form.rollingThreshold.trim(), enabled: true, description: "Rolling movement threshold", propertyValueType: "TEXT" },
    { propertyType: "ALERT_SETTING", propertyName: "alert-delta-threshold", propertyValue: form.deltaThreshold.trim(), enabled: true, description: "Delta movement threshold", propertyValueType: "TEXT" }
  ];

  return properties.filter((property) => property.propertyValue);
}

function cryptoCoinProperties(value: string): UserPayload["properties"] {
  return value.split(/[\n, ]/)
    .map((coin) => coin.trim().toUpperCase())
    .filter(Boolean)
    .map((coin): UserProperty => ({
      propertyType: "CRYPTO_COIN",
      propertyName: coin,
      propertyValue: coin,
      enabled: true,
      description: "Crypto coin",
      propertyValueType: "SYMBOL"
    }));
}
