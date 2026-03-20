/**
 * Kite Connect API HTTP client for Cloud Functions.
 * Uses Node.js built-in https module — no external dependencies.
 */

import * as https from "https";
import { kiteApiKey } from "./secrets";

const KITE_BASE_HOST = "api.kite.trade";

export class KiteApiError extends Error {
  constructor(
    message: string,
    public readonly kiteErrorType: string,
    public readonly httpStatus: number
  ) {
    super(message);
    this.name = "KiteApiError";
  }
}

interface KiteResponse {
  status: string;
  data?: Record<string, unknown>;
  message?: string;
  error_type?: string;
}

/**
 * Makes an HTTPS request to the Kite Connect API.
 *
 * @param endpoint  - Path, e.g. "/session/token"
 * @param method    - HTTP method ("GET", "POST", etc.)
 * @param body      - Optional request body (will be form-encoded for POST)
 * @param accessToken - Optional access token for authenticated requests
 */
export async function makeKiteRequest(
  endpoint: string,
  method: string,
  body?: Record<string, string>,
  accessToken?: string
): Promise<Record<string, unknown>> {
  return new Promise((resolve, reject) => {
    const formBody = body
      ? Object.entries(body)
          .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
          .join("&")
      : undefined;

    const headers: Record<string, string> = {
      "X-Kite-Version": "3",
    };

    if (formBody) {
      headers["Content-Type"] = "application/x-www-form-urlencoded";
      headers["Content-Length"] = String(Buffer.byteLength(formBody));
    }

    if (accessToken) {
      headers["Authorization"] = `token ${kiteApiKey.value()}:${accessToken}`;
    }

    const options: https.RequestOptions = {
      hostname: KITE_BASE_HOST,
      path: endpoint,
      method: method.toUpperCase(),
      headers,
    };

    const req = https.request(options, (res) => {
      let rawData = "";
      res.on("data", (chunk) => {
        rawData += chunk;
      });
      res.on("end", () => {
        let parsed: KiteResponse;
        try {
          parsed = JSON.parse(rawData) as KiteResponse;
        } catch {
          reject(
            new KiteApiError("Invalid JSON response from Kite API", "ParseError", res.statusCode ?? 0)
          );
          return;
        }

        if (parsed.status !== "success") {
          reject(
            new KiteApiError(
              parsed.message ?? "Kite API error",
              parsed.error_type ?? "UnknownError",
              res.statusCode ?? 0
            )
          );
          return;
        }

        resolve(parsed.data ?? {});
      });
    });

    req.on("error", (err) => {
      reject(new KiteApiError(err.message, "NetworkError", 0));
    });

    if (formBody) {
      req.write(formBody);
    }

    req.end();
  });
}
