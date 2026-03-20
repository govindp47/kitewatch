import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { REGION } from "../utils/region";
import { getAuthenticatedUid } from "../middleware/authMiddleware";
import { getUserProfileRef } from "../utils/firestoreUtils";

interface StoreApiKeyRequest {
  apiKey: string;
}

export const storeApiKey = onCall<StoreApiKeyRequest>(
  { region: REGION },
  async (request) => {
    const uid = getAuthenticatedUid(request);

    const { apiKey } = request.data;
    if (typeof apiKey !== "string" || apiKey.trim().length < 4 || apiKey.trim().length > 64) {
      throw new HttpsError(
        "invalid-argument",
        "apiKey must be a string between 4 and 64 characters."
      );
    }

    await getUserProfileRef(uid).set(
      { apiKey: apiKey.trim(), updatedAt: FieldValue.serverTimestamp() },
      { merge: true }
    );

    return { success: true };
  }
);
