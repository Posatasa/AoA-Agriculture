package com.posatasa.aoaagriculture.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Refills one of AoA's resources (Spirit, Energy), reached by name at runtime.
 *
 * <p>Like the Farming experience bridge, this deliberately does not compile against AoA: the add-on
 * needs AoA at run time and nothing else. The calls are looked up once per resource and cached:
 *
 * <pre>
 *   AoAResource.Instance resource = PlayerUtil.getResource(player, AoAResources.SPIRIT.get());
 *   resource.setValue(resource.getMaxValue());
 * </pre>
 *
 * <p>The maximum is read from the resource instead of assuming one, so a data pack or an AoA update
 * that changes it still fills to the top. If any lookup fails (a different AoA version), the effect
 * is skipped and logged once - the bucket itself keeps working.
 */
public final class AoAResourceRefill {
    private static final Logger LOGGER = LogManager.getLogger("aoaagriculture");

    private static final String RESOURCES = "net.tslat.aoa3.common.registration.custom.AoAResources";
    private static final String RESOURCE = "net.tslat.aoa3.player.resource.AoAResource";
    private static final String INSTANCE = "net.tslat.aoa3.player.resource.AoAResource$Instance";
    private static final String PLAYER_UTIL = "net.tslat.aoa3.util.PlayerUtil";

    /** One AoA resource this add-on can refill. */
    public enum Kind {
        SPIRIT("SPIRIT"),
        ENERGY("ENERGY");

        private final String field;

        Kind(String field) {
            this.field = field;
        }
    }

    private static final Lookup[] LOOKUPS = new Lookup[Kind.values().length];
    private static boolean resolved;
    private static boolean unavailable;

    private static Method getResource;
    private static Method getMaxValue;
    private static Method setValue;

    /** Fills the player's resource to its maximum. Returns whether it was set. */
    public static boolean refill(ServerPlayerEntity player, Kind kind) {
        if (!resolve()) {
            return false;
        }
        Lookup lookup = LOOKUPS[kind.ordinal()];
        if (lookup == null) {
            return false;
        }
        try {
            Object spirit = lookup.registryObjectGet.invoke(lookup.field.get(null));
            Object instance = getResource.invoke(null, (PlayerEntity) player, spirit);
            if (instance == null) {
                return false;
            }
            float maximum = (Float) getMaxValue.invoke(instance);
            setValue.invoke(instance, maximum);
            return true;
        } catch (Throwable t) {
            disable(t);
            return false;
        }
    }

    private static synchronized boolean resolve() {
        if (resolved) {
            return !unavailable;
        }
        resolved = true;
        try {
            Class<?> resources = Class.forName(RESOURCES);
            Class<?> resource = Class.forName(RESOURCE);
            Class<?> instance = Class.forName(INSTANCE);
            Class<?> playerUtil = Class.forName(PLAYER_UTIL);
            getResource = playerUtil.getMethod("getResource", PlayerEntity.class, resource);
            getMaxValue = instance.getMethod("getMaxValue");
            setValue = instance.getMethod("setValue", float.class);
            for (Kind kind : Kind.values()) {
                Field field = resources.getField(kind.field);
                LOOKUPS[kind.ordinal()] = new Lookup(field, field.getType().getMethod("get"));
            }
            return true;
        } catch (Throwable t) {
            disable(t);
            return false;
        }
    }

    private static void disable(Throwable t) {
        unavailable = true;
        LOGGER.warn("AoA's resources cannot be reached; the resource buckets will not refill anything: "
                + "{}", t.toString());
    }

    /** One resource: AoA's registry field, and the {@code get()} that unwraps it. */
    private static final class Lookup {
        private final Field field;
        private final Method registryObjectGet;

        private Lookup(Field field, Method registryObjectGet) {
            this.field = field;
            this.registryObjectGet = registryObjectGet;
        }
    }

    private AoAResourceRefill() {}
}
