package org.bonej.wrapperPlugins.wrapperUtils;

public interface UsagePrefsAccess {
    boolean readOptedIn();

    boolean readOptInPrompted();

    int readCookie();

    int readCookie2();

    long readFirstTime();

    int readSessionKey();

    void writeSessionKey(final int key);
}
