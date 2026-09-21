package net.minecraft.resources;
public class ResourceLocation { public final String ns,path; public ResourceLocation(String n,String p){ns=n;path=p;}
 public boolean equals(Object o){return o instanceof ResourceLocation r && r.ns.equals(ns)&&r.path.equals(path);} public int hashCode(){return ns.hashCode()*31+path.hashCode();} public String toString(){return ns+":"+path;} }
