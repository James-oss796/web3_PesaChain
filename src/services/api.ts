const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

export type Account = {
  id: string;
  name?: string | null;
  email: string;
  phone?: string | null;
  role: "BORROWER" | "LOAN_OFFICER" | "ADMINISTRATOR";
  passwordChangeRequired: boolean;
};

export type ApiProblem = {
  status: number;
  code: string;
  message: string;
};

let csrf: { token: string; headerName: string } | undefined;
let unauthorizedHandler: (() => void) | undefined;

export function setUnauthorizedHandler(handler?: () => void) {
  unauthorizedHandler = handler;
}

export class ApiError extends Error {
  constructor(readonly problem: ApiProblem) {
    super(problem.message);
    this.name = "ApiError";
  }
}

async function refreshCsrf() {
  const response = await fetch(`${API_BASE}/api/v1/auth/csrf`, {
    credentials: "include",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw await toApiError(response);
  csrf = (await response.json()) as { token: string; headerName: string };
  return csrf;
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {};
  try {
    body = (await response.json()) as Record<string, unknown>;
  } catch {
    // Error bodies from infrastructure proxies may be empty or non-JSON.
  }
  const status = response.status;
  return new ApiError({
    status,
    code: typeof body.code === "string" ? body.code : `HTTP_${status}`,
    message:
      typeof body.detail === "string"
        ? body.detail
        : typeof body.message === "string"
          ? body.message
          : status === 401
            ? "Your session has ended. Sign in again to continue."
            : status === 403
              ? "You do not have permission to perform this action."
              : status >= 500
                ? `The service returned a server error (HTTP ${status}). Please retry; if it continues, ask an administrator to inspect the service.`
                : `The request could not be completed (HTTP ${status}). Please review the details and try again.`,
  });
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? "GET").toUpperCase();
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const token = await refreshCsrf();
    headers.set(token.headerName, token.token);
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}/api/v1${path}`, {
      ...options,
      method,
      headers,
      credentials: "include",
    });
  } catch {
    throw new ApiError({
      status: 0,
      code: "SERVICE_UNAVAILABLE",
      message: "PesaChain could not reach the application service. Your records have not been changed.",
    });
  }

  if (response.status === 401 && !path.startsWith("/auth/login") && !path.startsWith("/auth/register") && !path.startsWith("/auth/me")) {
    unauthorizedHandler?.();
  }
  if (!response.ok) throw await toApiError(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function clearCsrf() {
  csrf = undefined;
}

export function idempotencyKey() {
  return crypto.randomUUID();
}
