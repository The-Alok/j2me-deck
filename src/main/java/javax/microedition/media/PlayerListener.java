// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.media;
public interface PlayerListener {
 String CLOSED="closed", DEVICE_AVAILABLE="deviceAvailable", DEVICE_UNAVAILABLE="deviceUnavailable", DURATION_UPDATED="durationUpdated", END_OF_MEDIA="endOfMedia", ERROR="error", STARTED="started", STOPPED="stopped", VOLUME_CHANGED="volumeChanged";
 void playerUpdate(Player player,String event,Object eventData);
}
