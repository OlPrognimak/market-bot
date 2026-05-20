import { formatDateTime, formatPercent } from "@/lib/format";
import type { MarketDashboardSnapshot, MarketScanResult } from "../types/market-dashboard";

type Props = {
  snapshot: MarketDashboardSnapshot | null;
};

export function SummaryStrip({ snapshot }: Props) {
  return (
    <section className="summary-strip" aria-label="Market movement summary">
      <SummaryPanel title="Top Rolling +" result={snapshot?.topPositiveRolling} valueKey="rollingDelta" tone="positive" />
      <SummaryPanel title="Top Rolling -" result={snapshot?.topNegativeRolling} valueKey="rollingDelta" tone="negative" />
      <SummaryPanel title="Top Delta +" result={snapshot?.topPositiveDelta} valueKey="delta" tone="positive" />
      <SummaryPanel title="Top Delta -" result={snapshot?.topNegativeDelta} valueKey="delta" tone="negative" />
      <div className="summary-panel">
        <span className="panel-title">Last Scan</span>
        <strong>{formatDateTime(snapshot?.lastScanAt)}</strong>
        <span className="panel-subtitle">{snapshot?.results.length ?? 0} symbols</span>
      </div>
    </section>
  );
}

function SummaryPanel({
  title,
  result,
  valueKey,
  tone
}: {
  title: string;
  result?: MarketScanResult | null;
  valueKey: "rollingDelta" | "delta";
  tone: "positive" | "negative";
}) {
  return (
    <div className={`summary-panel ${tone}`}>
      <span className="panel-title">{title}</span>
      <strong>{result ? formatPercent(result[valueKey]) : "No data"}</strong>
      <span className="panel-subtitle">{result ? `${result.companyName} (${result.symbol})` : "Waiting for scan"}</span>
    </div>
  );
}
