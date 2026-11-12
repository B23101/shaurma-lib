package org.apache.logging.log4j;
public interface Logger { void warn(String m, Object... a); void info(String m, Object... a); void error(String m, Object... a); void debug(String m, Object... a); }
