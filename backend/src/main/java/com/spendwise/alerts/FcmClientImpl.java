package com.spendwise.alerts;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Component;

/** E5-S3-T1 — reuses the existing firebase-admin dependency (auth.FirebaseConfig), no new SDK. */
@Component
public class FcmClientImpl implements FcmClient {

    private final FirebaseMessaging firebaseMessaging;

    public FcmClientImpl(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public void send(String fcmToken, String title, String body) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .build();
        try {
            firebaseMessaging.send(message);
        } catch (FirebaseMessagingException e) {
            // Wrapped as unchecked so AlertDispatchServiceImpl's catch (RuntimeException) —
            // the same never-throw contract as every other best-effort job dependency in this
            // codebase — covers this uniformly with the SMTP client's MailException.
            throw new IllegalStateException("FCM send failed: " + e.getMessage(), e);
        }
    }
}
