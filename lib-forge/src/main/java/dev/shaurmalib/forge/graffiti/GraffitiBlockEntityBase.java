package dev.shaurmalib.forge.graffiti;

import dev.shaurmalib.common.graffiti.GraffitiSpec;
import dev.shaurmalib.common.graffiti.GraffitiTextLine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Базовий {@code BlockEntity} для графіті (план, п. 3.14) — узагальнення
 * {@code GraffitiBlockEntity} snipers_shaurma (193 рядки). Тримає лише
 * дані ({@link GraffitiSpec}), ніякого рендер-стану — так само, як і
 * оригінал: сам блок невидимий ({@code RenderShape.INVISIBLE}), картинка
 * малюється клієнтським world-рендерером окремо (не через
 * {@code BlockEntityRenderer}) — той рендерер лишається продуктовим кодом
 * консюмера в цьому переносі (див. клас-докстрінг {@link GraffitiBlockBase}
 * для повного списку того, що НЕ входить у цей етап).
 * <p>
 * <b>Консюмер надає лише:</b> {@code EntityType<GraffitiBlockEntityBase>}
 * через власний {@code BlockEntityType.Builder} (як і решта GeckoLib/
 * animated-block модулів лібу — база не має власного реєстру екземплярів,
 * той самий принцип, що {@code AnimatedBlockEntityBase}).
 * <p>
 * <b>Синхронізація:</b> {@link #markDirtyAndSync()} викликає
 * {@link GraffitiSyncManager#syncBlock} при кожній зміні поля — той самий
 * підхід, що в оригіналі: НЕ покладаємось лише на
 * {@code ClientboundBlockEntityDataPacket} (хоча {@link #getUpdatePacket()}
 * і тут реалізований коректно про всяк випадок для ванільних механізмів
 * типу {@code /reload}), а явно шлемо власний легкий {@code GraffitiSyncPacket}
 * — так клієнтський кеш рендера наповнюється детерміновано і одразу
 * містить fingerprint файлу для порівняння з кешем.
 */
public abstract class GraffitiBlockEntityBase extends BlockEntity {

    private final GraffitiSpec spec = new GraffitiSpec();

    protected GraffitiBlockEntityBase(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Прямий доступ до поточного стану — читання (без мутації ззовні,
     *  мутація завжди йде через сеттери нижче, щоб не пропустити sync). */
    public GraffitiSpec spec() {
        return spec;
    }

    // ── Мутатори (кожен викликає markDirtyAndSync, як в оригіналі) ──────

    public void setContentMode(dev.shaurmalib.common.graffiti.GraffitiContentMode mode) {
        spec.setContentMode(mode);
        markDirtyAndSync();
    }

    public void setImageName(String imageName) {
        spec.setImageName(imageName);
        markDirtyAndSync();
    }

    public void setTextLines(List<GraffitiTextLine> lines) {
        spec.setTextLines(lines);
        markDirtyAndSync();
    }

    public void setScale(float scale) {
        spec.setScale(scale);
        markDirtyAndSync();
    }

    public void setOffsets(float offsetX, float offsetY) {
        spec.setOffsets(offsetX, offsetY);
        markDirtyAndSync();
    }

    public void setRotationDeg(float rotationDeg) {
        spec.setRotationDeg(rotationDeg);
        markDirtyAndSync();
    }

    public void rotate(int direction) {
        setRotationDeg(spec.rotationDeg() + direction * 15f);
    }

    /** Сторона стіни, до якої кріпиться графіті — дублює
     *  {@code BlockState.FACING} для зручності мережі/кешу, як в оригіналі.
     *  Консюмер передає властивість {@code FACING} свого блоку сюди. */
    public abstract Direction getSide();

    /** Позначає чанк брудним і одразу штовхає стан клієнтам, що бачать
     *  чанк. Викликається з кожного сеттера вище — консюмеру не потрібно
     *  пам'ятати про це самому. */
    protected void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            GraffitiSyncManager.syncBlock(level, this);
        }
    }

    // ── NBT ──────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        GraffitiCodec.saveSpec(tag, spec);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        GraffitiCodec.loadSpec(tag, spec);
    }

    // ── Мережа: повний стейт разом з блоком (chunk (re)send, /reload і т.п.) ──

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }
}
