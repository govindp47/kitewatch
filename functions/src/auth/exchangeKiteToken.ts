import * as crypto from "crypto";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { REGION } from "../utils/region";
import { kiteApiSecret, kiteApiKey } from "../utils/secrets";
import { getAuthenticatedUid } from "../middleware/authMiddleware";
import { getUserProfileRef, getUserSessionRef } from "../utils/firestoreUtils";
import { makeKiteRequest, KiteApiError } from "../utils/kiteClient";
import * as logger from "firebase-functions/logger";

interface ExchangeKiteTokenRequest {
  requestToken: string;
}

interface KiteSessionData {
  access_token: string;
  user_id: string;
  user_name: string;
}

export const exchangeKiteToken = onCall<ExchangeKiteTokenRequest>(
  {
    region: REGION,
    secrets: [kiteApiSecret, kiteApiKey],
  },
  async (request) => {
    const uid = getAuthenticatedUid(request);

    const { requestToken } = request.data;
    if (typeof requestToken !== "string" || requestToken.trim().length === 0) {
      throw new HttpsError("invalid-argument", "requestToken must be a non-empty string.");
    }

    // Fetch the user's stored API key (set during onboarding via storeApiKey)
    const profileSnap = await getUserProfileRef(uid).get();
    if (!profileSnap.exists) {
      throw new HttpsError(
        "not-found",
        "User profile not found. Call storeApiKey before exchangeKiteToken."
      );
    }

    const profileData = profileSnap.data();
    const apiKey = profileData?.apiKey as string | undefined;
    if (!apiKey || apiKey.trim().length === 0) {
      throw new HttpsError(
        "not-found",
        "API key not found in user profile. Call storeApiKey before exchangeKiteToken."
      );
    }

    // Compute SHA-256 checksum server-side: sha256(apiKey + requestToken + apiSecret)
    const apiSecret = kiteApiSecret.value();
    const checksum = crypto
      .createHash("sha256")
      .update(apiKey + requestToken.trim() + apiSecret)
      .digest("hex");

    let sessionData: KiteSessionData;
    try {
      const response = await makeKiteRequest("/session/token", "POST", {
        api_key: apiKey,
        request_token: requestToken.trim(),
        checksum,
      });

      sessionData = response as unknown as KiteSessionData;

      if (!sessionData.access_token || !sessionData.user_id) {
        throw new KiteApiError("Incomplete session data from Kite", "IncompleteResponse", 200);
      }
    } catch (err) {
      const isKiteError = err instanceof KiteApiError || (err as Error)?.name === "KiteApiError";
      logger.error("Kite token exchange failed", {
        uid,
        errorName: (err as Error)?.name,
        errorMessage: (err as Error)?.message,
        kiteErrorType: isKiteError ? (err as KiteApiError).kiteErrorType : "N/A",
        httpStatus: isKiteError ? (err as KiteApiError).httpStatus : "N/A",
      });
      // Intentionally opaque: do not leak raw Kite error messages to the caller
      throw new HttpsError("unauthenticated", "Kite token exchange failed.");
    }

    // Store access_token in Firestore — NEVER return it to the device
    await getUserSessionRef(uid).set(
      {
        accessToken: sessionData.access_token,
        tokenObtainedAt: FieldValue.serverTimestamp(),
        tokenExpired: false,
      },
      { merge: true }
    );

    // Update profile with Kite identity fields
    await getUserProfileRef(uid).set(
      {
        kiteUserId: sessionData.user_id,
        kiteUserName: sessionData.user_name ?? "",
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    logger.info("Kite token exchange successful", { uid, kiteUserId: sessionData.user_id });

    // access_token intentionally absent from the response
    return {
      success: true,
      kiteUserId: sessionData.user_id,
      kiteUserName: sessionData.user_name ?? "",
    };
  }
);
