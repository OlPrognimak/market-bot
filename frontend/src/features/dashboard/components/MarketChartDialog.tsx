"use client";

import { useEffect, useRef, useState } from "react";
import { backendHttpUrl } from "@/lib/websocket";
import { formatPercent, formatPrice } from "@/lib/format";
import type { MarketChartPoint, MarketChartRange, MarketChartResponse, MarketScanResult } from "../types/market-dashboard";

type Props = {
  result: MarketScanResult | null;
  onClose: () => void;
};

const ranges: MarketChartRange[] = ["today", "week", "month", "year"];

export function MarketChartDialog({ result, onClose }: Props) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [range, setRange] = useState<MarketChartRange>("today");
  const [chart, setChart] = useState<MarketChartResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!result) {
      return;
    }

    setRange("today");
  }, [result?.symbol]);

  useEffect(() => {
    if (!result) {
      return;
    }

    const selectedResult = result;
    let cancelled = false;

    async function loadChart() {
      setLoading(true);
      setError(null);
      try {
        const response = await fetch(`${backendHttpUrl()}/api/dashboard/chart/${encodeURIComponent(selectedResult.symbol)}?range=${range}`);
        if (!response.ok) {
          throw new Error(`Chart request failed with ${response.status}`);
        }
        const payload = (await response.json()) as MarketChartResponse;
        if (!cancelled) {
          setChart(payload);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Could not load chart");
          setChart(null);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    loadChart();

    return () => {
      cancelled = true;
    };
  }, [range, result?.symbol, result?.updatedAt]);

  useEffect(() => {
    if (!result || !chart) {
      return;
    }

    drawChart(canvasRef.current, chart.points);
  }, [chart, result]);

  if (!result) {
    return null;
  }

  const points = chart?.points ?? [];
  const latest = points.at(-1);

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="chart-dialog" role="dialog" aria-modal="true" aria-labelledby="chart-title" onClick={(event) => event.stopPropagation()}>
        <header className="chart-dialog-header">
          <div>
            <h2 id="chart-title">{result.symbol} - {result.companyName}</h2>
            <p>{result.region ?? "Market"} | {result.exchange ?? "Exchange"} | {result.currency ?? "Currency"}</p>
            {chart ? (
              <p>
                {chartLabel(chart)} | {chartDateRange(chart)}
              </p>
            ) : null}
          </div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close chart">x</button>
        </header>

        <div className="range-tabs" role="tablist" aria-label="Chart range">
          {ranges.map((item) => (
            <button key={item} type="button" className={item === range ? "active" : ""} onClick={() => setRange(item)}>
              {labelRange(item)}
            </button>
          ))}
        </div>

        <div className="chart-panel">
          <canvas ref={canvasRef} width={960} height={420} />
          {loading ? <div className="chart-overlay">Loading chart</div> : null}
          {!loading && error ? <div className="chart-overlay error">{error}</div> : null}
          {!loading && !error && points.length === 0 ? <div className="chart-overlay">No saved data for this range</div> : null}
        </div>

        <div className="chart-stats">
          <span>Price: {latest ? formatPrice(latest.price) : formatPrice(result.currentPrice)}</span>
          <span>
            Day:{" "}
            <span className={toneClass(latest ? latest.percentChange : result.currentPercent)}>
              {latest ? formatPercent(latest.percentChange) : formatPercent(result.currentPercent)}
            </span>
          </span>
          <span>Range: {latest ? `${formatPrice(latest.low)} - ${formatPrice(latest.high)}` : `${formatPrice(result.low)} - ${formatPrice(result.high)}`}</span>
          <span>Previous close: {latest ? formatPrice(latest.previousClose) : formatPrice(result.previousClose)}</span>
          {chart ? <span>Dates: {chartDateRange(chart)}</span> : null}
        </div>
      </section>
    </div>
  );
}

function labelRange(range: MarketChartRange): string {
  return range.charAt(0).toUpperCase() + range.slice(1);
}

function chartLabel(chart: MarketChartResponse): string {
  if (chart.range === "today" && chart.fallback) {
    return "Last active trading day";
  }

  return "Showing requested range";
}

function chartDateRange(chart: MarketChartResponse): string {
  if (!chart.actualFrom || !chart.actualTo) {
    return "No saved dates";
  }

  const from = formatChartDate(chart.actualFrom);
  const to = formatChartDate(chart.actualTo);
  return from === to ? from : `${from} - ${to}`;
}

function formatChartDate(value: string): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "2-digit",
    year: "numeric"
  }).format(new Date(value));
}

function toneClass(value: number): string {
  if (value > 0) {
    return "value-positive";
  }
  if (value < 0) {
    return "value-negative";
  }
  return "value-neutral";
}

function drawChart(canvas: HTMLCanvasElement | null, points: MarketChartPoint[]) {
  if (!canvas) {
    return;
  }

  const context = canvas.getContext("2d");
  if (!context) {
    return;
  }

  const rect = canvas.getBoundingClientRect();
  const ratio = window.devicePixelRatio || 1;
  canvas.width = Math.max(1, Math.floor(rect.width * ratio));
  canvas.height = Math.max(1, Math.floor(rect.height * ratio));
  context.scale(ratio, ratio);

  const width = rect.width;
  const height = rect.height;
  context.clearRect(0, 0, width, height);

  if (points.length === 0) {
    return;
  }

  const padding = { top: 18, right: 58, bottom: 36, left: 72 };
  const values = points.map((point) => point.percentChange);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const rawMin = Math.min(min, 0);
  const rawMax = Math.max(max, 0);
  const span = Math.max(rawMax - rawMin, 0.01);
  const yMin = rawMin - span * 0.15;
  const yMax = rawMax + span * 0.15;
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;
  const latestPercent = values.at(-1)!;
  const lineColor = latestPercent >= 0 ? "#147a46" : "#b42318";

  context.font = "12px Arial, sans-serif";
  context.lineWidth = 1;
  context.strokeStyle = "#d9e0e6";
  context.fillStyle = "#5f6f7d";

  for (let index = 0; index <= 4; index += 1) {
    const y = padding.top + (plotHeight / 4) * index;
    context.beginPath();
    context.moveTo(padding.left, y);
    context.lineTo(width - padding.right, y);
    context.stroke();
    const label = yMax - ((yMax - yMin) / 4) * index;
    context.fillText(formatPercent(label), width - padding.right + 8, y + 4);
  }

  const zeroY = padding.top + plotHeight - ((0 - yMin) / (yMax - yMin)) * plotHeight;
  context.beginPath();
  context.moveTo(padding.left, zeroY);
  context.lineTo(width - padding.right, zeroY);
  context.strokeStyle = "#4f5f6b";
  context.lineWidth = 2.5;
  context.stroke();
  context.fillStyle = "#687783";
  context.fillText("0.00%", 8, zeroY + 4);

  context.strokeStyle = lineColor;
  context.lineWidth = 2;
  context.beginPath();

  points.forEach((point, index) => {
    const x = padding.left + (points.length === 1 ? plotWidth / 2 : (plotWidth / (points.length - 1)) * index);
    const y = padding.top + plotHeight - ((point.percentChange - yMin) / (yMax - yMin)) * plotHeight;
    if (index === 0) {
      context.moveTo(x, y);
    } else {
      context.lineTo(x, y);
    }
  });
  context.stroke();

  context.fillStyle = lineColor;
  points.forEach((point, index) => {
    const x = padding.left + (points.length === 1 ? plotWidth / 2 : (plotWidth / (points.length - 1)) * index);
    const y = padding.top + plotHeight - ((point.percentChange - yMin) / (yMax - yMin)) * plotHeight;
    context.beginPath();
    context.arc(x, y, 3, 0, Math.PI * 2);
    context.fill();
  });

  context.fillStyle = "#5f6f7d";
  const first = new Date(points[0].time).toLocaleString();
  const last = new Date(points[points.length - 1].time).toLocaleString();
  context.fillText(first, padding.left, height - 10);
  context.fillText(last, width - padding.right - context.measureText(last).width, height - 10);
}
