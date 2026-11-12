package dev.shaurmalib.forge.graffiti;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Базовий декоративний блок-носій графіті (план, п. 3.14) — узагальнення
 * {@code GraffitiBlock} snipers_shaurma (161 рядок), 1:1 логіка форми,
 * розміщення й хітбоксу.
 * <p>
 * ВАЖЛИВО: цей блок НЕ має власної моделі і НЕ рендериться через
 * {@code BlockEntityRenderer}. Картинка малюється повністю окремо —
 * клієнтським world-рендерером на {@code RenderLevelStageEvent} (як
 * лідерборд-голограма з {@code LeaderboardWorldRenderer}, план п. 3.21,
 * не використовує BER, а малює текстуру напряму в
 * {@code AFTER_TRANSLUCENT_BLOCKS}). <b>Цей рендерер, клієнтський кеш
 * ({@code GraffitiClientCache}) і canvas-редактор
 * ({@code GraffitiMenuScreen}, 1164 рядки) НЕ входять у цей етап
 * переносу</b> — навмисно залишені продуктовим кодом консюмера/наступним
 * етапом бібліотеки, бо це окремий великий об'єм роботи (world-рендер +
 * GUI canvas), а серверне ядро (блок, дані, файлове сховище, передача
 * зображень) вже самодостатнє й корисне без них.
 * <p>
 * Блок сам по собі:
 * <ul>
 *   <li>невидимий ({@code RenderShape.INVISIBLE});</li>
 *   <li>має тонкий хітбокс (0.1 блока) впритул до стіни, з якою межує;</li>
 *   <li>непрохідний ({@code getCollisionShape == getShape});</li>
 *   <li>розміщується лише на вертикальній поверхні ({@code FACING} =
 *       сторона стіни, до якої кріпиться).</li>
 * </ul>
 * <p>
 * <b>Консюмер надає:</b> {@link #newBlockEntity(BlockPos, BlockState)}
 * (повертає власний підклас {@link GraffitiBlockEntityBase}) і
 * {@link #openMenu(ServerPlayer, BlockPos)} (відкриває власний UI
 * налаштування — canvas-редактор лишається продуктовим кодом консюмера
 * в цьому переносі, як описано вище).
 */
public abstract class GraffitiBlockBase extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Товщина хітбоксу вздовж нормалі стіни. */
    private static final double THICKNESS = 0.10;

    private static final VoxelShape SHAPE_NORTH = Shapes.box(0.0, 0.0, 1.0 - THICKNESS, 1.0, 1.0, 1.0);
    private static final VoxelShape SHAPE_SOUTH = Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, THICKNESS);
    private static final VoxelShape SHAPE_WEST = Shapes.box(1.0 - THICKNESS, 0.0, 0.0, 1.0, 1.0, 1.0);
    private static final VoxelShape SHAPE_EAST = Shapes.box(0.0, 0.0, 0.0, THICKNESS, 1.0, 1.0);

    protected GraffitiBlockBase() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .strength(1.0f, 6.0f)
                .noOcclusion()
                .noCollission() // базово вимкнено, повертаємо явно нижче через getCollisionShape
                .pushReaction(PushReaction.DESTROY)
                .isRedstoneConductor((s, g, p) -> false)
                .isSuffocating((s, g, p) -> false)
                .isViewBlocking((s, g, p) -> false)
        );
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // ── BlockState ───────────────────────────────────────────────────────

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // FACING = сторона грані, по якій клікнули (напрям "геть від стіни").
        Direction clickedFace = ctx.getClickedFace();
        if (clickedFace.getAxis().isVertical()) return null; // тільки вертикальні поверхні
        return defaultBlockState().setValue(FACING, clickedFace);
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing);
    }

    // ── Shape ────────────────────────────────────────────────────────────

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // Непрохідний — та сама форма, що й хітбокс візуальної взаємодії.
        return getShape(state, level, pos, ctx);
    }

    // ── Render ───────────────────────────────────────────────────────────

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Блок сам по собі нічого не малює — вся картинка йде через
        // world-renderer консюмера (RenderLevelStageEvent), не через BER.
        return RenderShape.INVISIBLE;
    }

    // ── Interaction (ПКМ у креативі → меню налаштувань консюмера) ─────────

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!player.isCreative()) return InteractionResult.PASS;

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp) {
            openMenu(sp, pos);
        }
        return InteractionResult.CONSUME;
    }

    /** Консюмер відкриває власне меню налаштувань графіті (canvas-редактор
     *  чи будь-який інший UI) — рушій лібу лише детектує клік у креативі і
     *  делегує сюди, не диктує сам UI. */
    protected abstract void openMenu(ServerPlayer player, BlockPos pos);

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof GraffitiBlockEntityBase gbe && !level.isClientSide) {
                GraffitiSyncManager.onBlockRemoved(level, gbe);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
