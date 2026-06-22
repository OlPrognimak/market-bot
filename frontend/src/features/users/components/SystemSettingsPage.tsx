"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  deleteProviderSymbolMapping,
  deleteSystemApiCredential,
  fetchProviderSymbolMappings,
  fetchSystemApiCredentials,
  saveProviderSymbolMapping,
  saveSystemApiCredential
} from "@/lib/auth";
import { ALL_FILTER_VALUE } from "@/lib/filterConstants";
import { nextSort, sortButtonLabel, sortRows, type SortColumn, type SortState } from "@/lib/tableSort";
import type {
  PortfolioProviderType,
  ProviderSymbolMapping,
  ProviderSymbolMappingPayload,
  SystemApiCredential,
  SystemApiCredentialPayload,
  SystemCredentialType
} from "../types";

type Props = {
  token: string;
};

type Section = "mappings" | "credentials";
type StatusFilter = typeof ALL_FILTER_VALUE | "ENABLED" | "DISABLED";

type MappingForm = ProviderSymbolMappingPayload & { id?: number };
type CredentialForm = SystemApiCredentialPayload & { id?: number };

const emptyMapping: MappingForm = {
  providerType: "TRADE_REPUBLIC",
  sourceSymbol: "",
  sourceSymbolType: "ISIN",
  marketProvider: "YAHOO",
  marketSymbol: "",
  instrumentName: "",
  currency: "",
  enabled: true,
  verified: false,
  priority: 100
};

const emptyCredential: CredentialForm = {
  credentialType: "AI_PROVIDER",
  providerName: "OPENAI",
  displayName: "",
  secretValue: "",
  enabled: true,
  active: false,
  description: ""
};

const contains = (value: string | null | undefined, filter: string) => {
  const normalized = filter.trim().toLowerCase();
  return !normalized || (value ?? "").toLowerCase().includes(normalized);
};

const matchesStatus = (enabled: boolean, status: StatusFilter) => {
  if (status === "ENABLED") return enabled;
  if (status === "DISABLED") return !enabled;
  return true;
};

export function SystemSettingsPage({ token }: Props) {
  const [section, setSection] = useState<Section>("mappings");
  const [mappings, setMappings] = useState<ProviderSymbolMapping[]>([]);
  const [credentials, setCredentials] = useState<SystemApiCredential[]>([]);
  const [mappingForm, setMappingForm] = useState<MappingForm>(emptyMapping);
  const [credentialForm, setCredentialForm] = useState<CredentialForm>(emptyCredential);
  const [filter, setFilter] = useState("");
  const [status, setStatus] = useState<StatusFilter>(ALL_FILTER_VALUE);
  const [mappingSort, setMappingSort] = useState<SortState<MappingColumnKey>>({ key: "provider", direction: "asc" });
  const [credentialSort, setCredentialSort] = useState<SortState<CredentialColumnKey>>({ key: "type", direction: "asc" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setError(null);
    const [mappingRows, credentialRows] = await Promise.all([
      fetchProviderSymbolMappings(token),
      fetchSystemApiCredentials(token)
    ]);
    setMappings(mappingRows);
    setCredentials(credentialRows);
  };

  useEffect(() => {
    load().catch((err) => setError(err instanceof Error ? err.message : "Could not load system settings"));
  }, [token]);

  const filteredMappings = useMemo(() => {
    const filtered = mappings.filter((row) =>
      matchesStatus(row.enabled, status)
      && (
        contains(row.providerType, filter)
        || contains(row.sourceSymbol, filter)
        || contains(row.marketSymbol, filter)
        || contains(row.instrumentName, filter)
      )
    );
    return sortRows(filtered, mappingSort, mappingColumns);
  }, [filter, mappingSort, mappings, status]);

  const filteredCredentials = useMemo(() => {
    const filtered = credentials.filter((row) =>
      matchesStatus(row.enabled, status)
      && (
        contains(row.credentialType, filter)
        || contains(row.providerName, filter)
        || contains(row.displayName, filter)
        || contains(row.description, filter)
      )
    );
    return sortRows(filtered, credentialSort, credentialColumns);
  }, [credentialSort, credentials, filter, status]);

  const saveMapping = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await saveProviderSymbolMapping(token, mappingForm, mappingForm.id);
      setMappingForm(emptyMapping);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save symbol mapping");
    } finally {
      setBusy(false);
    }
  };

  const saveCredential = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await saveSystemApiCredential(token, credentialForm, credentialForm.id);
      setCredentialForm(emptyCredential);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save API credential");
    } finally {
      setBusy(false);
    }
  };

  const removeMapping = async () => {
    if (!mappingForm.id) return;
    setBusy(true);
    setError(null);
    try {
      await deleteProviderSymbolMapping(token, mappingForm.id);
      setMappingForm(emptyMapping);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete symbol mapping");
    } finally {
      setBusy(false);
    }
  };

  const removeCredential = async () => {
    if (!credentialForm.id) return;
    setBusy(true);
    setError(null);
    try {
      await deleteSystemApiCredential(token, credentialForm.id);
      setCredentialForm(emptyCredential);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete API credential");
    } finally {
      setBusy(false);
    }
  };

  const selectMapping = (row: ProviderSymbolMapping) => {
    setMappingForm({
      id: row.id,
      providerType: row.providerType,
      sourceSymbol: row.sourceSymbol,
      sourceSymbolType: row.sourceSymbolType,
      marketProvider: row.marketProvider,
      marketSymbol: row.marketSymbol,
      instrumentName: row.instrumentName ?? "",
      currency: row.currency ?? "",
      enabled: row.enabled,
      verified: row.verified,
      priority: row.priority
    });
  };

  const selectCredential = (row: SystemApiCredential) => {
    setCredentialForm({
      id: row.id,
      credentialType: row.credentialType,
      providerName: row.providerName,
      displayName: row.displayName,
      secretValue: "",
      enabled: row.enabled,
      active: row.active,
      description: row.description ?? ""
    });
  };

  return (
    <section className="users-layout catalog-page-layout system-settings-layout">
      <div className="users-list">
        <div className="section-header">
          <h2>{section === "mappings" ? "Provider Symbol Mappings" : "API Credentials"}</h2>
          <div className="header-actions">
            <button type="button" className={section === "mappings" ? "" : "secondary-button"} onClick={() => setSection("mappings")}>Mappings</button>
            <button type="button" className={section === "credentials" ? "" : "secondary-button"} onClick={() => setSection("credentials")}>API keys</button>
          </div>
        </div>

        <section className="toolbar catalog-toolbar" aria-label="System settings filters">
          <input value={filter} onChange={(event) => setFilter(event.target.value)} placeholder="Search settings" aria-label="Search system settings" />
          <select value={status} onChange={(event) => setStatus(event.target.value as StatusFilter)} aria-label="Filter status">
            <option value={ALL_FILTER_VALUE}>All statuses</option>
            <option value="ENABLED">Enabled</option>
            <option value="DISABLED">Disabled</option>
          </select>
        </section>

        {section === "mappings" ? (
          <div className="table-frame catalog-table-frame">
            <table className="users-table system-mapping-table">
              <thead><tr>{mappingColumns.map((column) => <th key={column.key}><button type="button" className="sortable-header" onClick={() => setMappingSort((current) => nextSort(current, column.key))}>{sortButtonLabel(mappingSort, column.key, column.label)}</button></th>)}</tr></thead>
              <tbody>
                {filteredMappings.map((row) => (
                  <tr key={row.id} className={mappingForm.id === row.id ? "selected-row" : ""} onClick={() => selectMapping(row)}>
                    <td>{row.providerType}</td>
                    <td>{row.sourceSymbol}</td>
                    <td>{row.sourceSymbolType}</td>
                    <td>{row.marketProvider}:{row.marketSymbol}</td>
                    <td>{row.instrumentName || "-"}</td>
                    <td>{row.enabled ? "Enabled" : "Disabled"}</td>
                    <td>{row.verified ? "Yes" : "No"}</td>
                  </tr>
                ))}
                {filteredMappings.length === 0 ? <tr><td colSpan={7}>No mappings match the current filters.</td></tr> : null}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="table-frame catalog-table-frame">
            <table className="users-table system-credential-table">
              <thead><tr>{credentialColumns.map((column) => <th key={column.key}><button type="button" className="sortable-header" onClick={() => setCredentialSort((current) => nextSort(current, column.key))}>{sortButtonLabel(credentialSort, column.key, column.label)}</button></th>)}</tr></thead>
              <tbody>
                {filteredCredentials.map((row) => (
                  <tr key={row.id} className={credentialForm.id === row.id ? "selected-row" : ""} onClick={() => selectCredential(row)}>
                    <td>{row.credentialType}</td>
                    <td>{row.providerName}</td>
                    <td>{row.displayName}</td>
                    <td>{row.hasSecret ? row.maskedSecret : "-"}</td>
                    <td>{row.enabled ? "Enabled" : "Disabled"}</td>
                    <td>{row.active ? "Yes" : "No"}</td>
                  </tr>
                ))}
                {filteredCredentials.length === 0 ? <tr><td colSpan={6}>No API credentials match the current filters.</td></tr> : null}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {section === "mappings" ? (
        <form className="user-form" onSubmit={saveMapping}>
          <h2>{mappingForm.id ? "Update Mapping" : "Create Mapping"}</h2>
          <label>
            Provider
            <select value={mappingForm.providerType} onChange={(event) => setMappingForm({ ...mappingForm, providerType: event.target.value as PortfolioProviderType })}>
              <option value="TRADE_REPUBLIC">Trade Republic</option>
              <option value="REVOLUT">Revolut</option>
            </select>
          </label>
          <label>Source symbol<input value={mappingForm.sourceSymbol} onChange={(event) => setMappingForm({ ...mappingForm, sourceSymbol: event.target.value.toUpperCase() })} required /></label>
          <label>Source type<input value={mappingForm.sourceSymbolType} onChange={(event) => setMappingForm({ ...mappingForm, sourceSymbolType: event.target.value.toUpperCase() })} required /></label>
          <label>Market provider<input value={mappingForm.marketProvider} onChange={(event) => setMappingForm({ ...mappingForm, marketProvider: event.target.value.toUpperCase() })} required /></label>
          <label>Market symbol<input value={mappingForm.marketSymbol} onChange={(event) => setMappingForm({ ...mappingForm, marketSymbol: event.target.value.toUpperCase() })} required /></label>
          <label>Name<input value={mappingForm.instrumentName ?? ""} onChange={(event) => setMappingForm({ ...mappingForm, instrumentName: event.target.value })} /></label>
          <label>Currency<input value={mappingForm.currency ?? ""} onChange={(event) => setMappingForm({ ...mappingForm, currency: event.target.value.toUpperCase() })} /></label>
          <label>Priority<input type="number" value={mappingForm.priority} onChange={(event) => setMappingForm({ ...mappingForm, priority: Number(event.target.value) })} /></label>
          <label className="checkbox-label"><input checked={mappingForm.enabled} onChange={(event) => setMappingForm({ ...mappingForm, enabled: event.target.checked })} type="checkbox" />Enabled</label>
          <label className="checkbox-label"><input checked={mappingForm.verified} onChange={(event) => setMappingForm({ ...mappingForm, verified: event.target.checked })} type="checkbox" />Verified</label>
          {error ? <div className="error-banner">{error}</div> : null}
          <div className="form-actions">
            <button type="submit" disabled={busy || !mappingForm.sourceSymbol || !mappingForm.marketSymbol}>{busy ? "Saving" : mappingForm.id ? "Update" : "Create"}</button>
            {mappingForm.id ? <button type="button" className="danger-button" onClick={removeMapping} disabled={busy}>Delete</button> : null}
            <button type="button" className="secondary-button" onClick={() => setMappingForm(emptyMapping)}>New</button>
          </div>
        </form>
      ) : (
        <form className="user-form" onSubmit={saveCredential}>
          <h2>{credentialForm.id ? "Update API Key" : "Create API Key"}</h2>
          <label>
            Type
            <select value={credentialForm.credentialType} onChange={(event) => setCredentialForm({ ...credentialForm, credentialType: event.target.value as SystemCredentialType })}>
              <option value="AI_PROVIDER">AI provider</option>
              <option value="MARKET_DATA_PROVIDER">Market data provider</option>
              <option value="MESSAGING_PROVIDER">Messaging provider</option>
            </select>
          </label>
          <label>Provider<input value={credentialForm.providerName} onChange={(event) => setCredentialForm({ ...credentialForm, providerName: event.target.value.toUpperCase() })} required /></label>
          <label>Display name<input value={credentialForm.displayName} onChange={(event) => setCredentialForm({ ...credentialForm, displayName: event.target.value })} required /></label>
          <label>Secret value<input type="password" value={credentialForm.secretValue ?? ""} onChange={(event) => setCredentialForm({ ...credentialForm, secretValue: event.target.value })} placeholder={credentialForm.id ? "Leave empty to keep existing secret" : ""} /></label>
          <label>Description<input value={credentialForm.description ?? ""} onChange={(event) => setCredentialForm({ ...credentialForm, description: event.target.value })} /></label>
          <label className="checkbox-label"><input checked={credentialForm.enabled} onChange={(event) => setCredentialForm({ ...credentialForm, enabled: event.target.checked })} type="checkbox" />Enabled</label>
          <label className="checkbox-label"><input checked={credentialForm.active} onChange={(event) => setCredentialForm({ ...credentialForm, active: event.target.checked })} type="checkbox" />Active for this credential type</label>
          {error ? <div className="error-banner">{error}</div> : null}
          <div className="form-actions">
            <button type="submit" disabled={busy || !credentialForm.providerName || !credentialForm.displayName}>{busy ? "Saving" : credentialForm.id ? "Update" : "Create"}</button>
            {credentialForm.id ? <button type="button" className="danger-button" onClick={removeCredential} disabled={busy}>Delete</button> : null}
            <button type="button" className="secondary-button" onClick={() => setCredentialForm(emptyCredential)}>New</button>
          </div>
        </form>
      )}
    </section>
  );
}

type MappingColumnKey = "provider" | "source" | "type" | "market" | "name" | "status" | "verified";
type CredentialColumnKey = "type" | "provider" | "name" | "secret" | "status" | "active";

const mappingColumns: Array<SortColumn<ProviderSymbolMapping, MappingColumnKey>> = [
  { key: "provider", label: "Provider", value: (row) => row.providerType },
  { key: "source", label: "Source", value: (row) => row.sourceSymbol },
  { key: "type", label: "Type", value: (row) => row.sourceSymbolType },
  { key: "market", label: "Market", value: (row) => `${row.marketProvider}:${row.marketSymbol}` },
  { key: "name", label: "Name", value: (row) => row.instrumentName },
  { key: "status", label: "Status", value: (row) => row.enabled },
  { key: "verified", label: "Verified", value: (row) => row.verified }
];

const credentialColumns: Array<SortColumn<SystemApiCredential, CredentialColumnKey>> = [
  { key: "type", label: "Type", value: (row) => row.credentialType },
  { key: "provider", label: "Provider", value: (row) => row.providerName },
  { key: "name", label: "Name", value: (row) => row.displayName },
  { key: "secret", label: "Secret", value: (row) => row.maskedSecret },
  { key: "status", label: "Status", value: (row) => row.enabled },
  { key: "active", label: "Active", value: (row) => row.active }
];
