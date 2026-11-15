package com.example.globalminusone.browser;

/** Cleanup may target only this session's marked launch, never a preexisting task. */
public final class ChromeTaskOwnership {
    private ChromeTaskOwnership() {}
    public static boolean belongsToSession(String session, String taskSession, boolean preexistingTask,
                                           String chromePackage, String taskPackage) {
        return !preexistingTask && session != null && chromePackage != null && chromePackage.equals(taskPackage) &&
                session.equals(taskSession);
    }
}
