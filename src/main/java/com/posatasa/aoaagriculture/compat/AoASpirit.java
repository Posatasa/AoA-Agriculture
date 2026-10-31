package com.posatasa.aoaagriculture.compat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Refills AoA's Spirit resource, reached by name at runtime.
 *
 * <p>Like the Farming experience bridge, this deliberately does not compile against AoA: the add-on
 * needs AoA at run time and nothing else. The four calls are looked up once and cached:
 *
 * <pre>
 *   AoAResource.Instance spirit = PlayerUtil.getResource(player, AoAResources.SPIRIT.get());
 *   spirit.setValue(spirit.getMaxValue());
 * </pre>
 *
 * <p>The maximum is read from the resource instead of assuming one, so a resource pack or an AoA
 * update that changes it still fills to the top. If any of the lookups fails (a different AoA
 * version), the effect is skipped and logged once - the bucket itself keeps working.
 */
public final class AoASpirit {
    private static final Logger LOGGER = LogManager.getLogger("aoaagriculture");

    private static final String RESOURCES = "net.tslat.aoa3.common.registration.custom.AoAResources";
    private static final String RESOURCE = "net.tslat.aoa3.player.resource.AoAResource";
    private static final String INSTANCE = "net.tslat.aoa3.player.resource.AoAResource$Instance";
    private static final String PLAYER_UTIL = "net.tslat.aoa3.util.PlayerUtil";

    private static boolean resolved;
    private static boolean unavailable;
    private static Field spiritField;
    private static Method registryObjectGet;
    private static Method getResource;
    private static Method getMaxValue;
    private static Method setValue;

    /** Sets the player's Spirit to its maximum. Returns whether it was set. */
    public static boolean refill(ServerPlayerEntity player) {
        if (!resolve()) {
            return false;
        }
        try {
            Object holder = spiritField.get(null);
            Object spirit = registryObjectGet.invoke(holder);
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
            spiritField = resources.getField("SPIRIT");
            registryObjectGet = spiritField.getType().getMethod("get");
            getResource = playerUtil.getMethod("getResource", PlayerEntity.class, resource);
            getMaxValue = instance.getMethod("getMaxValue");
            setValue = instance.getMethod("setValue", float.class);
            return true;
        } catch (Throwable t) {
            disable(t);
            return false;
        }
    }

    private static void disable(Throwable t) {
        unavailable = true;
        LOGGER.warn("AoA's Spirit resource cannot be reached; the Elecanium water bucket will not "
                + "refill Spirit: {}", t.toString());
    }

    private AoASpirit() {}
}
