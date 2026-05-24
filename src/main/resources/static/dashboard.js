const rowsEl = document.getElementById("marketRows");
const scanStatusEl = document.getElementById("scanStatus");
const scanButton = document.getElementById("scanButton");
const dialog = document.getElementById("chartDialog");
const closeChart = document.getElementById("closeChart");
const chartCanvas = document.getElementById("chartCanvas");
const chartEmpty = document.getElementById("chartEmpty");
const chartTitle = document.getElementById("chartTitle");
const chartSubtitle = document.getElementById("chartSubtitle");
const statPrice = document.getElementById("statPrice");
const statChange = document.getElementById("statChange");
const statRange = document.getElementById("statRange");
const statPrevious = document.getElementById("statPrevious");

let activeSymbol = null;
let activeName = null;
let activeRange = "today";

const formatPercent = new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
});

const formatPrice = new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
});

function movementClass(value) {
    if (value > 0) {
        return "up";
    }
    if (value < 0) {
        return "down";
    }
    return "neutral";
}

function pct(value) {
    return `${formatPercent.format(value)}%`;
}

function price(value) {
    return formatPrice.format(value);
}

function labelFor(result, field) {
    if (!result) {
        return "-";
    }
    return `${result.symbol} ${pct(result[field])}`;
}

async function loadSnapshot() {
    const response = await fetch("/api/dashboard/snapshot");
    if (!response.ok) {
        throw new Error(`Snapshot request failed: ${response.status}`);
    }

    return response.json();
}

function renderSnapshot(snapshot) {
    const lastScan = snapshot.lastScanAt ? new Date(snapshot.lastScanAt).toLocaleString() : "-";
    scanStatusEl.textContent = `Last scan: ${lastScan}`;

    document.getElementById("topRollingUp").textContent = labelFor(snapshot.topPositiveRolling, "rollingDelta");
    document.getElementById("topRollingDown").textContent = labelFor(snapshot.topNegativeRolling, "rollingDelta");
    document.getElementById("topDeltaUp").textContent = labelFor(snapshot.topPositiveDelta, "delta");
    document.getElementById("topDeltaDown").textContent = labelFor(snapshot.topNegativeDelta, "delta");

    const results = snapshot.results || [];
    if (results.length === 0) {
        rowsEl.innerHTML = `<tr><td colspan="8" class="empty">No market data yet</td></tr>`;
        return;
    }

    rowsEl.innerHTML = results.map((result) => `
        <tr>
            <td>
                <button class="symbol-button" type="button" data-symbol="${result.symbol}" data-name="${escapeHtml(result.companyName)}">
                    ${result.symbol}
                </button>
            </td>
            <td>${escapeHtml(result.companyName)}</td>
            <td>${escapeHtml(result.region || "-")}</td>
            <td class="num">${price(result.currentPrice)}</td>
            <td class="num ${movementClass(result.currentPercent)}">${pct(result.currentPercent)}</td>
            <td class="num ${movementClass(result.delta)}">${pct(result.delta)}</td>
            <td class="num ${movementClass(result.rollingDelta)}">${pct(result.rollingDelta)}</td>
            <td><span class="status-pill ${result.alert ? "alert" : ""}">${result.alert ? "Alert" : result.direction}</span></td>
        </tr>
    `).join("");
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;");
}

async function refreshSnapshot() {
    try {
        renderSnapshot(await loadSnapshot());
    } catch (error) {
        scanStatusEl.textContent = error.message;
    }
}

async function triggerScan() {
    scanButton.disabled = true;
    try {
        const response = await fetch("/api/dashboard/scan", { method: "POST" });
        if (!response.ok) {
            throw new Error(`Scan request failed: ${response.status}`);
        }
        renderSnapshot(await response.json());
    } catch (error) {
        scanStatusEl.textContent = error.message;
    } finally {
        scanButton.disabled = false;
    }
}

async function openChart(symbol, name) {
    activeSymbol = symbol;
    activeName = name;
    activeRange = "today";
    setActiveRangeButton();
    chartTitle.textContent = `${symbol} - ${name}`;
    chartSubtitle.textContent = "Today";
    dialog.showModal();
    await loadChart();
}

async function loadChart() {
    if (!activeSymbol) {
        return;
    }

    chartSubtitle.textContent = activeRange.charAt(0).toUpperCase() + activeRange.slice(1);
    const response = await fetch(`/api/dashboard/chart/${encodeURIComponent(activeSymbol)}?range=${activeRange}`);
    if (!response.ok) {
        chartEmpty.hidden = false;
        chartEmpty.textContent = `Chart request failed: ${response.status}`;
        clearCanvas();
        return;
    }

    const chart = await response.json();
    renderChart(chart.points || []);
}

function renderChart(points) {
    if (points.length === 0) {
        chartEmpty.hidden = false;
        chartEmpty.textContent = "No saved data for this range";
        clearCanvas();
        updateStats(null);
        return;
    }

    chartEmpty.hidden = true;
    updateStats(points[points.length - 1]);
    drawLineChart(points);
}

function updateStats(point) {
    if (!point) {
        statPrice.textContent = "Price: -";
        statChange.textContent = "Day: -";
        statRange.textContent = "Range: -";
        statPrevious.textContent = "Previous close: -";
        return;
    }

    statPrice.textContent = `Price: ${price(point.price)}`;
    statChange.textContent = `Day: ${pct(point.percentChange)}`;
    statRange.textContent = `Range: ${price(point.low)} - ${price(point.high)}`;
    statPrevious.textContent = `Previous close: ${price(point.previousClose)}`;
}

function clearCanvas() {
    const ctx = chartCanvas.getContext("2d");
    ctx.clearRect(0, 0, chartCanvas.width, chartCanvas.height);
}

function drawLineChart(points) {
    const rect = chartCanvas.getBoundingClientRect();
    const ratio = window.devicePixelRatio || 1;
    chartCanvas.width = Math.floor(rect.width * ratio);
    chartCanvas.height = Math.floor(rect.height * ratio);

    const ctx = chartCanvas.getContext("2d");
    ctx.scale(ratio, ratio);

    const width = rect.width;
    const height = rect.height;
    const padding = { top: 18, right: 56, bottom: 34, left: 54 };
    const values = points.map((point) => point.percentChange);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = Math.max(max - min, 0.01);
    const yMin = min - span * 0.15;
    const yMax = max + span * 0.15;
    const plotWidth = width - padding.left - padding.right;
    const plotHeight = height - padding.top - padding.bottom;
    const lineColor = values[values.length - 1] >= values[0] ? "#168a4a" : "#be2f2f";

    ctx.clearRect(0, 0, width, height);
    ctx.font = "12px system-ui, sans-serif";
    ctx.lineWidth = 1;
    ctx.strokeStyle = "#d8dde3";
    ctx.fillStyle = "#65717f";

    for (let i = 0; i <= 4; i++) {
        const y = padding.top + (plotHeight / 4) * i;
        ctx.beginPath();
        ctx.moveTo(padding.left, y);
        ctx.lineTo(width - padding.right, y);
        ctx.stroke();

        const labelValue = yMax - ((yMax - yMin) / 4) * i;
        ctx.fillText(`${formatPercent.format(labelValue)}%`, width - padding.right + 8, y + 4);
    }

    ctx.strokeStyle = lineColor;
    ctx.lineWidth = 2;
    ctx.beginPath();

    points.forEach((point, index) => {
        const x = padding.left + (points.length === 1 ? plotWidth / 2 : (plotWidth / (points.length - 1)) * index);
        const y = padding.top + plotHeight - ((point.percentChange - yMin) / (yMax - yMin)) * plotHeight;
        if (index === 0) {
            ctx.moveTo(x, y);
        } else {
            ctx.lineTo(x, y);
        }
    });

    ctx.stroke();

    ctx.fillStyle = lineColor;
    points.forEach((point, index) => {
        const x = padding.left + (points.length === 1 ? plotWidth / 2 : (plotWidth / (points.length - 1)) * index);
        const y = padding.top + plotHeight - ((point.percentChange - yMin) / (yMax - yMin)) * plotHeight;
        ctx.beginPath();
        ctx.arc(x, y, 3, 0, Math.PI * 2);
        ctx.fill();
    });

    ctx.fillStyle = "#65717f";
    const first = new Date(points[0].time).toLocaleString();
    const last = new Date(points[points.length - 1].time).toLocaleString();
    ctx.fillText(first, padding.left, height - 10);
    const lastWidth = ctx.measureText(last).width;
    ctx.fillText(last, width - padding.right - lastWidth, height - 10);
}

function setActiveRangeButton() {
    document.querySelectorAll(".range-tabs button").forEach((button) => {
        button.classList.toggle("active", button.dataset.range === activeRange);
    });
}

rowsEl.addEventListener("click", async (event) => {
    const button = event.target.closest(".symbol-button");
    if (!button) {
        return;
    }
    await openChart(button.dataset.symbol, button.dataset.name);
});

document.querySelectorAll(".range-tabs button").forEach((button) => {
    button.addEventListener("click", async () => {
        activeRange = button.dataset.range;
        setActiveRangeButton();
        await loadChart();
    });
});

closeChart.addEventListener("click", () => dialog.close());
scanButton.addEventListener("click", triggerScan);
window.addEventListener("resize", () => {
    if (dialog.open && activeSymbol) {
        loadChart();
    }
});

refreshSnapshot();
setInterval(refreshSnapshot, 15000);
