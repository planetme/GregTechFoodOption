package gregtechfoodoption.block;

import gregtech.api.recipes.ModHandler;
import gregtech.api.util.function.TriConsumer;
import gregtechfoodoption.block.tree.GTFOBlockLeaves;
import gregtechfoodoption.block.tree.GTFOBlockLog;
import gregtechfoodoption.block.tree.GTFOBlockSapling;
import gregtechfoodoption.utils.GTFOLog;
import gregtechfoodoption.utils.GTFOUtils;
import gregtechfoodoption.worldgen.trees.BiomeCondition;
import gregtechfoodoption.worldgen.trees.GTFOTreeGen;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.gen.NoiseGeneratorSimplex;
import net.minecraftforge.common.IPlantable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.minecraft.block.BlockLeaves.CHECK_DECAY;
import static net.minecraft.block.BlockLeaves.DECAYABLE;

public abstract class GTFOTree {
    public final String name;
    protected GTFOTreeGen TREE_GROW_INSTANCE;
    protected GTFOTreeGen WORLD_GEN_INSTANCE;

    private int totalChunksChecked;
    private int totalChunksPlaced;

    public IBlockState logState;
    public IBlockState leavesState;
    public IBlockState saplingState;

    private NoiseGeneratorSimplex generatorSimplex;
    private final int seed;
    public final List<BiomeCondition> biomeConditions = new ArrayList<>();

    public static final List<GTFOTree> TREES = new ArrayList<>();

    public GTFOTree(String name, int seed) {
        this.name = name;
        this.seed = seed;
        this.TREE_GROW_INSTANCE = new GTFOTreeGen(true, this);
        this.WORLD_GEN_INSTANCE = new GTFOTreeGen(false, this);
        TREES.add(this);
    }

    public void setWorld(World world) {
        generatorSimplex = new NoiseGeneratorSimplex(new Random(world.getSeed() + seed));
    }

    public double getRandomStrength(int chunkX, int chunkZ) {
        return generatorSimplex.getValue(chunkX * 0.05, chunkZ * 0.05);
    }

    // For testing purposes only.
    public void updatePlacePercentage(boolean didSucceed) {
        totalChunksChecked++;
        if (didSucceed) {
            totalChunksPlaced++;
        }
        if (totalChunksChecked % 1000 == 0) {
            GTFOLog.logger.info("Tree " + this.name + " has been placed successfully in chunks " + ((double) totalChunksPlaced / (totalChunksChecked / 100)) + " percent of the time out of " + totalChunksChecked + " chunks checked");
        }
    }

    public boolean grow(World world, BlockPos.MutableBlockPos pos, Random random, TriConsumer<World, BlockPos, IBlockState> notifier) {
        int minHeight = getMinTrunkHeight(random);

        // Check if tree fits in world
        if (pos.getY() >= 1 && pos.getY() + minHeight + 1 <= world.getHeight()) {
            if (isSuitableLocation(world, pos, minHeight)) {
                IBlockState state = world.getBlockState(pos.down());
                if (state.getBlock().canSustainPlant(state, world, pos.down(), EnumFacing.UP, this.getPlantableSapling()) && pos.getY() < world.getHeight() - minHeight - 1) {
                    state.getBlock().onPlantGrow(state, world, pos.down(), pos);
                    generateLeaves(world, pos, minHeight, random, notifier);
                    generateTrunk(world, pos, minHeight, notifier);
                    return true;
                }
            }
        }
        return false;
    }

    public int getMinTrunkHeight(Random random) {
        return random.nextInt(3) + 5;
    }

    public GTFOTreeGen getTreeGrowInstance() {
        return TREE_GROW_INSTANCE;
    }

    public GTFOTreeGen getWorldGenInstance() {
        return WORLD_GEN_INSTANCE;
    }

    public GTFOTree addCondition(BiomeCondition condition) {
        biomeConditions.add(condition);
        return this;
    }

    public void setupBlocks() {
        GTFOBlockLeaves leaves = GTFOMetaBlocks.GTFO_LEAVES.get(seed / 4);
        this.leavesState = leaves.getStateFromMeta(seed % 4 << 2);
        GTFOBlockLog log = GTFOMetaBlocks.GTFO_LOGS.get(seed / 4);
        this.logState = log.getStateFromMeta(seed % 4 << 2);
        GTFOBlockSapling sapling = GTFOMetaBlocks.GTFO_SAPLINGS.get(seed / 8);
        this.saplingState = sapling.getStateFromMeta(seed % 8 << 1);
    }

    public IPlantable getPlantableSapling() {
        return (IPlantable) this.saplingState.getBlock();
    }

    public abstract int getBlockColor(IBlockState state, IBlockAccess worldIn, BlockPos pos, int tintIndex);
    public abstract int getItemColor(ItemStack stack, int tintIndex);

    public boolean isReplaceable(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return this.canGrowInto(state.getBlock());
    }

    protected boolean canGrowInto(Block blockType) {
        Material material = blockType.getDefaultState().getMaterial();
        return material == Material.AIR || material == Material.LEAVES || blockType == Blocks.GRASS || blockType == Blocks.DIRT || blockType == Blocks.LOG || blockType == Blocks.LOG2 || blockType == Blocks.SAPLING || blockType == Blocks.VINE;
    }

    protected void generateLeaves(World world, BlockPos.MutableBlockPos pos, int height, Random random, TriConsumer<World, BlockPos, IBlockState> notifier) {
        for (int foliageY = pos.getY() - 3 + height; foliageY <= pos.getY() + height; ++foliageY) {
            int foliageLayer = foliageY - (pos.getY() + height);
            int foliageLayerRadius = 1 - foliageLayer / 2;

            for (int foliageX = pos.getX() - foliageLayerRadius; foliageX <= pos.getX() + foliageLayerRadius; ++foliageX) {
                int foliageRelativeX = foliageX - pos.getX();

                for (int foliageZ = pos.getZ() - foliageLayerRadius; foliageZ <= pos.getZ() + foliageLayerRadius; ++foliageZ) {
                    int foliageRelativeZ = foliageZ - pos.getZ();

                    // Fill in layer with some randomness
                    if (Math.abs(foliageRelativeX) != foliageLayerRadius || Math.abs(foliageRelativeZ) != foliageLayerRadius || random.nextInt(2) != 0 && foliageLayer != 0) {
                        BlockPos newLeavesPos = new BlockPos(foliageX, foliageY, foliageZ);
                        IBlockState state = world.getBlockState(newLeavesPos);

                        if (state.getBlock().isReplaceable(world, pos) || state.getBlock().canBeReplacedByLeaves(state, world, pos)) {
                            notifier.accept(world, newLeavesPos, this.leavesState);
                        }
                    }
                }
            }
        }
    }

    protected void generateTrunk(World world, BlockPos.MutableBlockPos pos, int minHeight, TriConsumer<World, BlockPos, IBlockState> notifier) {
        BlockPos.MutableBlockPos upN = GTFOUtils.copy(pos);
        for (int height = 0; height < minHeight; ++height) {
            upN.move(EnumFacing.UP);
            IBlockState state = world.getBlockState(upN);

            if (state.getBlock().isAir(state, world, upN) || state.getBlock().isLeaves(state, world, upN)) {
                notifier.accept(world, pos.up(height), logState.withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.Y));
            }
        }
    }

    protected boolean isSuitableLocation(World world, BlockPos pos, int minHeight) {
        for (int height = 0; height <= 1 + minHeight; ++height) {
            // Handle increasing space towards top of tree
            int extraSpaceNeeded = getMooreRadiusAtHeight(height, minHeight);

            BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

            for (int checkX = pos.getX() - extraSpaceNeeded; checkX <= pos.getX() + extraSpaceNeeded; ++checkX) {
                for (int checkZ = pos.getZ() - extraSpaceNeeded; checkZ <= pos.getZ() + extraSpaceNeeded; ++checkZ) {
                    if (!isReplaceable(world, blockPos.setPos(checkX, height + pos.getY(), checkZ))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * @param height An integer representing the block height at which this radius is being taken (starting from 0).
     * @param trunkHeight An integer representing the height of the trunk.
     * @return The maximum radius outside the center block that the tree can take up at this height value.
     */
    protected abstract int getMooreRadiusAtHeight(int height, int trunkHeight);

    public void initRecipes() {
        ModHandler.addShapelessRecipe(this.name + "_wood_planks",
                new ItemStack(GTFOMetaBlocks.GTFO_PLANKS.get(seed / 16), 4, seed % 16),
                new ItemStack(GTFOMetaBlocks.GTFO_LOGS.get(seed / 4), 1, (seed % 4) << 2));
    }

    public ItemStack getApple() {
        return ItemStack.EMPTY;
    };

    protected IBlockState getNaturalLeavesState() {
        return this.leavesState.withProperty(DECAYABLE, true).withProperty(CHECK_DECAY, true);
    }
}