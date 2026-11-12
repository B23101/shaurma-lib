package dev.kosmx.playerAnim.minecraftApi;
import net.minecraft.client.player.AbstractClientPlayer;
public final class PlayerAnimationAccess { public static dev.kosmx.playerAnim.api.layered.AnimationStack getPlayerAnimLayer(AbstractClientPlayer p){ return p.stack; } }
