package com.example.globalminusone.browser;

/** Keep an existing display powered while its host output is replaced. */
public final class EmbeddedOutputTransition {
    private EmbeddedOutputTransition() {}
    public interface Port<S> {
        void setTaskHidden(boolean hidden) throws Exception;
        void installOutput(S output) throws Exception;
    }
    public static <S> void replace(S output, boolean hidden, Port<S> port) throws Exception {
        if (output == null) throw new IllegalArgumentException("A live display needs a non-null output");
        // Lose focus before parking; regain it only after the real output exists.
        if (hidden) port.setTaskHidden(true);
        port.installOutput(output);
        if (!hidden) port.setTaskHidden(false);
    }
}
