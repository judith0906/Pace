import { setGlobalOptions } from "firebase-functions";
import * as logger from "firebase-functions/logger";
import { onValueWritten } from "firebase-functions/v2/database";
import * as admin from "firebase-admin";

admin.initializeApp();

setGlobalOptions({ maxInstances: 10 });

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
