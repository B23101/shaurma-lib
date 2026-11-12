package dev.shaurmalib.platform.event;

public interface Cancellable extends Event {
    boolean isCancelled();
    void cancel();
}
