import { setGlobalOptions } from "firebase-functions";
import * as logger from "firebase-functions/logger";
import { onValueWritten } from "firebase-functions/v2/database";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";

admin.initializeApp();

setGlobalOptions({ maxInstances: 10 });

const FIREBASE_WEB_API_KEY = "AIzaSyAfYbuot_GyUyy-ToC_W5DOxkaS-ZfDUEg";

const SUPABASE_URL = "https://nveliyinwnrcagsykxtz.supabase.co";
const SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im52ZWxpeWlud25yY2Fnc3lreHR6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYxNjg2MDcsImV4cCI6MjA5MTc0NDYwN30.xbn52nb0MS2OpGWbPnxjgG3KTxwEPwIQWsHNGtlHjAo";
const SUPABASE_PROFILE_BUCKET = "profile-photos";
const SUPABASE_CHAT_BUCKET = "chat-moments";

export const onMessageWrite = onValueWritten(
    "/circles/{circleId}/messages/{messageId}",
    async (event) => {
        if (!event.data.after.exists()) {
            logger.info("Message deleted, skipping");
            return;
        }

        const message = event.data.after.val();
        const circleId = event.params.circleId;
        const messageId = event.params.messageId;

        logger.info(`New message ${messageId} in circle ${circleId}`, { message });

        const circleNameSnap = await admin.database()
            .ref(`circles/${circleId}/name`).once("value");
        const circleName: string = circleNameSnap.val() || "Círculo";

        const title = `Tienes un nuevo mensaje en ${circleName}`;
        let body: string;
        if (message.senderId === "system") {
            body = message.text || "Nuevo mensaje del sistema";
        } else {
            const senderName: string = message.senderName || "Alguien";
            const msgText: string = message.text || "...";
            body = `${senderName}: ${msgText}`;
        }

        const membersSnap = await admin.database()
            .ref(`circles/${circleId}/members`).once("value");
        const members: Record<string, boolean> = membersSnap.val() || {};
        const memberIds = Object.keys(members);

        const tokens: string[] = [];

        for (const uid of memberIds) {
            if (uid === message.senderId && message.senderId !== "system") continue;

            const mutedSnap = await admin.database()
                .ref(`users/${uid}/mutedCircles/${circleId}`).once("value");
            if (mutedSnap.val() === true) continue;

            const tokensSnap = await admin.database()
                .ref(`users/${uid}/fcmTokens`).once("value");
            const userTokens: Record<string, boolean> = tokensSnap.val() || {};
            tokens.push(...Object.keys(userTokens));
        }

        if (tokens.length === 0) {
            logger.info("No tokens to send");
            return;
        }

        logger.info(`Sending push to ${tokens.length} tokens`);

        const data: Record<string, string> = {
            title,
            body,
            circleId,
            circleName,
            senderName: message.senderName || "",
        };

        const multicastMessage: admin.messaging.MulticastMessage = {
            tokens,
            data,
            android: {
                priority: "high",
                notification: {
                    title,
                    body,
                    channelId: "pace_circle_chat",
                    tag: circleId,
                    priority: "high",
                },
            },
        };

        try {
            const response = await admin.messaging().sendEachForMulticast(multicastMessage);
            logger.info(`Sent: ${response.successCount} success, ${response.failureCount} failures`);
        } catch (error) {
            logger.error("Error sending push", error);
        }
    }
);

interface SupabaseStorageObject {
    name: string;
    id?: string;
}

async function signInWithPassword(email: string, password: string): Promise<{ idToken: string; uid: string }> {
    const res = await fetch(
        `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_WEB_API_KEY}`,
        {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, password, returnSecureToken: true }),
        }
    );

    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err?.error?.message || "INVALID_CREDENTIALS");
    }

    const data = await res.json();
    return { idToken: data.idToken as string, uid: data.localId as string };
}

async function deleteSupabaseFolder(bucket: string, folderPath: string): Promise<void> {
    try {
        const base = SUPABASE_URL.replace(/\/$/, "");
        const listUrl =
            `${base}/storage/v1/object/list/${bucket}` +
            `?prefix=${encodeURIComponent(folderPath)}&limit=1000&offset=0&sort=name&order=asc`;

        const listRes = await fetch(listUrl, {
            method: "POST",
            headers: {
                apikey: SUPABASE_ANON_KEY,
                Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({}),
        });

        if (!listRes.ok) return;
        const objects = (await listRes.json()) as SupabaseStorageObject[];

        if (!Array.isArray(objects) || objects.length === 0) return;

        const paths = objects
            .map((o) => o.name)
            .filter((n): n is string => typeof n === "string" && n.length > 0);

        if (paths.length === 0) return;

        await fetch(`${base}/storage/v1/object/${bucket}`, {
            method: "DELETE",
            headers: {
                apikey: SUPABASE_ANON_KEY,
                Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ prefixes: paths }),
        });
    } catch (error) {
        logger.warn(`Supabase delete folder ${bucket}/${folderPath} failed`, error);
    }
}

async function removeUserFromAllCircles(uid: string): Promise<void> {
    const circlesSnap = await admin.database()
        .ref(`users/${uid}/circles`)
        .once("value");
    const circleIds: string[] = circlesSnap.exists()
        ? Object.keys(circlesSnap.val() || {})
        : [];

    const updates: Record<string, unknown> = {};

    for (const circleId of circleIds) {
        const circleSnap = await admin.database()
            .ref(`circles/${circleId}`)
            .once("value");
        if (!circleSnap.exists()) continue;

        const circle = circleSnap.val();
        const createdBy: string = circle?.createdBy || "";
        const joinCode: string = circle?.joinCode || "";
        const members: Record<string, unknown> = circle?.members || {};
        const memberIds = Object.keys(members || {});

        if (createdBy === uid) {
            updates[`circles/${circleId}`] = null;
            if (joinCode) {
                updates[`circleJoinCodes/${joinCode}`] = null;
            }
            for (const memberId of memberIds) {
                updates[`users/${memberId}/circles/${circleId}`] = null;
            }
        } else {
            updates[`circles/${circleId}/members/${uid}`] = null;
            updates[`users/${uid}/circles/${circleId}`] = null;
        }
    }

    if (Object.keys(updates).length > 0) {
        await admin.database().ref().update(updates);
    }
}

async function cleanupProfilePhotos(uid: string): Promise<void> {
    await deleteSupabaseFolder(SUPABASE_PROFILE_BUCKET, `profiles/${uid}/`);
}

async function cleanupChatPhotos(uid: string): Promise<void> {
    const circlesSnap = await admin.database()
        .ref(`users/${uid}/circles`)
        .once("value");
    const circleIds: string[] = circlesSnap.exists()
        ? Object.keys(circlesSnap.val() || {})
        : [];

    for (const circleId of circleIds) {
        const messagesSnap = await admin.database()
            .ref(`circles/${circleId}/messages`)
            .once("value");
        if (!messagesSnap.exists()) continue;

        const messages: Record<string, Record<string, unknown>> = messagesSnap.val() || {};

        for (const msgId of Object.keys(messages)) {
            const msg = messages[msgId] || {};
            if (msg.senderId !== uid) continue;

            const photoUrl: string = (msg.photoUrl as string) || "";
            if (!photoUrl.includes(SUPABASE_URL) || !photoUrl.includes(`/object/public/`)) continue;

            const marker = `${SUPABASE_URL}/storage/v1/object/public/${SUPABASE_CHAT_BUCKET}/`;
            if (photoUrl.indexOf(marker) !== 0) continue;

            const objectPath = photoUrl.substring(marker.length);

            await fetch(`${SUPABASE_URL.replace(/\/$/, "")}/storage/v1/object/${SUPABASE_CHAT_BUCKET}`, {
                method: "DELETE",
                headers: {
                    apikey: SUPABASE_ANON_KEY,
                    Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
                    "Content-Type": "application/json",
                },
                body: JSON.stringify({ prefixes: [objectPath] }),
            });
        }
    }
}

async function isGoogleAccount(email: string): Promise<boolean> {
    try {
        const user = await admin.auth().getUserByEmail(email);
        return Array.isArray(user.providerData) &&
            user.providerData.some((p) => p?.providerId === "google.com");
    } catch {
        return false;
    }
}

export const deleteAccountRequest = onCall(
    { maxInstances: 10 },
    async (request) => {
        const data = request.data as { email?: unknown; password?: unknown };
        const { email, password } = data || {};

        if (typeof email !== "string" || typeof password !== "string" || !email || !password) {
            throw new HttpsError("invalid-argument", "MISSING_FIELDS");
        }

        try {
            const { idToken, uid } = await signInWithPassword(email, password);

            const decoded = await admin.auth().verifyIdToken(idToken);
            if (decoded.uid !== uid) {
                throw new HttpsError("permission-denied", "UNAUTHORIZED");
            }

            await removeUserFromAllCircles(uid);

            const subnodes = [
                "habits",
                "habit_logs",
                "settings",
                "profile",
                "devices",
                "circles",
                "blocked",
                "mutedCircles",
                "fcmTokens",
                "subscription",
                "backups",
                "firstInstallDate",
            ];

            const updates: Record<string, unknown> = {};
            for (const node of subnodes) {
                updates[`users/${uid}/${node}`] = null;
            }
            updates[`users/${uid}`] = null;
            await admin.database().ref().update(updates);

            await cleanupProfilePhotos(uid);
            await cleanupChatPhotos(uid);

            await admin.auth().deleteUser(uid);

            logger.info(`Account deleted via web for uid ${uid}`);
            return { success: true };
        } catch (error) {
            if (error instanceof HttpsError) {
                throw error;
            }

            const message = (error as Error)?.message || "UNKNOWN_ERROR";
            logger.error("deleteAccountRequest error", error);

            const invalidCredentials =
                message === "INVALID_CREDENTIALS" ||
                message === "INVALID_PASSWORD" ||
                message === "EMAIL_NOT_FOUND" ||
                message === "USER_DISABLED" ||
                message === "INVALID_LOGIN_CREDENTIALS";

            if (invalidCredentials && (await isGoogleAccount(email))) {
                throw new HttpsError("failed-precondition", "GOOGLE_ACCOUNT");
            }

            if (invalidCredentials) {
                throw new HttpsError("unauthenticated", "INVALID_CREDENTIALS");
            }

            throw new HttpsError("internal", message);
        }
    }
);
