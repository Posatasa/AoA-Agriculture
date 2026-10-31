package com.posatasa.aoaagriculture.fluid;

import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.state.StateContainer;
import net.minecraftforge.fluids.ForgeFlowingFluid;

/**
 * Chilli water: a fluid of this add-on's own, modelled on AoA's candied water.
 *
 * <p>It is water-like - it flows, it can be swum in, buckets and bottles take it - with one
 * exception that is the point of it: an entity standing in it does not stop burning, and is set on
 * fire. See {@code MixinEntityChilliWater}.
 */
public abstract class ChilliWaterFluid extends ForgeFlowingFluid {
    protected ChilliWaterFluid(Properties properties) {
        super(properties);
    }

    /** The still fluid. A source block is full. */
    public static class Source extends ChilliWaterFluid {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    /** The flowing fluid, which carries a level like vanilla's. */
    public static class Flowing extends ChilliWaterFluid {
        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        protected void createFluidStateDefinition(StateContainer.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }
    }
}
