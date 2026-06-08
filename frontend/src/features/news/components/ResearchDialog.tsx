"use client";

import { Fragment, useState, type ReactNode } from "react";
import { fetchNewsResearchStatus, startNewsResearch } from "../api";
import type { InstrumentType, NewsResearchLayer, NewsResearchResponse } from "../types";

type Props = {
  token: string;
  instrumentType: InstrumentType;
  symbol: string;
  instrumentName: string;
  onClose: () => void;
};

export function ResearchDialog({ token, instrumentType, symbol, instrumentName, onClose }: Props) {
  const [layer, setLayer] = useState<NewsResearchLayer>("COMPLETE_RESEARCH");
  const [research, setResearch] = useState<NewsResearchResponse | null>(null);
  const [researching, setResearching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [maximized, setMaximized] = useState(false);

  async function analyze() {
    setResearching(true);
    setError(null);
    try {
      let status = await startNewsResearch(token, instrumentType, symbol, layer);
      setResearch(status);
      while (status.running) {
        await sleep(1500);
        status = await fetchNewsResearchStatus(token, instrumentType, symbol, layer);
        setResearch(status);
      }
      if (status.error) {
        setError(status.error);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not analyze instrument");
    } finally {
      setResearching(false);
    }
  }

  return (
    <div className="modal-backdrop news-modal-backdrop" role="presentation" onClick={onClose}>
      <section className={`news-dialog${maximized ? " maximized" : ""}`} role="dialog" aria-modal="true" aria-labelledby="research-title" onClick={(event) => event.stopPropagation()}>
        <header className="chart-dialog-header">
          <div>
            <h2 id="research-title">Research: {symbol} - {instrumentName}</h2>
            <p>Live web research with OpenAI</p>
          </div>
          <div className="chart-dialog-actions">
            <button
              type="button"
              className="icon-button dialog-size-button"
              onClick={() => setMaximized((current) => !current)}
              aria-label={maximized ? "Restore research dialog" : "Maximize research dialog"}
              title={maximized ? "Restore" : "Maximize"}
            >
              <span className={maximized ? "restore-dialog-icon" : "maximize-dialog-icon"} aria-hidden="true" />
            </button>
            <button type="button" className="icon-button" onClick={onClose} aria-label="Close research">x</button>
          </div>
        </header>

        <div className="news-research-toolbar">
          <label>
            <span>Research layer</span>
            <select value={layer} onChange={(event) => setLayer(event.target.value as NewsResearchLayer)} disabled={researching}>
              <option value="CURRENT_SITUATION">Current Situation</option>
              <option value="FUNDAMENTAL_ANALYSIS">Fundamental Analysis</option>
              <option value="SCENARIO_ANALYSIS">Scenario Analysis</option>
              <option value="COMPLETE_RESEARCH">Complete Research</option>
            </select>
          </label>
          <button type="button" className="primary-button" onClick={analyze} disabled={researching}>
            {researching ? "Analysing" : "Run analysis"}
          </button>
        </div>

        {error ? <p className="news-error">{error}</p> : null}
        <section className="news-research">
          {researching ? <div className="research-progress"><span /> Searching and analysing current web sources</div> : null}
          {!researching && !research?.content && !research?.error ? (
            <div className="news-empty">Select a research layer and run analysis.</div>
          ) : null}
          {research?.content ? (
            <article className="research-answer">
              <header>
                <span>AI web research</span>
                <strong>{researchLayerLabel(research.layer)}</strong>
                {research.generatedAt ? <small>Generated {formatDate(research.generatedAt)}</small> : null}
              </header>
              <MarkdownContent content={research.content} />
              {research.sources.length > 0 ? (
                <section className="research-sources">
                  <h3>Sources consulted</h3>
                  <ol>
                    {research.sources.map((source) => (
                      <li key={source.url}>
                        <a href={source.url} target="_blank" rel="noopener noreferrer">{source.title}</a>
                      </li>
                    ))}
                  </ol>
                </section>
              ) : null}
            </article>
          ) : null}
        </section>
      </section>
    </div>
  );
}

function researchLayerLabel(layer: NewsResearchLayer): string {
  return {
    CURRENT_SITUATION: "Current Situation",
    FUNDAMENTAL_ANALYSIS: "Fundamental Analysis",
    SCENARIO_ANALYSIS: "Scenario Analysis",
    COMPLETE_RESEARCH: "Complete Research"
  }[layer];
}

function MarkdownContent({ content }: { content: string }) {
  const lines = content.split(/\r?\n/);
  return <div className="research-markdown">{lines.map((line, index) => renderMarkdownLine(line, index))}</div>;
}

function renderMarkdownLine(line: string, key: number): ReactNode {
  const value = line.trim();
  if (!value) return <div className="research-spacer" key={key} />;
  if (value.startsWith("### ")) return <h4 key={key}>{inlineMarkdown(value.slice(4))}</h4>;
  if (value.startsWith("## ")) return <h3 key={key}>{inlineMarkdown(value.slice(3))}</h3>;
  if (value.startsWith("# ")) return <h2 key={key}>{inlineMarkdown(value.slice(2))}</h2>;
  if (/^[-*]\s/.test(value)) return <div className="research-list-item" key={key}><span>•</span><p>{inlineMarkdown(value.slice(2))}</p></div>;
  const numbered = value.match(/^(\d+)\.\s+(.*)$/);
  if (numbered) return <div className="research-list-item numbered" key={key}><span>{numbered[1]}.</span><p>{inlineMarkdown(numbered[2])}</p></div>;
  return <p key={key}>{inlineMarkdown(value)}</p>;
}

function inlineMarkdown(value: string): ReactNode {
  return value.split(/(\*\*[^*]+\*\*|\[[^\]]+\]\(https?:\/\/[^)]+\))/g).map((part, index) => {
    if (part.startsWith("**") && part.endsWith("**")) {
      return <strong key={index}>{part.slice(2, -2)}</strong>;
    }
    const link = part.match(/^\[([^\]]+)\]\((https?:\/\/[^)]+)\)$/);
    if (link) {
      return <a key={index} href={link[2]} target="_blank" rel="noopener noreferrer">{link[1]}</a>;
    }
    return <Fragment key={index}>{part}</Fragment>;
  });
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function sleep(milliseconds: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, milliseconds));
}
