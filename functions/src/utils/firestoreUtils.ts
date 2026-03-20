import { getFirestore } from "firebase-admin/firestore";

/** Shared Firestore instance — Admin SDK singleton. */
export const db = getFirestore();

/** users/{uid}/profile/data */
export function getUserProfileRef(uid: string) {
  return db.collection("users").doc(uid).collection("profile").doc("data");
}

/** users/{uid}/session/data */
export function getUserSessionRef(uid: string) {
  return db.collection("users").doc(uid).collection("session").doc("data");
}

/** users/{uid}/syncState/data */
export function getUserSyncStateRef(uid: string) {
  return db.collection("users").doc(uid).collection("syncState").doc("data");
}
