"use client";

import { ChangeEvent, useEffect, useState } from "react";
import { fetchPortfolioImports, uploadPortfolioCsv } from "../api";
import type { PortfolioImport } from "../types";

export function ImportDataPage({ token }: { token: string }) {
  const [imports, setImports] = useState<PortfolioImport[]>([]);
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = async () => setImports(await fetchPortfolioImports(token));

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
        results.push(await uploadPortfolioCsv(token, file));
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
          <p>Upload Revolut all-transactions and gain/loss CSV exports. Existing records are skipped.</p>
        </div>
      </header>

      <section className="portfolio-upload-panel">
        <div>
          <strong>Provider type: Revolut</strong>
          <p>Select one or both Revolut CSV exports. Files are detected by their headers.</p>
        </div>
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
              <th>Imported</th>
              <th>Provider</th>
              <th>Schema</th>
              <th>File</th>
              <th>Total</th>
              <th>New</th>
              <th>Skipped</th>
            </tr>
          </thead>
          <tbody>
            {imports.map((item) => (
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
