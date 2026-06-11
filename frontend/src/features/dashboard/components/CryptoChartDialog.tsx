"use client";

import { useEffect, useRef, useState } from "react";
import {
  CandlestickSeries,
  ColorType,
  createChart,
  LineSeries,
  LineStyle,
  LineType,
  type AutoscaleInfo,
  type CandlestickData,
  type IChartApi,
  type ISeriesApi,
  type LineData,
  type Time
} from "lightweight-charts";
import { authFetch } from "@/lib/auth";
import { formatDateTime, formatPercent } from "@/lib/format";
import { NewsInsightDialog } from "@/features/news/components/NewsInsightDialog";
import { ResearchDialog } from "@/features/news/components/ResearchDialog";
import type { CryptoScanResult, MarketCandleResponse, MarketChartRange, MarketChartResponse } from "../types/market-dashboard";

type Props = {
  token: string;
  result: CryptoScanResult | null;
  onClose: () => void;
};

const ranges: MarketChartRange[] = ["today", "yesterday", "week", "month", "year"];
const CHART_TIME_ZONE = "Europe/Berlin";
type ChartMode = "movement" | "candles";

export function CryptoChartDialog({ token, result, onClose }: Props) {
  const chartContainerRef = useRef<HTMLDivElement | null>(null);
  const [range, setRange] = useState<MarketChartRange>("today");
  const [chart, setChart] = useState<MarketChartResponse | null>(null);
  const [candles, setCandles] = useState<MarketCandleResponse | null>(null);
  const [chartMode, setChartMode] = useState<ChartMode>("movement");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [newsOpen, setNewsOpen] = useState(false);
  const [researchOpen, setResearchOpen] = useState(false);

  useEffect(() => {
    if (!result) {
      return;
    }
    setRange("today");
    setChartMode("movement");
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
        const path = chartMode === "candles" ? "candles" : "chart";
        const response = await authFetch(token, `/api/crypto-dashboard/${path}/${encodeURIComponent(selectedResult.symbol)}?range=${range}`);
        if (!cancelled) {
          if (chartMode === "candles") {
            setCandles(await response.json() as MarketCandleResponse);
          } else {
            setChart(await response.json() as MarketChartResponse);
          }
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Could not load crypto chart");
          if (chartMode === "candles") setCandles(null);
          else setChart(null);
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
  }, [chartMode, range, result?.symbol, result?.updatedAt, token]);

  useEffect(() => {
    if (!result || !chartContainerRef.current || (chartMode === "movement" ? !chart : !candles)) {
      return;
    }

    const activeTimeZone = chartMode === "candles" && candles ? candles.timeZone : CHART_TIME_ZONE;
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
        tickMarkFormatter: (time: Time) => formatChartTime(time, activeTimeZone)
      },
      localization: {
        locale: "de-DE",
        timeFormatter: (time: Time) => formatChartTime(time, activeTimeZone)
      },
      crosshair: {
        horzLine: { color: "#687783" },
        vertLine: { color: "#687783" }
      }
    });

    if (chartMode === "candles" && candles) {
      const series = chartApi.addSeries(CandlestickSeries, {
        upColor: "#147a46",
        downColor: "#b42318",
        borderUpColor: "#147a46",
        borderDownColor: "#b42318",
        wickUpColor: "#147a46",
        wickDownColor: "#b42318",
        priceFormat: { type: "price", precision: 8, minMove: 0.00000001 }
      }) as ISeriesApi<"Candlestick", Time>;
      series.setData(toCandleData(candles));
    } else if (chart) {
      const chartPoints = toLineData(chart);
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
          const autoscale = original();
          if (autoscale === null || autoscale.priceRange === null) return null;
          return {
            ...autoscale,
            priceRange: {
              minValue: Math.min(autoscale.priceRange.minValue, 0),
              maxValue: Math.max(autoscale.priceRange.maxValue, 0)
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
    }
    chartApi.timeScale().fitContent();

    return () => destroyChart(chartApi);
  }, [candles, chart, chartMode, result]);

  if (!result) {
    return null;
  }

  const points = chart?.points ?? [];
  const latest = points.at(-1);
  const candlePoints = candles?.points ?? [];
  const latestCandle = candlePoints.at(-1);
  const activePointCount = chartMode === "candles" ? candlePoints.length : points.length;

  return (
    <>
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <section className="chart-dialog" role="dialog" aria-modal="true" aria-labelledby="crypto-chart-title" onClick={(event) => event.stopPropagation()}>
        <header className="chart-dialog-header">
          <div>
            <h2 id="crypto-chart-title">{result.baseAsset} - {result.coinName}</h2>
            <p>{result.symbol} | Binance USDT pair</p>
            {chartMode === "movement" && chart ? <p>{chartDateRange(chart)}</p> : null}
            {chartMode === "candles" && candles ? <p>Binance OHLC candles | {chartDateRange(candles, candles.timeZone)}</p> : null}
          </div>
          <div className="chart-dialog-actions">
            <button type="button" className="secondary-button" onClick={() => setNewsOpen(true)}>News</button>
            <button type="button" className="secondary-button" onClick={() => setResearchOpen(true)}>Analyse</button>
            <button type="button" className="icon-button" onClick={onClose} aria-label="Close chart">x</button>
          </div>
        </header>

        <div className="range-tabs" role="tablist" aria-label="Crypto chart range">
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
          {!loading && !error && activePointCount === 0 ? <div className="chart-overlay">No Binance data for this range</div> : null}
        </div>

        <div className="chart-stats">
          <span>Price: {chartMode === "candles" && latestCandle ? formatCryptoPrice(latestCandle.close) : latest ? formatCryptoPrice(latest.price) : formatCryptoPrice(result.closePrice)}</span>
          {chartMode === "candles" && latestCandle ? (
            <>
              <span>Open: {formatCryptoPrice(latestCandle.open)}</span>
              <span>High: {formatCryptoPrice(latestCandle.high)}</span>
              <span>Low: {formatCryptoPrice(latestCandle.low)}</span>
            </>
          ) : <><span>
            Range move:{" "}
            <span className={toneClass(latest ? latest.percentChange : result.priceChangePercent)}>
              {latest ? formatPercent(latest.percentChange) : formatPercent(result.priceChangePercent)}
            </span>
          </span>
          <span>Open: {latest ? formatCryptoPrice(latest.open) : formatCryptoPrice(result.openPrice)}</span></>}
          <span>Updated: {formatDateTime(result.updatedAt)}</span>
          {chartMode === "movement" && chart ? <span>Dates: {chartDateRange(chart)}</span> : null}
          {chartMode === "candles" && candles ? <span>Dates: {chartDateRange(candles, candles.timeZone)} | Interval: {candles.interval}</span> : null}
        </div>
        <div className="chart-mode-actions">
          <button type="button" className="secondary-button" onClick={() => setChartMode(chartMode === "movement" ? "candles" : "movement")}>
            {chartMode === "movement" ? "Show Candles" : "Show Movement"}
          </button>
        </div>
      </section>
    </div>
    {newsOpen ? (
      <NewsInsightDialog token={token} instrumentType="CRYPTO" symbol={result.baseAsset} instrumentName={result.coinName} onClose={() => setNewsOpen(false)} />
    ) : null}
    {researchOpen ? (
      <ResearchDialog token={token} instrumentType="CRYPTO" symbol={result.baseAsset} instrumentName={result.coinName} onClose={() => setResearchOpen(false)} />
    ) : null}
    </>
  );
}

function labelRange(range: MarketChartRange): string {
  return range.charAt(0).toUpperCase() + range.slice(1);
}

function chartDateRange(chart: Pick<MarketChartResponse, "actualFrom" | "actualTo">, timeZone = CHART_TIME_ZONE): string {
  if (!chart.actualFrom || !chart.actualTo) {
    return "No candle dates";
  }

  const from = formatChartDate(chart.actualFrom, timeZone);
  const to = formatChartDate(chart.actualTo, timeZone);
  return from === to ? from : `${from} - ${to}`;
}

function toCandleData(candles: MarketCandleResponse): CandlestickData<Time>[] {
  const pointsByTime = new Map<number, CandlestickData<Time>>();
  candles.points.forEach((point) => {
    const time = Math.floor(new Date(point.time).getTime() / 1000);
    pointsByTime.set(time, { time: time as Time, open: point.open, high: point.high, low: point.low, close: point.close });
  });
  return Array.from(pointsByTime.values()).sort((left, right) => Number(left.time) - Number(right.time));
}

function formatChartDate(value: string, timeZone = CHART_TIME_ZONE): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "2-digit",
    year: "numeric",
    timeZone
  }).format(new Date(value));
}

function formatChartTime(value: Time, timeZone = CHART_TIME_ZONE): string {
  if (typeof value !== "number") {
    return value.toString();
  }

  return new Intl.DateTimeFormat("de-DE", {
    timeZone,
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value * 1000));
}

function formatCryptoPrice(value: number): string {
  if (value >= 1000) {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "USD",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    }).format(value);
  }
  if (value >= 1) {
    return new Intl.NumberFormat("en-US", {
      style: "currency",
      currency: "USD",
      minimumFractionDigits: 2,
      maximumFractionDigits: 6
    }).format(value);
  }
  return `$${value.toFixed(8)}`;
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
