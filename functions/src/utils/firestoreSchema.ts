/**
 * Firestore document schema type definitions for KiteWatch.
 *
 * Collection layout:
 *   users/{uid}/profile    — UserProfile  (single document)
 *   users/{uid}/session    — UserSession  (single document)
 *   users/{uid}/syncState  — SyncState    (single document)
 *
 * All collections are private per-user. Access enforced by firestore.rules.
 * Cloud Functions use Admin SDK which bypasses rules by design.
 */

import { Timestamp } from "firebase-admin/firestore";

/** Stored at users/{uid}/profile */
export interface UserProfile {
  /** Kite Connect user ID (returned by /user/profile) */
  kiteUserId: string;
  /** Kite Connect user name (display only) */
  kiteUserName: string;
  /** Kite API key used during binding (for key rotation tracking) */
  apiKey: string;
  /** Server timestamp when the account was first bound */
  boundAt: Timestamp;
}

/** Stored at users/{uid}/session */
export interface UserSession {
  /** Kite Connect access_token obtained after OAuth exchange */
  accessToken: string;
  /** Server timestamp when the access_token was obtained */
  tokenObtainedAt: Timestamp;
  /** True when the token has been invalidated or expired */
  tokenExpired: boolean;
  /** Server timestamp of the last successful data sync, null if never synced */
  lastSyncedAt: Timestamp | null;
}

/** Stored at users/{uid}/syncState */
export type SyncStatus = "IDLE" | "RUNNING" | "SUCCESS" | "FAILED";

export interface SyncState {
  /** Server timestamp when the last sync operation started, null if never started */
  lastSyncStartedAt: Timestamp | null;
  /** Current or last-known sync status */
  lastSyncStatus: SyncStatus;
  /** Error message from the last FAILED sync, null otherwise */
  lastErrorMessage: string | null;
}
