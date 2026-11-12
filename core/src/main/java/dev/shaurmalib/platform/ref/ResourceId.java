package dev.shaurmalib.platform.ref;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Ідентифікатор ресурсу {@code namespace:path}. У 1.20.1/1.21.1 це
 * {@code ResourceLocation}, у 26.x — {@code Identifier}; ядро знає лише цей тип,
 * а адаптер версії конвертує в рідний.
 */
public record ResourceId(String namespace, String path) {
    private static final Pattern NS = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    public ResourceId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NS.matcher(namespace).matches()) throw new IllegalArgumentException("bad namespace: " + namespace);
        if (!PATH.matcher(path).matches()) throw new IllegalArgumentException("bad path: " + path);
    }

    public static ResourceId of(String ns, String path) { return new ResourceId(ns, path); }

    public static ResourceId parse(String s) {
        int i = s.indexOf(':');
        return i < 0 ? new ResourceId("minecraft", s) : new ResourceId(s.substring(0, i), s.substring(i + 1));
    }

    @Override public String toString() { return namespace + ':' + path; }
}
