"use client";

import { ChangeEvent, useEffect, useMemo, useState } from "react";
import { nextSort, sortButtonLabel, sortRows, type SortColumn, type SortState } from "@/lib/tableSort";
import { fetchPortfolioImports, uploadPortfolioCsv } from "../api";
import type { PortfolioImport, PortfolioProviderType } from "../types";

export function ImportDataPage({ token }: { token: string }) {
  const [imports, setImports] = useState<PortfolioImport[]>([]);
  const [files, setFiles] = useState<File[]>([]);
  const [providerType, setProviderType] = useState<PortfolioProviderType>("REVOLUT");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sort, setSort] = useState<SortState<ImportColumnKey>>({ key: "imported", direction: "desc" });

  const load = async () => setImports(await fetchPortfolioImports(token));
  const sortedImports = useMemo(() => sortRows(imports, sort, importColumns), [imports, sort]);

  useEffect(() => {
    load().catch((reason) => setError(reason instanceof Error ? reason.message : "Could not load imports"));
  }, [token]);

  const selectFiles = (event: ChangeEvent<HTMLInputElement>) => {
    setFiles(Array.from(event.target.files ?? []));
    setMessage(null);
    setError(null);
  };

  const upload = async () => {
    if (files.length === 0) return;
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const results: PortfolioImport[] = [];
      for (const file of files) {
        results.push(await uploadPortfolioCsv(token, file, providerType));
      }
      const imported = results.reduce((sum, item) => sum + item.importedRows, 0);
      const skipped = results.reduce((sum, item) => sum + item.skippedRows, 0);
      const duplicateFiles = results.filter((item) => item.duplicateFile).length;
      setMessage(`Processed ${results.length} file(s): ${imported} records imported, ${skipped} existing records skipped${duplicateFiles ? `, ${duplicateFiles} duplicate file(s)` : ""}.`);
      setFiles([]);
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not import CSV");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="dashboard">
      <header className="dashboard-header">
        <div>
          <h1>Import data</h1>
          <p>Upload Revolut or Trade Republic CSV exports. Existing records are skipped.</p>
        </div>
      </header>

      <section className="portfolio-upload-panel">
        <div>
          <strong>Provider type</strong>
          <p>Select the provider and one or more CSV exports. Files are detected by their headers.</p>
        </div>
        <select value={providerType} onChange={(event) => setProviderType(event.target.value as PortfolioProviderType)}>
          <option value="REVOLUT">Revolut</option>
          <option value="TRADE_REPUBLIC">Trade Republic</option>
        </select>
        <input type="file" accept=".csv,text/csv" multiple onChange={selectFiles} />
        <button type="button" disabled={busy || files.length === 0} onClick={upload}>
          {busy ? "Importing" : `Import ${files.length || ""} file${files.length === 1 ? "" : "s"}`}
        </button>
      </section>

      {message ? <div className="success-banner">{message}</div> : null}
      {error ? <div className="error-banner">{error}</div> : null}

      <div className="table-frame">
        <table className="portfolio-table">
          <thead>
            <tr>
              {importColumns.map((column) => (
                <th key={column.key}>
                  <button type="button" className="sortable-header" onClick={() => setSort((current) => nextSort(current, column.key))}>
                    {sortButtonLabel(sort, column.key, column.label)}
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sortedImports.map((item) => (
              <tr key={item.id}>
                <td>{item.importedAt ? new Date(item.importedAt).toLocaleString() : "-"}</td>
                <td>{item.providerType}</td>
                <td>{item.schemaType.replaceAll("_", " ")}</td>
                <td>{item.originalFileName}</td>
                <td>{item.totalRows}</td>
                <td>{item.importedRows}</td>
                <td>{item.skippedRows}</td>
              </tr>
            ))}
            {imports.length === 0 ? <tr><td colSpan={7}>No portfolio imports yet.</td></tr> : null}
          </tbody>
        </table>
      </div>
    </main>
  );
}

type ImportColumnKey = "imported" | "provider" | "schema" | "file" | "total" | "new" | "skipped";

const importColumns: Array<SortColumn<PortfolioImport, ImportColumnKey>> = [
  { key: "imported", label: "Imported", value: (row) => row.importedAt },
  { key: "provider", label: "Provider", value: (row) => row.providerType },
  { key: "schema", label: "Schema", value: (row) => row.schemaType },
  { key: "file", label: "File", value: (row) => row.originalFileName },
  { key: "total", label: "Total", value: (row) => row.totalRows },
  { key: "new", label: "New", value: (row) => row.importedRows },
  { key: "skipped", label: "Skipped", value: (row) => row.skippedRows }
];
