package me.aap.fermata.media.service;

import android.service.notification.NotificationListenerService;

/**
 * Not used for notifications: Android only lets an app with notification access see and control
 * other apps' media sessions, which {@link PreviousMediaApp} needs.
 */
public class MediaSessionListener extends NotificationListenerService {
}
