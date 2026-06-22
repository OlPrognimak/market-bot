"use client";

import { useEffect, useRef, useState } from "react";
import {
  CandlestickSeries,
  ColorType,
  createChart,
  createSeriesMarkers,
  LineSeries,
  LineStyle,
  LineType,
  type AutoscaleInfo,
  type CandlestickData,
  type IChartApi,
  type ISeriesApi,
  type LineData,
  type SeriesMarker,
  type Time
} from "lightweight-charts";
import { authFetch } from "@/lib/auth";
import { formatPercent, formatPrice } from "@/lib/format";
import { NewsInsightDialog } from "@/features/news/components/NewsInsightDialog";
import { ResearchDialog } from "@/features/news/components/ResearchDialog";
import type { MarketCandleResponse, MarketChartRange, MarketChartResponse, MarketScanResult } from "../types/market-dashboard";
import { fetchPortfolioMarkers } from "@/features/portfolio/api";
import type { PortfolioMarkerResponse } from "@/features/portfolio/types";

type Props = {
  token: string;
  result: MarketScanResult | null;
  onClose: () => void;
};

const ranges: MarketChartRange[] = ["today", "week", "month", "year"];
const CHART_TIME_ZONE = "Europe/Berlin";
type ChartMode = "movement" | "candles";
type PortfolioMarker = PortfolioMarkerResponse["markers"][number];

export function MarketChartDialog({ token, result, onClose }: Props) {
  const chartContainerRef = useRef<HTMLDivElement | null>(null);
  const [range, setRange] = useState<MarketChartRange>("today");
  const [chart, setChart] = useState<MarketChartResponse | null>(null);
  const [candles, setCandles] = useState<MarketCandleResponse | null>(null);
  const [chartMode, setChartMode] = useState<ChartMode>("movement");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [newsOpen, setNewsOpen] = useState(false);
  const [researchOpen, setResearchOpen] = useState(false);
  const [portfolioMarkers, setPortfolioMarkers] = useState<PortfolioMarkerResponse | null>(null);
  const [showPortfolioMarkers, setShowPortfolioMarkers] = useState(true);
  const [focusedPortfolioMarker, setFocusedPortfolioMarker] = useState<PortfolioMarker | null>(null);
  const [maximized, setMaximized] = useState(false);
  const [markerError, setMarkerError] = useState<string | null>(null);
  const [hoverTimeLabel, setHoverTimeLabel] = useState<{ text: string; x: number } | null>(null);

  useEffect(() => {
    if (!result) {
      return;
    }

    setRange("today");
    setChartMode("movement");
    setPortfolioMarkers(null);
    setFocusedPortfolioMarker(null);
    setMaximized(false);
    setMarkerError(null);
    setHoverTimeLabel(null);
  }, [result?.symbol]);

  useEffect(() => {
    if (!result) return;
    fetchPortfolioMarkers(token, result.symbol)
      .then((response) => {
        setPortfolioMarkers(response);
        setMarkerError(null);
        if (response.markers.length > 0) {
          setRange("year");
        }
      })
      .catch((reason) => {
        setPortfolioMarkers(null);
        setMarkerError(reason instanceof Error ? reason.message : "Could not load portfolio markers");
      });
  }, [result?.symbol, token]);

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
        const response = await authFetch(token, `/api/dashboard/${path}/${encodeURIComponent(selectedResult.symbol)}?range=${range}`);
        if (!cancelled) {
          if (chartMode === "candles") {
            setCandles((await response.json()) as MarketCandleResponse);
          } else {
            setChart((await response.json()) as MarketChartResponse);
          }
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "Could not load chart");
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
      autoSize: false,
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
        vertLine: {
          color: "#687783",
          labelVisible: false,
          labelBackgroundColor: "#26323d"
        }
      }
    });
    const resizeObserver = new ResizeObserver(() => {
      const container = chartContainerRef.current;
      if (container && container.clientWidth > 0 && container.clientHeight > 0) {
        chartApi.resize(container.clientWidth, container.clientHeight, true);
      }
    });
    resizeObserver.observe(chartContainerRef.current);

    let markerById: Map<string, PortfolioMarker> | null = null;

    if (chartMode === "candles" && candles) {
      const series = chartApi.addSeries(CandlestickSeries, {
        upColor: "#147a46",
        downColor: "#b42318",
        borderUpColor: "#147a46",
        borderDownColor: "#b42318",
        wickUpColor: "#147a46",
        wickDownColor: "#b42318",
        priceFormat: { type: "price", precision: 2, minMove: 0.01 }
      }) as ISeriesApi<"Candlestick", Time>;
      series.setData(toCandleData(candles));
    } else if (chart) {
      const chartPoints = toLineData(chart);
      const latestValue = chartPoints.at(-1)?.value ?? 0;
      const lineColor = latestValue > 0 ? "#147a46" : latestValue < 0 ? "#b42318" : "#65717f";
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
          if (result === null || result.priceRange === null) return null;
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
      if (showPortfolioMarkers && portfolioMarkers) {
        markerById = new Map(portfolioMarkers.markers.map((marker) => [String(marker.id), marker]));
        createSeriesMarkers(series, toMovementSeriesMarkers(chart, portfolioMarkers));
      }
      series.createPriceLine({
        price: 0,
        color: "#4f5f6b",
        lineWidth: 2,
        lineStyle: LineStyle.Solid,
        axisLabelVisible: true,
        title: "0.00%"
      });
    }
    chartApi.subscribeCrosshairMove((param) => {
      const container = chartContainerRef.current;
      if (!container || !param.point || param.time === undefined) {
        setHoverTimeLabel(null);
        setFocusedPortfolioMarker(null);
        return;
      }

      setHoverTimeLabel({
        text: formatChartTime(param.time, activeTimeZone),
        x: container.offsetLeft + param.point.x
      });

      if (markerById) {
        const hoveredId = param.hoveredInfo?.objectKind === "series-marker"
          ? String(param.hoveredInfo.objectId ?? param.hoveredObjectId ?? "")
          : String(param.hoveredObjectId ?? "");
        setFocusedPortfolioMarker(markerById.get(hoveredId) ?? null);
      }
    });
    chartApi.timeScale().fitContent();

    return () => {
      resizeObserver.disconnect();
      setHoverTimeLabel(null);
      destroyChart(chartApi);
    };
  }, [candles, chart, chartMode, portfolioMarkers, result, showPortfolioMarkers]);

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
      <section className={`chart-dialog${maximized ? " maximized" : ""}`} role="dialog" aria-modal="true" aria-labelledby="chart-title" onClick={(event) => event.stopPropagation()}>
        <header className="chart-dialog-header">
          <div>
            <h2 id="chart-title">{result.symbol} - {result.companyName}</h2>
            <p>{result.region ?? "Market"} | {result.exchange ?? "Exchange"} | {result.currency ?? "Currency"}</p>
            {chartMode === "movement" && chart ? (
              <p>
                {chartLabel(chart)} | {chartDateRange(chart)}
              </p>
            ) : null}
            {chartMode === "candles" && candles ? <p>{candleLabel(candles)} | {chartDateRange(candles, candles.timeZone)}</p> : null}
            {portfolioMarkers ? <p>Revolut markers: {portfolioMarkers.markers.length}</p> : null}
          </div>
          <div className="chart-dialog-actions">
            <button type="button" className="secondary-button" onClick={() => setNewsOpen(true)}>News</button>
            <button type="button" className="secondary-button" onClick={() => setResearchOpen(true)}>Analyse</button>
            <button
              type="button"
              className="icon-button dialog-size-button"
              onClick={() => setMaximized((current) => !current)}
              aria-label={maximized ? "Restore chart dialog" : "Maximize chart dialog"}
              title={maximized ? "Restore" : "Maximize"}
            >
              <span className={maximized ? "restore-dialog-icon" : "maximize-dialog-icon"} aria-hidden="true" />
            </button>
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
          {hoverTimeLabel ? (
            <div className="chart-hover-time-label" style={{ left: hoverTimeLabel.x }}>
              {hoverTimeLabel.text}
            </div>
          ) : null}
          {loading ? <div className="chart-overlay">Loading chart</div> : null}
          {!loading && error ? <div className="chart-overlay error">{error}</div> : null}
          {!loading && !error && activePointCount === 0 ? <div className="chart-overlay">No data for this range</div> : null}
        </div>
        {markerError ? <div className="portfolio-marker-error">{markerError}</div> : null}

        <div className="chart-stats">
          <span>Price: {chartMode === "candles" && latestCandle ? formatPrice(latestCandle.close) : latest ? formatPrice(latest.price) : formatPrice(result.currentPrice)}</span>
          {chartMode === "candles" && latestCandle ? (
            <>
              <span>Open: {formatPrice(latestCandle.open)}</span>
              <span>High: {formatPrice(latestCandle.high)}</span>
              <span>Low: {formatPrice(latestCandle.low)}</span>
            </>
          ) : <><span>
            Day:{" "}
            <span className={toneClass(latest ? latest.percentChange : result.currentPercent)}>
              {latest ? formatPercent(latest.percentChange) : formatPercent(result.currentPercent)}
            </span>
          </span>
          <span>Range: {latest ? `${formatPrice(latest.low)} - ${formatPrice(latest.high)}` : `${formatPrice(result.low)} - ${formatPrice(result.high)}`}</span>
          <span>Previous close: {latest ? formatPrice(latest.previousClose) : formatPrice(result.previousClose)}</span></>}
          {chartMode === "movement" && chart ? <span>Dates: {chartDateRange(chart)}</span> : null}
          {chartMode === "candles" && candles ? <span>Dates: {chartDateRange(candles, candles.timeZone)} | Interval: {candles.interval}</span> : null}
        </div>
        {focusedPortfolioMarker ? (
          <div className={`portfolio-marker-details ${focusedPortfolioMarker.type === "BUY" ? "buy" : "sell"}`}>
            <strong>{focusedPortfolioMarker.type} · Revolut</strong>
            <span>{new Date(focusedPortfolioMarker.eventTime).toLocaleString()}</span>
            <span>Quantity: {focusedPortfolioMarker.quantity.toLocaleString(undefined, { maximumFractionDigits: 8 })}</span>
            <span>Price: {new Intl.NumberFormat(undefined, { style: "currency", currency: focusedPortfolioMarker.currency }).format(focusedPortfolioMarker.price)}</span>
            <span>Total: {new Intl.NumberFormat(undefined, { style: "currency", currency: focusedPortfolioMarker.currency }).format(focusedPortfolioMarker.totalAmount)}</span>
          </div>
        ) : chartMode === "movement" && showPortfolioMarkers && portfolioMarkers?.markers.length ? (
          <div className="portfolio-marker-hint">Hover a blue buy point or orange sell point to see Revolut transaction details.</div>
        ) : null}
        <div className="chart-mode-actions">
          <button type="button" className="secondary-button" onClick={() => {
            setChartMode(chartMode === "movement" ? "candles" : "movement");
            setFocusedPortfolioMarker(null);
          }}>
            {chartMode === "movement" ? "Show Candles" : "Show Movement"}
          </button>
          <button
            type="button"
            className={showPortfolioMarkers ? "" : "secondary-button"}
            onClick={() => {
              setShowPortfolioMarkers((current) => !current);
              setFocusedPortfolioMarker(null);
            }}
          >
            {showPortfolioMarkers ? "Hide Portfolio Markers" : "Show Portfolio Markers"}
          </button>
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

function candleLabel(candles: MarketCandleResponse): string {
  return candles.range === "today" && candles.fallback ? "Last active trading day candles" : "Provider OHLC candles";
}

function chartDateRange(chart: Pick<MarketChartResponse, "actualFrom" | "actualTo">, timeZone = CHART_TIME_ZONE): string {
  if (!chart.actualFrom || !chart.actualTo) {
    return "No saved dates";
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

function toMovementSeriesMarkers(chart: MarketChartResponse, response: PortfolioMarkerResponse): SeriesMarker<Time>[] {
  const chartPoints = toLineData(chart).map((point) => ({
    time: Number(point.time),
    value: point.value
  }));
  if (chartPoints.length === 0) return [];
  const firstPoint = chartPoints[0].time - 24 * 60 * 60;
  const lastPoint = chartPoints.at(-1)!.time + 24 * 60 * 60;

  return response.markers.flatMap((marker) => {
    const eventTime = Math.floor(new Date(marker.eventTime).getTime() / 1000);
    if (eventTime < firstPoint || eventTime > lastPoint) return [];
    const nearestPoint = chartPoints.reduce((nearest, point) =>
      Math.abs(point.time - eventTime) < Math.abs(nearest.time - eventTime) ? point : nearest
    );
    return [{
      id: String(marker.id),
      time: nearestPoint.time as Time,
      position: "atPriceMiddle",
      price: nearestPoint.value,
      color: marker.type === "BUY" ? "#2563eb" : "#f59e0b",
      shape: "circle",
      size: 0.6
    } as SeriesMarker<Time>];
  });
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
  const date = timeToDate(value);
  if (!date) {
    return String(value);
  }

  return new Intl.DateTimeFormat("de-DE", {
    timeZone,
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function timeToDate(value: Time): Date | null {
  if (typeof value === "number") {
    return new Date(value * 1000);
  }
  if (typeof value === "string") {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }
  if ("year" in value && "month" in value && "day" in value) {
    return new Date(Date.UTC(value.year, value.month - 1, value.day));
  }
  return null;
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
