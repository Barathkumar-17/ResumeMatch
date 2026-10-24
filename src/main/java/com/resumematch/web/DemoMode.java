package com.resumematch.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.LongSupplier;

import javax.servlet.ServletContext;

/**
 * Tells whether the site runs as a public live demo, in which new sign-ups are paused.
 * The mode is on when the {@code RESUMEMATCH_DEMO_MODE} environment variable (which wins) or
 * the {@code demoMode} context parameter is {@code true}, read once at startup, or while the
 * flag file {@code <catalina.base>/resumematch-demo-mode.flag} exists. The flag file is looked
 * up at most every 10 seconds, so the share scripts can switch the mode on and off without
 * restarting Tomcat.
 *
 * <p>Demonstrates: {@code java.nio.file.Files}, a small time-based cache, {@code LongSupplier}
 * (so a test can control the clock), a {@code ServletContext} attribute.</p>
 */
public final class DemoMode {

    /** Name of the context attribute that holds the single instance. */
    public static final String CONTEXT_ATTRIBUTE = "demoMode";

    /** Environment variable that switches the mode on or off; wins over the context parameter. */
    public static final String ENV_NAME = "RESUMEMATCH_DEMO_MODE";

    /** Context parameter in web.xml. */
    public static final String PARAM_NAME = "demoMode";

    /** File name of the flag file inside {@code catalina.base}. */
    public static final String FLAG_FILE_NAME = "resumematch-demo-mode.flag";

    /** Friendly text shown instead of the register form. */
    public static final String SIGNUP_PAUSED_MESSAGE =
            "New sign-ups are paused in this live demo. Log in with the demo account: "
            + "demo@resumematch.in / demo1234";

    static final long CHECK_INTERVAL_MILLIS = 10_000;

    private final boolean alwaysOn;
    private final Path flagFile;
    private final LongSupplier clockMillis;
    private boolean flagPresent;
    private long lastCheck;
    private boolean checkedOnce;

    public DemoMode(boolean alwaysOn, Path flagFile, LongSupplier clockMillis) {
        this.alwaysOn = alwaysOn;
        this.flagFile = flagFile;
        this.clockMillis = clockMillis;
    }

    /** The environment value, when it is set, wins over the context parameter. */
    public static boolean parseSwitch(String envValue, String paramValue) {
        String value = (envValue != null && !envValue.trim().isEmpty()) ? envValue : paramValue;
        return value != null && value.trim().equalsIgnoreCase("true");
    }

    /** Reads the settings once and keeps the instance in the servlet context. */
    public static DemoMode install(ServletContext context, Path flagFile) {
        boolean on = parseSwitch(System.getenv(ENV_NAME), context.getInitParameter(PARAM_NAME));
        DemoMode mode = new DemoMode(on, flagFile, System::currentTimeMillis);
        context.setAttribute(CONTEXT_ATTRIBUTE, mode);
        return mode;
    }

    /** True when the live-demo mode is on for this context (false when it was never installed). */
    public static boolean isOn(ServletContext context) {
        Object mode = context.getAttribute(CONTEXT_ATTRIBUTE);
        return mode instanceof DemoMode && ((DemoMode) mode).isActive();
    }

    /** True when the switch is on or the flag file exists (checked at most every 10 seconds). */
    public synchronized boolean isActive() {
        if (alwaysOn) {
            return true;
        }
        if (flagFile == null) {
            return false;
        }
        long now = clockMillis.getAsLong();
        if (!checkedOnce || now - lastCheck >= CHECK_INTERVAL_MILLIS) {
            flagPresent = Files.exists(flagFile);
            lastCheck = now;
            checkedOnce = true;
        }
        return flagPresent;
    }
}
