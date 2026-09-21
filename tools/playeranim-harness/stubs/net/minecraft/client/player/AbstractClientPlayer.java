package net.minecraft.client.player;
import java.util.UUID;
public class AbstractClientPlayer { public final UUID uuid; public boolean removed=false; public dev.kosmx.playerAnim.api.layered.AnimationStack stack=new dev.kosmx.playerAnim.api.layered.AnimationStack();
  public AbstractClientPlayer(UUID u){uuid=u;} public UUID getUUID(){return uuid;} public boolean isRemoved(){return removed;} }
