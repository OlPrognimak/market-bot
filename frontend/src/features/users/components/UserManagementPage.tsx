"use client";

import { FormEvent, useEffect, useState } from "react";
import { createUser, deleteUser, fetchUsers, updateCurrentUser, updateUser } from "@/lib/auth";
import type { AppUser, UserPayload, UserProperty, UserRole } from "../types";
import { WatchlistEditor, watchlistProperties, watchlistToRows, type WatchlistRow } from "./WatchlistEditor";

type Props = {
  token: string;
  currentUser: AppUser;
  onCurrentUserUpdated?: (user: AppUser) => void;
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
  cryptoRows: WatchlistRow[];
  sharesRollingThreshold: string;
  sharesDeltaThreshold: string;
  cryptoRollingThreshold: string;
  cryptoDeltaThreshold: string;
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
  cryptoRows: [
    { propertyName: "BTC", propertyValue: "Bitcoin", enabled: true }
  ],
  sharesRollingThreshold: "0.8",
  sharesDeltaThreshold: "0.0001",
  cryptoRollingThreshold: "0.8",
  cryptoDeltaThreshold: "0.0001",
  telegramEnabled: true,
  telegramBotToken: "",
  telegramChatId: "",
  whatsAppEnabled: false,
  whatsAppChatId: ""
};

export function UserManagementPage({ token, currentUser, onCurrentUserUpdated }: Props) {
  const isAdmin = currentUser.role === "ADMIN";
  const [users, setUsers] = useState<AppUser[]>([]);
  const [form, setForm] = useState<FormState>(() => isAdmin ? emptyForm : userToForm(currentUser));
  const [dialogMode, setDialogMode] = useState<"create" | "edit" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const loadUsers = async () => {
    setError(null);
    if (!isAdmin) {
      setUsers([currentUser]);
      setForm(userToForm(currentUser));
      return;
    }
    setUsers(await fetchUsers(token));
  };

  useEffect(() => {
    loadUsers().catch((err) => setError(err instanceof Error ? err.message : "Could not load users"));
  }, [token, currentUser.id, isAdmin]);

  const openNewUserDialog = () => {
    setForm(emptyForm);
    setError(null);
    setDialogMode("create");
  };

  const openEditUserDialog = (user: AppUser) => {
    setForm(userToForm(user));
    setError(null);
    setDialogMode("edit");
  };

  const closeDialog = () => {
    setDialogMode(null);
    setForm(emptyForm);
    setError(null);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const payload = toPayload(form);
      if (!isAdmin) {
        const updatedUser = await updateCurrentUser(token, payload);
        setUsers([updatedUser]);
        setForm(userToForm(updatedUser));
        onCurrentUserUpdated?.(updatedUser);
      } else if (form.id) {
        await updateUser(token, form.id, payload);
        closeDialog();
      } else {
        await createUser(token, payload);
        closeDialog();
      }
      await loadUsers();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save user");
    } finally {
      setBusy(false);
    }
  };

  const removeUser = async (user: AppUser) => {
    setBusy(true);
    setError(null);
    try {
      await deleteUser(token, user.id);
      await loadUsers();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete user");
    } finally {
      setBusy(false);
    }
  };

  if (!isAdmin) {
    return renderUserForm("Edit User", false);
  }

  return (
    <section className="users-layout users-admin-layout">
      <div className="users-list">
        <div className="section-header">
          <h2>Users</h2>
          <button type="button" className="secondary-button" onClick={openNewUserDialog}>
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
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id}>
                  <td>{user.username}</td>
                  <td>{user.displayName}</td>
                  <td>{user.role}</td>
                  <td>{user.enabled ? "Enabled" : "Disabled"}</td>
                  <td className="users-actions-cell">
                    <button type="button" className="secondary-button compact-action-button" onClick={() => openEditUserDialog(user)} disabled={busy}>
                      Edit
                    </button>
                    <button type="button" className="danger-button compact-action-button" onClick={() => removeUser(user)} disabled={busy}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
              {users.length === 0 ? (
                <tr>
                  <td colSpan={5}>No users found.</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
        {error && !dialogMode ? <div className="error-banner users-page-error">{error}</div> : null}
      </div>

      {dialogMode ? (
        <div className="modal-backdrop" role="presentation" onClick={closeDialog}>
          <section className="user-dialog" role="dialog" aria-modal="true" aria-labelledby="user-dialog-title" onClick={(event) => event.stopPropagation()}>
            <header className="user-dialog-header">
              <h2 id="user-dialog-title">{dialogMode === "edit" ? "Update User" : "Create User"}</h2>
              <button type="button" className="icon-button dialog-close-button" onClick={closeDialog} aria-label="Close dialog">
                <CloseIcon />
              </button>
            </header>
            {renderUserForm(dialogMode === "edit" ? "Update User" : "Create User", true)}
          </section>
        </div>
      ) : null}
    </section>
  );

  function renderUserForm(title: string, dialog: boolean) {
    return (
      <form className={`user-form ${dialog ? "dialog-user-form" : ""}`} onSubmit={submit}>
        {!dialog ? <h2>{title}</h2> : null}
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
        {isAdmin ? (
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
        ) : null}
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
          }))} token={token} catalogType="stocks" pickerLabel="Symbol" />
        </fieldset>
        <fieldset>
          <legend>Crypto Coins</legend>
          <WatchlistEditor rows={form.cryptoRows} setRows={(update) => setForm((current) => ({
            ...current,
            cryptoRows: typeof update === "function" ? update(current.cryptoRows) : update
          }))} addLabel="Add Manually" token={token} catalogType="crypto" pickerLabel="Coin" />
        </fieldset>
        <fieldset>
          <legend>Shares Alert Settings</legend>
          <label>
            Rolling threshold
            <input value={form.sharesRollingThreshold} onChange={(event) => setForm({ ...form, sharesRollingThreshold: event.target.value })} inputMode="decimal" />
          </label>
          <label>
            Delta threshold
            <input value={form.sharesDeltaThreshold} onChange={(event) => setForm({ ...form, sharesDeltaThreshold: event.target.value })} inputMode="decimal" />
          </label>
        </fieldset>
        <fieldset>
          <legend>Crypto Alert Settings</legend>
          <label>
            Rolling threshold
            <input value={form.cryptoRollingThreshold} onChange={(event) => setForm({ ...form, cryptoRollingThreshold: event.target.value })} inputMode="decimal" />
          </label>
          <label>
            Delta threshold
            <input value={form.cryptoDeltaThreshold} onChange={(event) => setForm({ ...form, cryptoDeltaThreshold: event.target.value })} inputMode="decimal" />
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
          {dialog ? (
            <button type="button" className="secondary-button" onClick={closeDialog} disabled={busy}>
              Cancel
            </button>
          ) : null}
        </div>
      </form>
    );
  }
}

function userToForm(user: AppUser): FormState {
  return {
    id: user.id,
    username: user.username,
    password: "",
    displayName: user.displayName,
    email: user.email,
    role: user.role,
    enabled: user.enabled,
    metadataText: metadataToText(user.metadata),
    watchlistRows: watchlistToRows(user.properties),
    cryptoRows: watchlistToRows(user.properties, "CRYPTO_COIN"),
    sharesRollingThreshold: alertValue(user.properties, "shares-alert-rolling-threshold", "alert-rolling-threshold", "0.8"),
    sharesDeltaThreshold: alertValue(user.properties, "shares-alert-delta-threshold", "alert-delta-threshold", "0.0001"),
    cryptoRollingThreshold: alertValue(user.properties, "crypto-alert-rolling-threshold", "alert-rolling-threshold", "0.8"),
    cryptoDeltaThreshold: alertValue(user.properties, "crypto-alert-delta-threshold", "alert-delta-threshold", "0.0001"),
    telegramEnabled: propertyValue(user.properties, "BOT", "telegram-enabled") !== "false",
    telegramBotToken: propertyValue(user.properties, "BOT", "telegram-bot-token") ?? "",
    telegramChatId: propertyValue(user.properties, "BOT", "telegram-chat-id") ?? "",
    whatsAppEnabled: propertyValue(user.properties, "BOT", "whatsapp-enabled") === "true",
    whatsAppChatId: propertyValue(user.properties, "BOT", "whatsapp-chat-id") ?? ""
  };
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
      ...watchlistProperties(form.cryptoRows, { propertyType: "CRYPTO_COIN", description: "Crypto coin" })
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

function alertValue(properties: AppUser["properties"], name: string, fallbackName: string, defaultValue: string): string {
  return propertyValue(properties, "ALERT_SETTING", name)
    ?? propertyValue(properties, "ALERT_SETTING", fallbackName)
    ?? defaultValue;
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
    { propertyType: "ALERT_SETTING", propertyName: "shares-alert-rolling-threshold", propertyValue: form.sharesRollingThreshold.trim(), enabled: true, description: "Shares rolling movement threshold", propertyValueType: "TEXT" },
    { propertyType: "ALERT_SETTING", propertyName: "shares-alert-delta-threshold", propertyValue: form.sharesDeltaThreshold.trim(), enabled: true, description: "Shares delta movement threshold", propertyValueType: "TEXT" },
    { propertyType: "ALERT_SETTING", propertyName: "crypto-alert-rolling-threshold", propertyValue: form.cryptoRollingThreshold.trim(), enabled: true, description: "Crypto rolling movement threshold", propertyValueType: "TEXT" },
    { propertyType: "ALERT_SETTING", propertyName: "crypto-alert-delta-threshold", propertyValue: form.cryptoDeltaThreshold.trim(), enabled: true, description: "Crypto delta movement threshold", propertyValueType: "TEXT" }
  ];

  return properties.filter((property) => property.propertyValue);
}

function CloseIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
      <path d="M6.4 5 5 6.4 10.6 12 5 17.6 6.4 19 12 13.4 17.6 19 19 17.6 13.4 12 19 6.4 17.6 5 12 10.6 6.4 5Z" />
    </svg>
  );
}
