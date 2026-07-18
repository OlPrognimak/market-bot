const backendUrl = process.env.MARKET_BOT_BACKEND_URL ?? "http://localhost:8080";

export async function GET(request: Request, context: { params: Promise<{ path: string[] }> }) {
  return proxy(request, context);
}

export async function POST(request: Request, context: { params: Promise<{ path: string[] }> }) {
  return proxy(request, context);
}

export async function PUT(request: Request, context: { params: Promise<{ path: string[] }> }) {
  return proxy(request, context);
}

export async function DELETE(request: Request, context: { params: Promise<{ path: string[] }> }) {
  return proxy(request, context);
}

export async function OPTIONS(request: Request, context: { params: Promise<{ path: string[] }> }) {
  return proxy(request, context);
}

async function proxy(request: Request, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const sourceUrl = new URL(request.url);
  const targetUrl = new URL(`/api/${path.join("/")}${sourceUrl.search}`, backendUrl);
  const headers = proxyHeaders(request.headers);
  const body = hasBody(request.method) ? await request.arrayBuffer() : undefined;
  const response = await fetch(targetUrl, {
    method: request.method,
    headers,
    body,
    redirect: "manual"
  });
  if (response.status === 401 || response.status === 403) {
    console.warn(
      "Market Bot API proxy auth failure",
      JSON.stringify({
        method: request.method,
        path: `/api/${path.join("/")}`,
        backend: targetUrl.origin,
        status: response.status,
        authorization: request.headers.has("authorization") ? "present" : "missing"
      })
    );
  }

  if (response.status === 401 || response.status === 403) {
    const backendMessage = await response.text();
    const diagnostic = [
      backendMessage || response.statusText || `Backend returned ${response.status}`,
      `Proxy: next-api-route`,
      `auth: ${request.headers.has("authorization") ? "present" : "missing"}`,
      `backend: ${targetUrl.origin}`,
      `path: /api/${path.join("/")}`
    ].join(". ");
    return new Response(diagnostic, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders(response.headers, request.headers)
    });
  }

  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: responseHeaders(response.headers, request.headers)
  });
}

function hasBody(method: string) {
  return method !== "GET" && method !== "HEAD";
}

function proxyHeaders(source: Headers) {
  const headers = new Headers(source);
  [
    "accept-encoding",
    "connection",
    "content-length",
    "host",
    "x-forwarded-host",
    "x-forwarded-port",
    "x-forwarded-proto"
  ].forEach((header) => headers.delete(header));
  return headers;
}

function responseHeaders(source: Headers, requestHeaders: Headers) {
  const headers = new Headers(source);
  [
    "connection",
    "content-encoding",
    "content-length",
    "transfer-encoding"
  ].forEach((header) => headers.delete(header));
  headers.set("x-market-bot-proxy", "next-api-route");
  headers.set("x-market-bot-proxy-auth", requestHeaders.has("authorization") ? "present" : "missing");
  return headers;
}
