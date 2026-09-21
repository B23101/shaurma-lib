package dev.kosmx.playerAnim.minecraftApi;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
public final class PlayerAnimationRegistry { public static final Map<ResourceLocation, dev.kosmx.playerAnim.core.data.KeyframeAnimation> M=new HashMap<>();
  public static dev.kosmx.playerAnim.core.data.KeyframeAnimation getAnimation(ResourceLocation id){ return M.get(id);} }
