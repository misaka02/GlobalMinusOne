package com.example.globalminusone.browser;

/** Unknown/custom version strings fail the existing compatibility gate without parse crashes. */
public final class EmbeddedBrowserVersion {
    private EmbeddedBrowserVersion() { }
    public static int major(String version) {
        if (version == null) return 0;
        String first = version.trim().split("\\.", 2)[0];
        if (first.isEmpty() || first.length() > 9) return 0;
        for (int i = 0; i < first.length(); i++)
            if (first.charAt(i) < '0' || first.charAt(i) > '9') return 0;
        try { return Integer.parseInt(first); }
        catch (NumberFormatException unsupported) { return 0; }
    }
}
