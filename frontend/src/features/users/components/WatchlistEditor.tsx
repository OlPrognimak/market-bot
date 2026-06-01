"use client";

import { Fragment, useEffect, useMemo, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import { fetchWatchlistCatalog, validateWatchlistSymbol } from "@/lib/auth";
import type { AppUser, UserProperty, WatchlistCatalogItem } from "../types";

export type WatchlistRow = {
  id?: number;
  propertyName: string;
  propertyValue: string;
  enabled: boolean;
};

type Props = {
  rows: WatchlistRow[];
  setRows: Dispatch<SetStateAction<WatchlistRow[]>>;
  addLabel?: string;
  token?: string;
  catalogType?: "stocks" | "crypto";
  pickerLabel?: string;
  onValidationStateChange?: (invalid: boolean) => void;
};

type PropertyRowsOptions = {
  propertyType?: UserProperty["propertyType"];
  description?: string;
};

export function WatchlistEditor({
  rows,
  setRows,
  addLabel = "Add Manually",
  token,
  catalogType,
  pickerLabel = "Symbol",
  onValidationStateChange
}: Props) {
  const [catalog, setCatalog] = useState<WatchlistCatalogItem[]>([]);
  const [catalogLoaded, setCatalogLoaded] = useState(false);
  const [catalogLoading, setCatalogLoading] = useState(false);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [pickerMode, setPickerMode] = useState<{ type: "add" } | { type: "edit"; index: number } | null>(null);
  const [validationErrors, setValidationErrors] = useState<Record<number, string>>({});
  const [validatingRows, setValidatingRows] = useState<Record<number, boolean>>({});
  const [pendingValidationRows, setPendingValidationRows] = useState<Record<number, boolean>>({});

  const selectedSymbols = useMemo(
    () => new Set(rows.map((row) => normalizeSymbol(row.propertyName)).filter(Boolean)),
    [rows]
  );
  const availableCatalog = useMemo(
    () => catalog.filter((item) => !selectedSymbols.has(normalizeSymbol(item.symbol))),
    [catalog, selectedSymbols]
  );

  const validationInvalid = Object.keys(validationErrors).length > 0
    || Object.keys(validatingRows).length > 0
    || Object.keys(pendingValidationRows).length > 0;

  useEffect(() => {
    onValidationStateChange?.(validationInvalid);
  }, [onValidationStateChange, validationInvalid]);

  const clearValidationError = (index: number) => {
    setValidationErrors((current) => {
      if (!current[index]) {
        return current;
      }
      const next = { ...current };
      delete next[index];
      return next;
    });
  };

  const clearPendingValidation = (index: number) => {
    setPendingValidationRows((current) => {
      if (!current[index]) {
        return current;
      }
      const next = { ...current };
      delete next[index];
      return next;
    });
  };

  const markPendingValidation = (index: number, symbol: string) => {
    if (!token || !catalogType || !normalizeSymbol(symbol)) {
      clearPendingValidation(index);
      return;
    }
    setPendingValidationRows((current) => ({ ...current, [index]: true }));
  };

  const openPicker = async (mode: { type: "add" } | { type: "edit"; index: number }) => {
    if (!token || !catalogType) {
      return;
    }
    if (
      (mode.type === "add" && pickerMode?.type === "add") ||
      (mode.type === "edit" && pickerMode?.type === "edit" && pickerMode.index === mode.index)
    ) {
      setPickerMode(null);
      return;
    }
    setPickerMode(mode);
    if (catalogLoaded) {
      return;
    }
    setCatalogError(null);
    setCatalogLoading(true);
    try {
      setCatalog(await fetchWatchlistCatalog(token, catalogType));
      setCatalogLoaded(true);
    } catch (err) {
      setCatalogError(err instanceof Error ? err.message : "Could not load symbols");
    } finally {
      setCatalogLoading(false);
    }
  };

  const selectCatalogItem = (item: WatchlistCatalogItem) => {
    if (!pickerMode) {
      return;
    }
    if (pickerMode.type === "add") {
      addWatchlistRow(setRows, item);
    } else {
      updateWatchlistRow(pickerMode.index, { propertyName: normalizeSymbol(item.symbol), propertyValue: item.name }, setRows);
      clearValidationError(pickerMode.index);
      clearPendingValidation(pickerMode.index);
    }
    setPickerMode(null);
  };

  const validateManualSymbol = async (index: number, symbol: string) => {
    if (!token || !catalogType) {
      return;
    }
    const normalizedSymbol = normalizeSymbol(symbol);
    if (!normalizedSymbol) {
      clearValidationError(index);
      clearPendingValidation(index);
      return;
    }

    clearPendingValidation(index);
    setValidatingRows((current) => ({ ...current, [index]: true }));
    try {
      const result = await validateWatchlistSymbol(token, catalogType, normalizedSymbol);
      if (result.valid) {
        clearValidationError(index);
      } else {
        setValidationErrors((current) => ({ ...current, [index]: result.message || `${normalizedSymbol} was not found` }));
      }
    } catch (err) {
      setValidationErrors((current) => ({
        ...current,
        [index]: err instanceof Error ? err.message : "Could not validate symbol"
      }));
    } finally {
      setValidatingRows((current) => {
        const next = { ...current };
        delete next[index];
        return next;
      });
    }
  };

  useEffect(() => {
    if (!token || !catalogType) {
      return;
    }
    const pendingIndexes = Object.keys(pendingValidationRows).map(Number);
    if (pendingIndexes.length === 0) {
      return;
    }

    const timeout = window.setTimeout(() => {
      pendingIndexes.forEach((index) => validateManualSymbol(index, rows[index]?.propertyName ?? ""));
    }, 650);

    return () => window.clearTimeout(timeout);
  }, [catalogType, pendingValidationRows, rows, token]);

  return (
    <>
      <div className="table-frame settings-table-frame">
        <table className="settings-table">
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Name</th>
              <th>Enabled</th>
              <th>Edit</th>
              <th>Delete</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <Fragment key={row.id ?? `new-${index}`}>
                <tr className={rowClassName(row, pickerMode?.type === "edit" && pickerMode.index === index)}>
                  <td>
                    <input
                      value={row.propertyName}
                      onChange={(event) => {
                        clearValidationError(index);
                        markPendingValidation(index, event.target.value);
                        updateWatchlistRow(index, { propertyName: event.target.value.toUpperCase() }, setRows);
                      }}
                      onBlur={(event) => validateManualSymbol(index, event.target.value)}
                      aria-invalid={Boolean(validationErrors[index])}
                    />
                    {validatingRows[index] ? <div className="field-note">Checking symbol</div> : null}
                    {validationErrors[index] ? <div className="field-error">{validationErrors[index]}</div> : null}
                  </td>
                  <td>
                    <input
                      value={row.propertyValue}
                      onChange={(event) => updateWatchlistRow(index, { propertyValue: event.target.value }, setRows)}
                    />
                  </td>
                  <td>
                    <label className="checkbox-label compact-checkbox">
                      <input
                        checked={row.enabled}
                        onChange={(event) => updateWatchlistRow(index, { enabled: event.target.checked }, setRows)}
                        type="checkbox"
                        aria-label={`Enable ${row.propertyName || "symbol"}`}
                      />
                    </label>
                  </td>
                  <td>
                    {token && catalogType ? (
                      <button
                        type="button"
                        className={`secondary-button compact-action-button ${pickerMode?.type === "edit" && pickerMode.index === index ? "active-toggle-button" : ""}`}
                        onClick={() => openPicker({ type: "edit", index })}
                        aria-expanded={pickerMode?.type === "edit" && pickerMode.index === index}
                      >
                        Edit
                      </button>
                    ) : null}
                  </td>
                  <td>
                    <button
                      type="button"
                      className="icon-button delete-icon-button"
                      onClick={() => {
                        setValidationErrors({});
                        setValidatingRows({});
                        setPendingValidationRows({});
                        removeWatchlistRow(index, setRows);
                      }}
                      aria-label={`Delete ${row.propertyName || "symbol"}`}
                      title="Delete"
                    >
                      <TrashIcon />
                    </button>
                  </td>
                </tr>
                {pickerMode?.type === "edit" && pickerMode.index === index ? (
                  <tr className="symbol-picker-row">
                    <td colSpan={5}>{renderPicker(`Change ${pickerLabel}`)}</td>
                  </tr>
                ) : null}
              </Fragment>
            ))}
            {pickerMode?.type === "add" ? (
              <tr className="symbol-picker-row">
                <td colSpan={5}>{renderPicker(`Add ${pickerLabel}`)}</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
      <div className="form-actions">
        {token && catalogType ? (
          <button type="button" className="secondary-button" onClick={() => openPicker({ type: "add" })}>
            {pickerLabel}
          </button>
        ) : null}
        <button type="button" className="secondary-button" onClick={() => addWatchlistRow(setRows)}>
          {addLabel}
        </button>
      </div>
    </>
  );

  function renderPicker(title: string) {
    return (
      <div className="symbol-picker">
        <div className="symbol-picker-header">
          <strong>{title}</strong>
          <button type="button" className="link-button" onClick={() => setPickerMode(null)}>
            Close
          </button>
        </div>
        {catalogError ? <div className="error-banner">{catalogError}</div> : null}
        {catalogLoading ? <div className="empty-state">Loading symbols</div> : null}
        {!catalogLoading && !catalogError && availableCatalog.length === 0 ? (
          <div className="empty-state">No available symbols</div>
        ) : null}
        <div className="symbol-picker-list">
          {availableCatalog.map((item) => (
            <button key={item.symbol} type="button" onClick={() => selectCatalogItem(item)}>
              <span>{item.symbol}</span>
              <small>{item.name}</small>
            </button>
          ))}
        </div>
      </div>
    );
  }
}

export function watchlistToRows(properties: AppUser["properties"], propertyType: UserProperty["propertyType"] = "WATCHLIST"): WatchlistRow[] {
  return properties
    .filter((item) => item.propertyType === propertyType)
    .map((item) => ({
      id: item.id,
      propertyName: item.propertyName,
      propertyValue: item.propertyValue ?? item.propertyName,
      enabled: item.enabled !== false
    }));
}

export function watchlistProperties(rows: WatchlistRow[], options: PropertyRowsOptions = {}): UserProperty[] {
  const propertyType = options.propertyType ?? "WATCHLIST";
  const description = options.description ?? "Watchlist instrument";

  return rows
    .map((row) => ({
      ...row,
      propertyName: row.propertyName.trim().toUpperCase(),
      propertyValue: row.propertyValue.trim()
    }))
    .filter((row) => row.propertyName)
    .map((row) => ({
      id: row.id,
      propertyType,
      propertyName: row.propertyName,
      propertyValue: row.propertyValue || row.propertyName,
      enabled: row.enabled,
      description,
      propertyValueType: "SYMBOL"
    }));
}

function updateWatchlistRow(
  index: number,
  patch: Partial<WatchlistRow>,
  setRows: Dispatch<SetStateAction<WatchlistRow[]>>
) {
  setRows((rows) => rows.map((row, rowIndex) => rowIndex === index ? { ...row, ...patch } : row));
}

function addWatchlistRow(setRows: Dispatch<SetStateAction<WatchlistRow[]>>, item?: WatchlistCatalogItem) {
  setRows((rows) => [
    ...rows,
    {
      propertyName: item ? normalizeSymbol(item.symbol) : "",
      propertyValue: item?.name ?? "",
      enabled: true
    }
  ]);
}

function removeWatchlistRow(index: number, setRows: Dispatch<SetStateAction<WatchlistRow[]>>) {
  setRows((rows) => rows.filter((_, rowIndex) => rowIndex !== index));
}

function normalizeSymbol(symbol: string) {
  return symbol.trim().toUpperCase();
}

function rowClassName(row: WatchlistRow, selected: boolean) {
  return [
    row.enabled ? "" : "disabled-property-row",
    selected ? "selected-property-row" : ""
  ].filter(Boolean).join(" ");
}

function TrashIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
      <path d="M9 3h6l1 2h4v2H4V5h4l1-2Z" />
      <path d="M6 9h12l-1 12H7L6 9Zm4 2v8h2v-8h-2Zm4 0v8h2v-8h-2Z" />
    </svg>
  );
}
