package org.bonej.wrapperPlugins.wrapperUtils;

import org.scijava.prefs.PrefService;

import java.util.Random;

public class UsageIJ2PrefsAccess implements UsagePrefsAccess {
    private final PrefService prefs;
    private Random random = new Random();
    private static final Class<?> ASSOCIATED_CLASS = UsageReporterOptions.class;

    public UsageIJ2PrefsAccess(final PrefService prefService) {
        if (prefService == null) {
            throw new NullPointerException("PrefService cannot be null");
        }
        this.prefs = prefService;
    }

    @Override
    public boolean readOptedIn() {
        return prefs.getBoolean(ASSOCIATED_CLASS, UsageReporterOptions.OPTINKEY, false);
    }

    @Override
    public boolean readOptInPrompted() {
        return prefs.getBoolean(ASSOCIATED_CLASS, UsageReporterOptions.OPTINSET, false);
    }

    @Override
    public int readCookie() {
        return prefs.getInt(ASSOCIATED_CLASS, UsageReporterOptions.COOKIE, random.nextInt());
    }

    @Override
    public int readCookie2() {
        return prefs.getInt(ASSOCIATED_CLASS, UsageReporterOptions.COOKIE2, random.nextInt());
    }

    @Override
    public long readFirstTime() {
        return prefs.getInt(ASSOCIATED_CLASS, UsageReporterOptions.FIRSTTIMEKEY, random.nextInt());
    }

    @Override
    public int readSessionKey() {
        return prefs.getInt(UsageReporterOptions.class, UsageReporterOptions.SESSIONKEY, 0);
    }

    @Override
    public void writeSessionKey(final int key) {
        prefs.put(UsageReporterOptions.class, UsageReporterOptions.SESSIONKEY, key);
    }
}
