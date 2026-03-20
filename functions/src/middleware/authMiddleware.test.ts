import { getAuthenticatedUid, requireUid } from "./authMiddleware";
import { HttpsError, CallableRequest } from "firebase-functions/v2/https";

function makeRequest(uid?: string): CallableRequest {
  return {
    auth: uid
      ? { uid, token: {} as never, rawToken: "" }
      : undefined,
    data: {},
    rawRequest: {} as never,
    instanceIdToken: undefined,
    acceptsStreaming: false,
  };
}

describe("getAuthenticatedUid", () => {
  it("throws unauthenticated when auth is undefined", () => {
    const req = makeRequest(undefined);
    expect(() => getAuthenticatedUid(req)).toThrow(HttpsError);
    try {
      getAuthenticatedUid(req);
    } catch (e) {
      expect((e as HttpsError).code).toBe("unauthenticated");
    }
  });

  it("returns uid when auth is present", () => {
    const req = makeRequest("test-uid");
    expect(getAuthenticatedUid(req)).toBe("test-uid");
  });
});

describe("requireUid", () => {
  it("throws unauthenticated when uid is undefined", () => {
    expect(() => requireUid(undefined)).toThrow(HttpsError);
    try {
      requireUid(undefined);
    } catch (e) {
      expect((e as HttpsError).code).toBe("unauthenticated");
    }
  });

  it("does not throw when uid is a non-empty string", () => {
    expect(() => requireUid("test-uid")).not.toThrow();
  });
});
