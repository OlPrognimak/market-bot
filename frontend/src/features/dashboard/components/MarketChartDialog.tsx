"use client";

import { useEffect, useRef, useState } from "react";
import {
  ColorType,
  createChart,
  LineSeries,
  LineStyle,
  LineType,
  type AutoscaleInfo,
  type IChartApi,
  type ISeriesApi,
  type LineData,
  type Time
} from "lightweight-charts";
import { authFetch } from "@/lib/auth";
import { formatPercent, formatPrice } from "@/lib/format";
import { NewsInsightDialog } from "@/features/news/components/NewsInsightDialog";
import { ResearchDialog } from "@/features/news/components/ResearchDialog";
import type { MarketChartRange, MarketChartResponse, MarketScanResult } from "../types/market-dashboard";

type Props = {
  token: string;
  result: MarketScanResult | null;
  onClose: () => void;
};

const ranges: MarketChartRange[] = ["today", "week", "month", "year"];
const CHART_TIME_ZONE = "Europe/Berlin";

export function MarketChartDialog({ token, result, onClose }: Props) {
  const chartContainerRef = useRef<HTMLDivElement | null>(null);
  const [range, setRange] = useState<MarketChartRange>("today");
  const [chart, setChart] = useState<MarketChartResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [newsOpen, setNewsOpen] = useState(false);
  const [researchOpen, setResearchOpen] = useState(false);

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
        const response = await authFetch(token, `/api/dashboard/chart/${encodeURIComponent(selectedResult.symbol)}?range=${range}`);
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
  }, [range, result?.symbol, result?.updatedAt, token]);

  useEffect(() => {
    if (!result || !chart || !chartContainerRef.current) {
      return;
    }

    const chartPoints = toLineData(chart);
    const chartApi = createChart(chartContainerRef.current, {
      autoSize: true,
      layout: {
        background: { type: ColorType.Solid, color: "#ffffff" },
        textColor: "#5f6f7d",
        fontFamily: "Arial, Helvetica, sans-serif"
      },
      grid: {
        vertLines: { color: "#eef2f5" },
        horzLines: { color: "#eef2f5" }
      },
      rightPriceScale: {
        borderColor: "#d9e0e6"
      },
      timeScale: {
        borderColor: "#d9e0e6",
        timeVisible: true,
        secondsVisible: false,
        tickMarkFormatter: (time: Time) => formatChartTime(time)
      },
      localization: {
        locale: "de-DE",
        timeFormatter: (time: Time) => formatChartTime(time)
      },
      crosshair: {
        horzLine: { color: "#687783" },
        vertLine: { color: "#687783" }
      }
    });

    const latestValue = chartPoints.at(-1)?.value ?? 0;
    const lineColor = latestValue >= 0 ? "#147a46" : "#b42318";
    const series = chartApi.addSeries(LineSeries, {
      color: lineColor,
      lineWidth: 2,
      lineType: LineType.Curved,
      pointMarkersVisible: false,
      lastValueVisible: true,
      priceLineVisible: true,
      priceFormat: {
        type: "custom",
        formatter: (value: number) => formatPercent(value)
      },
      autoscaleInfoProvider: (original: () => AutoscaleInfo | null) => {
        const result = original();
        if (result === null || result.priceRange === null) {
          return null;
        }

        return {
          ...result,
          priceRange: {
            minValue: Math.min(result.priceRange.minValue, 0),
            maxValue: Math.max(result.priceRange.maxValue, 0)
          }
        };
      }
    }) as ISeriesApi<"Line", Time>;

    series.setData(chartPoints);
    series.createPriceLine({
      price: 0,
      color: "#4f5f6b",
      lineWidth: 2,
      lineStyle: LineStyle.Solid,
      axisLabelVisible: true,
      title: "0.00%"
    });
    chartApi.timeScale().fitContent();

    return () => {
      destroyChart(chartApi);
    };
  }, [chart, result]);

  if (!result) {
    return null;
  }

  const points = chart?.points ?? [];
  const latest = points.at(-1);

  return (
    <>
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
          <div className="chart-dialog-actions">
            <button type="button" className="secondary-button" onClick={() => setNewsOpen(true)}>News</button>
            <button type="button" className="secondary-button" onClick={() => setResearchOpen(true)}>Analyse</button>
            <button type="button" className="icon-button" onClick={onClose} aria-label="Close chart">x</button>
          </div>
        </header>

        <div className="range-tabs" role="tablist" aria-label="Chart range">
          {ranges.map((item) => (
            <button key={item} type="button" className={item === range ? "active" : ""} onClick={() => setRange(item)}>
              {labelRange(item)}
            </button>
          ))}
        </div>

        <div className="chart-panel">
          <div ref={chartContainerRef} className="lightweight-chart" />
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
    {newsOpen ? (
      <NewsInsightDialog token={token} instrumentType="SHARE" symbol={result.symbol} instrumentName={result.companyName} onClose={() => setNewsOpen(false)} />
    ) : null}
    {researchOpen ? (
      <ResearchDialog token={token} instrumentType="SHARE" symbol={result.symbol} instrumentName={result.companyName} onClose={() => setResearchOpen(false)} />
    ) : null}
    </>
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
    year: "numeric",
    timeZone: CHART_TIME_ZONE
  }).format(new Date(value));
}

function formatChartTime(value: Time): string {
  if (typeof value !== "number") {
    return value.toString();
  }

  return new Intl.DateTimeFormat("de-DE", {
    timeZone: CHART_TIME_ZONE,
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value * 1000));
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

function toLineData(chart: MarketChartResponse): LineData<Time>[] {
  const pointsByTime = new Map<number, LineData<Time>>();

  chart.points.forEach((point) => {
    const time = Math.floor(new Date(point.time).getTime() / 1000);
    pointsByTime.set(time, {
      time: time as Time,
      value: point.percentChange
    });
  });

  return Array.from(pointsByTime.values()).sort((left, right) => Number(left.time) - Number(right.time));
}

function destroyChart(chart: IChartApi) {
  chart.remove();
}
