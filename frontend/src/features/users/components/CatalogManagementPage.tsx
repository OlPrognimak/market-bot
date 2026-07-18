"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  deleteCryptoCatalogItem,
  deleteStockCatalogItem,
  fetchCryptoCatalog,
  fetchStockCatalog,
  saveCryptoCatalogItem,
  saveStockCatalogItem
} from "@/lib/auth";
import { ALL_FILTER_VALUE } from "@/lib/filterConstants";
import { nextSort, sortButtonLabel, sortRows, type SortColumn, type SortState } from "@/lib/tableSort";
import type { CatalogItemPayload, CryptoCatalogItem, StockCatalogItem } from "../types";

type Props = {
  token: string;
};

type CatalogType = "stocks" | "crypto";
type StatusFilter = typeof ALL_FILTER_VALUE | "ENABLED" | "DISABLED";

type FormState = CatalogItemPayload & { id?: number };
type CatalogFilters = {
  symbol: string;
  name: string;
  region: string;
  sector: string;
  exchange: string;
  currency: string;
  priority: string;
  pairSymbol: string;
  status: StatusFilter;
};

const emptyForm: FormState = {
  symbol: "",
  name: "",
  region: "",
  sector: "",
  exchange: "",
  currency: "",
  enabled: true,
  priority: "NORMAL"
};

const emptyFilters: CatalogFilters = {
  symbol: "",
  name: "",
  region: "",
  sector: "",
  exchange: "",
  currency: "",
  priority: "",
  pairSymbol: "",
  status: ALL_FILTER_VALUE
};

const containsFilter = (value: string | null | undefined, filter: string) => {
  const normalizedFilter = filter.trim().toLowerCase();
  if (!normalizedFilter) {
    return true;
  }
  return (value ?? "").toLowerCase().includes(normalizedFilter);
};

const matchesStatus = (enabled: boolean, status: StatusFilter) => {
  if (status === "ENABLED") {
    return enabled;
  }
  if (status === "DISABLED") {
    return !enabled;
  }
  return true;
};

export function CatalogManagementPage({ token }: Props) {
  const [catalogType, setCatalogType] = useState<CatalogType>("stocks");
  const [stocks, setStocks] = useState<StockCatalogItem[]>([]);
  const [crypto, setCrypto] = useState<CryptoCatalogItem[]>([]);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState<CatalogFilters>(emptyFilters);
  const [stockSort, setStockSort] = useState<SortState<StockCatalogColumnKey>>({ key: "symbol", direction: "asc" });
  const [cryptoSort, setCryptoSort] = useState<SortState<CryptoCatalogColumnKey>>({ key: "symbol", direction: "asc" });

  const rows = catalogType === "stocks" ? stocks : crypto;
  const filteredRows = useMemo(() => {
    const filtered = rows.filter((row) => {
      if (!matchesStatus(row.enabled, filters.status)) {
        return false;
      }

      if (catalogType === "stocks") {
        const stock = row as StockCatalogItem;
        return containsFilter(stock.symbol, filters.symbol)
          && containsFilter(stock.name, filters.name)
          && containsFilter(stock.region, filters.region)
          && containsFilter(stock.sector, filters.sector)
          && containsFilter(stock.exchange, filters.exchange)
          && containsFilter(stock.currency, filters.currency)
          && containsFilter(stock.priority, filters.priority);
      }

      const coin = row as CryptoCatalogItem;
      return containsFilter(coin.symbol, filters.symbol)
        && containsFilter(coin.name, filters.name)
        && containsFilter(coin.pairSymbol, filters.pairSymbol);
    });
    return catalogType === "stocks"
      ? sortRows(filtered as StockCatalogItem[], stockSort, stockCatalogColumns)
      : sortRows(filtered as CryptoCatalogItem[], cryptoSort, cryptoCatalogColumns);
  }, [catalogType, cryptoSort, filters, rows, stockSort]);

  const load = async () => {
    setError(null);
    const [stockRows, cryptoRows] = await Promise.all([
      fetchStockCatalog(token),
      fetchCryptoCatalog(token)
    ]);
    setStocks(stockRows);
    setCrypto(cryptoRows);
  };

  useEffect(() => {
    load().catch((err) => setError(err instanceof Error ? err.message : "Could not load catalog"));
  }, [token]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (catalogType === "stocks") {
        await saveStockCatalogItem(token, form, form.id);
      } else {
        await saveCryptoCatalogItem(token, form, form.id);
      }
      setForm(emptyForm);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save catalog item");
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!form.id) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      if (catalogType === "stocks") {
        await deleteStockCatalogItem(token, form.id);
      } else {
        await deleteCryptoCatalogItem(token, form.id);
      }
      setForm(emptyForm);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete catalog item");
    } finally {
      setBusy(false);
    }
  };

  const selectRow = (row: StockCatalogItem | CryptoCatalogItem) => {
    setForm({
      id: row.id,
      symbol: row.symbol,
      name: row.name,
      region: "region" in row ? row.region ?? "" : "",
      sector: "sector" in row ? row.sector ?? "" : "",
      exchange: "exchange" in row ? row.exchange ?? "" : "",
      currency: "currency" in row ? row.currency ?? "" : "",
      enabled: row.enabled,
      priority: "priority" in row ? row.priority : "NORMAL"
    });
  };

  const updateFilter = (name: keyof CatalogFilters, value: string) => {
    setFilters((current) => ({ ...current, [name]: value }));
  };

  const tableColSpan = catalogType === "stocks" ? 8 : 4;

  return (
    <section className="users-layout catalog-page-layout">
      <div className="users-list">
        <div className="section-header">
          <h2>{catalogType === "stocks" ? "Shares Catalog" : "Crypto Catalog"}</h2>
          <div className="header-actions">
            <button type="button" className={catalogType === "stocks" ? "" : "secondary-button"} onClick={() => {
              setCatalogType("stocks");
              setForm(emptyForm);
              setFilters(emptyFilters);
            }}>Shares</button>
            <button type="button" className={catalogType === "crypto" ? "" : "secondary-button"} onClick={() => {
              setCatalogType("crypto");
              setForm(emptyForm);
              setFilters(emptyFilters);
            }}>Crypto</button>
          </div>
        </div>
        <section className="toolbar catalog-toolbar" aria-label="Catalog filters">
          <input
            value={filters.symbol}
            onChange={(event) => updateFilter("symbol", event.target.value)}
            placeholder="Symbol"
            aria-label="Filter catalog symbol"
          />
          <input
            value={filters.name}
            onChange={(event) => updateFilter("name", event.target.value)}
            placeholder="Name"
            aria-label="Filter catalog name"
          />
          {catalogType === "stocks" ? (
            <>
              <input value={filters.region} onChange={(event) => updateFilter("region", event.target.value)} placeholder="Region" aria-label="Filter catalog region" />
              <input value={filters.sector} onChange={(event) => updateFilter("sector", event.target.value)} placeholder="Sector" aria-label="Filter catalog sector" />
              <input value={filters.exchange} onChange={(event) => updateFilter("exchange", event.target.value)} placeholder="Exchange" aria-label="Filter catalog exchange" />
              <input value={filters.currency} onChange={(event) => updateFilter("currency", event.target.value)} placeholder="Currency" aria-label="Filter catalog currency" />
              <input value={filters.priority} onChange={(event) => updateFilter("priority", event.target.value)} placeholder="Priority" aria-label="Filter catalog priority" />
            </>
          ) : (
            <input value={filters.pairSymbol} onChange={(event) => updateFilter("pairSymbol", event.target.value)} placeholder="Pair" aria-label="Filter catalog pair" />
          )}
          <select value={filters.status} onChange={(event) => updateFilter("status", event.target.value as StatusFilter)} aria-label="Filter catalog status">
            <option value={ALL_FILTER_VALUE}>All statuses</option>
            <option value="ENABLED">Enabled</option>
            <option value="DISABLED">Disabled</option>
          </select>
        </section>
        <div className="table-frame catalog-table-frame">
          <table className={`users-table ${catalogType === "stocks" ? "stock-catalog-table" : "crypto-catalog-table"}`}>
            <thead>
              <tr>
                {(catalogType === "stocks" ? stockCatalogColumns : cryptoCatalogColumns).map((column) => (
                  <th key={column.key}>
                    <button
                      type="button"
                      className="sortable-header"
                      onClick={() => catalogType === "stocks"
                        ? setStockSort((current) => nextSort(current, column.key as StockCatalogColumnKey))
                        : setCryptoSort((current) => nextSort(current, column.key as CryptoCatalogColumnKey))}
                    >
                      {catalogType === "stocks"
                        ? sortButtonLabel(stockSort, column.key as StockCatalogColumnKey, column.label)
                        : sortButtonLabel(cryptoSort, column.key as CryptoCatalogColumnKey, column.label)}
                    </button>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filteredRows.map((row) => (
                <tr key={row.id} className={form.id === row.id ? "selected-row" : ""} onClick={() => selectRow(row)}>
                  <td>{row.symbol}</td>
                  <td>{row.name}</td>
                  {catalogType === "stocks" ? (
                    <>
                      <td>{(row as StockCatalogItem).region || "-"}</td>
                      <td>{(row as StockCatalogItem).sector || "-"}</td>
                      <td>{(row as StockCatalogItem).exchange || "-"}</td>
                      <td>{(row as StockCatalogItem).currency || "-"}</td>
                      <td>{(row as StockCatalogItem).priority || "-"}</td>
                    </>
                  ) : (
                    <td>{(row as CryptoCatalogItem).pairSymbol}</td>
                  )}
                  <td>{row.enabled ? "Enabled" : "Disabled"}</td>
                </tr>
              ))}
              {filteredRows.length === 0 ? (
                <tr>
                  <td colSpan={tableColSpan}>No catalog items match the current filters.</td>
                </tr>
              ) : null}
            </tbody>
          </table>
        </div>
      </div>

      <form className="user-form" onSubmit={submit}>
        <h2>{form.id ? "Update Catalog Item" : "Create Catalog Item"}</h2>
        <label>
          Symbol
          <input value={form.symbol} onChange={(event) => setForm({ ...form, symbol: event.target.value.toUpperCase() })} required />
        </label>
        <label>
          Name
          <input value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required={catalogType === "crypto"} />
        </label>
        {catalogType === "stocks" ? (
          <>
            <label>Region<input value={form.region ?? ""} onChange={(event) => setForm({ ...form, region: event.target.value })} /></label>
            <label>Sector<input value={form.sector ?? ""} onChange={(event) => setForm({ ...form, sector: event.target.value })} /></label>
            <label>Exchange<input value={form.exchange ?? ""} onChange={(event) => setForm({ ...form, exchange: event.target.value })} /></label>
            <label>Currency<input value={form.currency ?? ""} onChange={(event) => setForm({ ...form, currency: event.target.value })} /></label>
            <label>
              Priority
              <select value={form.priority ?? "NORMAL"} onChange={(event) => setForm({ ...form, priority: event.target.value })}>
                <option value="LOW">LOW</option>
                <option value="NORMAL">NORMAL</option>
                <option value="HIGH">HIGH</option>
              </select>
            </label>
          </>
        ) : null}
        <label className="checkbox-label">
          <input checked={form.enabled !== false} onChange={(event) => setForm({ ...form, enabled: event.target.checked })} type="checkbox" />
          Enabled
        </label>
        {error ? <div className="error-banner">{error}</div> : null}
        <div className="form-actions">
          <button type="submit" disabled={busy || !form.symbol || (catalogType === "crypto" && !form.name)}>{busy ? "Saving" : form.id ? "Update" : "Create"}</button>
          {form.id ? <button type="button" className="danger-button" onClick={remove} disabled={busy}>Delete</button> : null}
          <button type="button" className="secondary-button" onClick={() => setForm(emptyForm)}>New</button>
        </div>
      </form>
    </section>
  );
}

type StockCatalogColumnKey = "symbol" | "name" | "region" | "sector" | "exchange" | "currency" | "priority" | "status";
type CryptoCatalogColumnKey = "symbol" | "name" | "pair" | "status";

const stockCatalogColumns: Array<SortColumn<StockCatalogItem, StockCatalogColumnKey>> = [
  { key: "symbol", label: "Symbol", value: (row) => row.symbol },
  { key: "name", label: "Name", value: (row) => row.name },
  { key: "region", label: "Region", value: (row) => row.region },
  { key: "sector", label: "Sector", value: (row) => row.sector },
  { key: "exchange", label: "Exchange", value: (row) => row.exchange },
  { key: "currency", label: "Currency", value: (row) => row.currency },
  { key: "priority", label: "Priority", value: (row) => row.priority },
  { key: "status", label: "Status", value: (row) => row.enabled }
];

const cryptoCatalogColumns: Array<SortColumn<CryptoCatalogItem, CryptoCatalogColumnKey>> = [
  { key: "symbol", label: "Symbol", value: (row) => row.symbol },
  { key: "name", label: "Name", value: (row) => row.name },
  { key: "pair", label: "Pair", value: (row) => row.pairSymbol },
  { key: "status", label: "Status", value: (row) => row.enabled }
];
