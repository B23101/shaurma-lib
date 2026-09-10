package dev.shaurmalib.forge.network.packets;

import dev.shaurmalib.common.item.ItemDefinition;
import dev.shaurmalib.common.item.ItemDefinitionRegistry;
import dev.shaurmalib.forge.item.AnimatedGeoItem;
import dev.shaurmalib.forge.item.ItemCameraController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;

import java.util.function.Supplier;

/**
 * Сервер → клієнт: тригерить анімацію на предметі в руці.
 * <p>
 * Перенесено з оригінального {@code ItemAnimPacket.java}, з ОДНІЄЮ
 * принциповою зміною: оригінал мав хардкод
 * {@code instanceof DeminingDeviceItem} прямо в мережевому пакеті ядра —
 * тобто бібліотечний рушій "знав" про конкретний предмет конкретного
 * мода. Тут замість цього рушій дивиться, чи в {@link ItemDefinition}
 * зареєстрований {@code cameraTrack} — якщо так, викликає
 * {@link ItemCameraController} узагальнено, для БУДЬ-ЯКОГО предмета
 * будь-якого мода, без хардкоду класу.
 * <p>
 * {@code animName == ""} означає "скинути до idle" (як в оригіналі).
 */
public class ItemAnimPacket {

    public final InteractionHand hand;
    public final String animName;

    public ItemAnimPacket(InteractionHand hand, String animName) {
        this.hand = hand;
        this.animName = animName;
    }

    public static void encode(ItemAnimPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.hand == InteractionHand.MAIN_HAND);
        buf.writeUtf(pkt.animName, 64);
    }

    public static ItemAnimPacket decode(FriendlyByteBuf buf) {
        InteractionHand hand = buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        String anim = buf.readUtf(64);
        return new ItemAnimPacket(hand, anim);
    }

    public static void handle(ItemAnimPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientHandler.handleClientSide(pkt));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    public static class ClientHandler {
        public static void handleClientSide(ItemAnimPacket pkt) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null) return;

            ItemStack stack = mc.player.getItemInHand(pkt.hand);
            if (stack.isEmpty()) return;

            ItemDefinition<?, ?> def = ItemDefinitionRegistry
                    .<Object, Object>get(stack.getItem().getClass())
                    .orElse(null);

            if (pkt.animName == null || pkt.animName.isEmpty()) {
                // Сесію скасовано достроково (напр. смерть гравця під час анімації) —
                // якщо в цього предмета є камера-трек, плавно її скасовуємо.
                if (def != null && def.cameraTrack != null) {
                    ItemCameraController.cancelActive();
                }
                return;
            }

            if (!(stack.getItem() instanceof GeoItem geoItem)) return;

            long instanceId = GeoItem.getId(stack);
            AnimatableInstanceCache cache = geoItem.getAnimatableInstanceCache();
            AnimatableManager<?> manager = cache.getManagerForId(instanceId);

            if (manager != null) {
                manager.tryTriggerAnimation(AnimatedGeoItem.MAIN_CONTROLLER, pkt.animName);
            }

            // Узагальнена заміна snipers-специфічного "use".equals(animName) && instanceof DeminingDeviceItem:
            // будь-який предмет з зареєстрованим cameraTrack запускає камеру на своєму
            // основному тригер-кліпі (animationName з ItemDefinition), не лише на "use".
            // ПРИМІТКА: якщо предмет використовує startUse(..., animOverride) для програвання
            // ІНШОЇ анімації, ніж def.animationName (як LotteryTicketItem: базове "win",
            // але animOverride="lose" для програшу) — camera-track тут НЕ спрацює для
            // override-кліпу. У snipers жоден предмет з cameraTrack (лише DeminingDeviceItem,
            // "use") animOverride не використовує, тому це не регресія проти оригіналу
            // (оригінал так само хардкодив саме "use"), але при переносі camera-track на
            // предмет з кількома тригер-кліпами це варто врахувати явно.
            if (def != null && def.cameraTrack != null && def.animationName.equals(pkt.animName)) {
                ItemCameraController.trigger(def.cameraTrack);
            }
        }
    }
}