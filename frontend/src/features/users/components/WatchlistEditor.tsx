"use client";

import type { Dispatch, SetStateAction } from "react";
import type { AppUser, UserProperty } from "../types";

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
};

type PropertyRowsOptions = {
  propertyType?: UserProperty["propertyType"];
  description?: string;
};

export function WatchlistEditor({ rows, setRows, addLabel = "Add Symbol" }: Props) {
  return (
    <>
      <div className="table-frame settings-table-frame">
        <table className="settings-table">
          <thead>
            <tr>
              <th>Symbol</th>
              <th>Name</th>
              <th>Enabled</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={row.id ?? `new-${index}`} className={row.enabled ? "" : "disabled-property-row"}>
                <td>
                  <input
                    value={row.propertyName}
                    onChange={(event) => updateWatchlistRow(index, { propertyName: event.target.value.toUpperCase() }, setRows)}
                  />
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
                  <button
                    type="button"
                    className="icon-button delete-icon-button"
                    onClick={() => removeWatchlistRow(index, setRows)}
                    aria-label={`Delete ${row.propertyName || "symbol"}`}
                    title="Delete"
                  >
                    <TrashIcon />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="form-actions">
        <button type="button" className="secondary-button" onClick={() => addWatchlistRow(setRows)}>
          {addLabel}
        </button>
      </div>
    </>
  );
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

function addWatchlistRow(setRows: Dispatch<SetStateAction<WatchlistRow[]>>) {
  setRows((rows) => [...rows, { propertyName: "", propertyValue: "", enabled: true }]);
}

function removeWatchlistRow(index: number, setRows: Dispatch<SetStateAction<WatchlistRow[]>>) {
  setRows((rows) => rows.filter((_, rowIndex) => rowIndex !== index));
}

function TrashIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
      <path d="M9 3h6l1 2h4v2H4V5h4l1-2Z" />
      <path d="M6 9h12l-1 12H7L6 9Zm4 2v8h2v-8h-2Zm4 0v8h2v-8h-2Z" />
    </svg>
  );
}
