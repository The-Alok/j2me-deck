// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 J2ME Deck contributors
package javax.microedition.midlet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class MIDlet {
    private static final Map<String, String> PROPS = new ConcurrentHashMap<>();

    protected MIDlet() { javax.microedition.lcdui.Display.getDisplay(this); }

    public static void initProps(Map<String, String> props) {
        PROPS.clear();
        if (props != null) PROPS.putAll(props);
    }

    public final String getAppProperty(String key) { return PROPS.get(key); }
    public final void notifyDestroyed() { j2medeck.LinuxLoader.midletDestroyed(); }
    public final void notifyPaused() {}
    public final void resumeRequest() {}
    public final int checkPermission(String permission) { return 1; }
    public final boolean platformRequest(String url) throws javax.microedition.io.ConnectionNotFoundException {
        return j2medeck.LinuxLoader.platformRequest(url);
    }

    // Desktop-loader entry points (protected lifecycle methods are not reachable from another package).
    public final void $startApp() throws MIDletStateChangeException { startApp(); }
    public final void $pauseApp() { pauseApp(); }
    public final void $destroyApp(boolean u) throws MIDletStateChangeException { destroyApp(u); }

    protected abstract void startApp() throws MIDletStateChangeException;
    protected abstract void pauseApp();
    protected abstract void destroyApp(boolean unconditional) throws MIDletStateChangeException;
}
