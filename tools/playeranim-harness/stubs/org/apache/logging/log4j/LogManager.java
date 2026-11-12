package org.apache.logging.log4j;
public final class LogManager {
  public static Logger getLogger(String n){ return new Logger(){
    public void warn(String m,Object... a){System.out.println("[WARN "+n+"] "+m);}
    public void info(String m,Object... a){} public void debug(String m,Object... a){} public void error(String m,Object... a){System.out.println("[ERR "+n+"] "+m);} }; }
}
