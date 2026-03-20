/**
 * Unit tests for exchangeKiteToken (FM-009).
 *
 * Strategy:
 *  - Mock firebase-admin/firestore (Admin SDK)
 *  - Mock ../utils/kiteClient (makeKiteRequest, KiteApiError)
 *  - Mock ../utils/secrets (kiteApiSecret, kiteApiKey)
 *  - Invoke the onCall handler directly via the test helper pattern
 */

import { CallableRequest } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";

// ── Secrets mock ──────────────────────────────────────────────────────────────
jest.mock("../utils/secrets", () => ({
  kiteApiSecret: { value: () => "test-api-secret" },
  kiteApiKey: { value: () => "test-api-key" },
}));

// ── kiteClient mock ───────────────────────────────────────────────────────────
const mockMakeKiteRequest = jest.fn();
jest.mock("../utils/kiteClient", () => {
  class KiteApiError extends Error {
    constructor(
      message: string,
      public kiteErrorType: string,
      public httpStatus: number
    ) {
      super(message);
      this.name = "KiteApiError";
    }
  }
  return { makeKiteRequest: mockMakeKiteRequest, KiteApiError };
});

// ── Firestore mock ────────────────────────────────────────────────────────────
const mockSessionSet = jest.fn().mockResolvedValue(undefined);
const mockProfileSet = jest.fn().mockResolvedValue(undefined);
const mockProfileGet = jest.fn();

jest.mock("../utils/firestoreUtils", () => ({
  getUserProfileRef: (_uid: string) => ({
    get: mockProfileGet,
    set: mockProfileSet,
  }),
  getUserSessionRef: (_uid: string) => ({
    set: mockSessionSet,
  }),
}));

// ── firebase-admin/firestore FieldValue ───────────────────────────────────────
jest.mock("firebase-admin/firestore", () => ({
  FieldValue: { serverTimestamp: () => "SERVER_TIMESTAMP" },
}));

// ── firebase-functions/logger ─────────────────────────────────────────────────
jest.mock("firebase-functions/logger", () => ({
  info: jest.fn(),
  error: jest.fn(),
}));

// ── Load the function AFTER mocks are in place ────────────────────────────────
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { exchangeKiteToken } = require("./exchangeKiteToken");

// ── Helpers ───────────────────────────────────────────────────────────────────
function makeRequest(
  uid: string | undefined,
  data: Record<string, unknown> = {}
): CallableRequest {
  return {
    auth: uid ? { uid, token: {} as never, rawToken: "" } : undefined,
    data,
    rawRequest: {} as never,
    instanceIdToken: undefined,
    acceptsStreaming: false,
  };
}

// Reach inside the onCall wrapper to get the handler
// firebase-functions v2 onCall returns an object; the run() method invokes the handler
async function invoke(
  uid: string | undefined,
  data: Record<string, unknown>
): Promise<unknown> {
  const req = makeRequest(uid, data);
  return (exchangeKiteToken as { run: (r: CallableRequest) => Promise<unknown> }).run(req);
}

const VALID_KITE_RESPONSE = {
  access_token: "kite-access-token-abc",
  user_id: "ZR1234",
  user_name: "Test User",
};

// ── Tests ─────────────────────────────────────────────────────────────────────

beforeEach(() => {
  jest.clearAllMocks();
});

describe("exchangeKiteToken", () => {
  it("throws unauthenticated when caller has no auth", async () => {
    await expect(invoke(undefined, { requestToken: "tok" })).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  it("throws not-found when user profile does not exist (storeApiKey not called)", async () => {
    mockProfileGet.mockResolvedValue({ exists: false });

    await expect(invoke("uid-123", { requestToken: "tok" })).rejects.toMatchObject({
      code: "not-found",
    });
  });

  it("throws unauthenticated when Kite API returns an error", async () => {
    mockProfileGet.mockResolvedValue({
      exists: true,
      data: () => ({ apiKey: "test-api-key" }),
    });

    const { KiteApiError } = jest.requireMock("../utils/kiteClient");
    mockMakeKiteRequest.mockRejectedValue(
      new KiteApiError("Token exchange failed", "TokenException", 403)
    );

    await expect(invoke("uid-123", { requestToken: "tok" })).rejects.toMatchObject({
      code: "unauthenticated",
    });
  });

  it("stores access_token in Firestore and returns success (no token in response)", async () => {
    mockProfileGet.mockResolvedValue({
      exists: true,
      data: () => ({ apiKey: "test-api-key" }),
    });
    mockMakeKiteRequest.mockResolvedValue(VALID_KITE_RESPONSE);

    const result = await invoke("uid-123", { requestToken: "request_token_xyz" });

    // access_token must NOT be in the response
    expect(result).toEqual({
      success: true,
      kiteUserId: "ZR1234",
      kiteUserName: "Test User",
    });
    expect((result as Record<string, unknown>).access_token).toBeUndefined();
    expect((result as Record<string, unknown>).accessToken).toBeUndefined();

    // Session document must be written to Firestore
    expect(mockSessionSet).toHaveBeenCalledWith(
      {
        accessToken: "kite-access-token-abc",
        tokenObtainedAt: FieldValue.serverTimestamp(),
        tokenExpired: false,
      },
      { merge: true }
    );

    // Profile must be updated with Kite identity
    expect(mockProfileSet).toHaveBeenCalledWith(
      expect.objectContaining({
        kiteUserId: "ZR1234",
        kiteUserName: "Test User",
      }),
      { merge: true }
    );
  });

  it("computes checksum using KITE_API_SECRET (not returned to caller)", async () => {
    mockProfileGet.mockResolvedValue({
      exists: true,
      data: () => ({ apiKey: "test-api-key" }),
    });
    mockMakeKiteRequest.mockResolvedValue(VALID_KITE_RESPONSE);

    await invoke("uid-123", { requestToken: "request_token_xyz" });

    // Verify kiteClient was called with a checksum field (computed server-side)
    const callArgs = mockMakeKiteRequest.mock.calls[0];
    expect(callArgs[0]).toBe("/session/token");
    expect(callArgs[1]).toBe("POST");
    expect(callArgs[2]).toHaveProperty("checksum");
    expect(callArgs[2]).toHaveProperty("api_key", "test-api-key");
    expect(callArgs[2]).toHaveProperty("request_token", "request_token_xyz");

    // The checksum must incorporate the secret — verify it's a hex SHA-256 string
    const { checksum } = callArgs[2] as { checksum: string };
    expect(checksum).toMatch(/^[0-9a-f]{64}$/);

    // Verify the checksum value matches the expected computation
    const crypto = await import("crypto");
    const expected = crypto
      .createHash("sha256")
      .update("test-api-key" + "request_token_xyz" + "test-api-secret")
      .digest("hex");
    expect(checksum).toBe(expected);
  });

  it("throws invalid-argument when requestToken is empty", async () => {
    await expect(invoke("uid-123", { requestToken: "" })).rejects.toMatchObject({
      code: "invalid-argument",
    });
  });

  it("throws invalid-argument when requestToken is missing", async () => {
    await expect(invoke("uid-123", {})).rejects.toMatchObject({
      code: "invalid-argument",
    });
  });
});
