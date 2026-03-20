import { CallableRequest, HttpsError } from "firebase-functions/v2/https";

/**
 * Extracts the verified Firebase UID from a CallableRequest.
 * Throws HttpsError("unauthenticated") if the caller is not authenticated.
 */
export function getAuthenticatedUid(request: CallableRequest): string {
  const uid = request.auth?.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  return uid;
}

/**
 * Asserts that a uid value is a non-empty string.
 * Throws HttpsError("unauthenticated") if not — for use in internal call paths.
 */
export function requireUid(uid: string | undefined): asserts uid is string {
  if (!uid) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
}
