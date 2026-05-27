"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { createUser, deleteUser, fetchUsers, updateUser } from "@/lib/auth";
import type { AppUser, UserPayload, UserRole } from "../types";

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
};

const emptyForm: FormState = {
  id: null,
  username: "",
  password: "",
  displayName: "",
  email: "",
  role: "USER",
  enabled: true,
  metadataText: "environment=production\nregion=EU"
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
      metadataText: metadataToText(user.metadata)
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
          <input value={form.username} onChange={(event) => setForm({ ...form, username: event.target.value })} />
        </label>
        <label>
          Display name
          <input value={form.displayName} onChange={(event) => setForm({ ...form, displayName: event.target.value })} />
        </label>
        <label>
          Email
          <input value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} type="email" />
        </label>
        <label>
          Password {form.id ? <span className="label-note">leave empty to keep current</span> : null}
          <input
            value={form.password}
            onChange={(event) => setForm({ ...form, password: event.target.value })}
            type="password"
            minLength={form.id ? undefined : 8}
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
        {error ? <div className="error-banner">{error}</div> : null}
        <div className="form-actions">
          <button type="submit" disabled={busy || !form.username || !form.displayName || !form.email || (!form.id && form.password.length < 8)}>
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
    properties: []
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
