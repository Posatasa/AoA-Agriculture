import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Reproduces Forge's own listener check for every event type this mod listens to.
 *
 * `EventBus#addListener` throws "Cannot register a generic event listener with addListener, use
 * addGenericListener" for any event that extends {@code GenericEvent}, and that exception happens
 * while the mod is being constructed - i.e. it takes the whole game down at startup. This walks the
 * mod's own listener methods and applies the same rule, so the mistake is caught by the build
 * rather than by the game.
 */
public class VerifyListeners {
    public static void main(String[] args) throws Exception {
        Class<?> genericEvent = Class.forName("net.minecraftforge.eventbus.api.GenericEvent");
        // initialize=false: only the signatures are needed, and running the static initialiser
        // would register blocks outside a mod-loading context.
        String[] holders = {
                "com.posatasa.aoaagriculture.registry.FarmlandBlocks",
                "com.posatasa.aoaagriculture.registry.Buckets",
                "com.posatasa.aoaagriculture.registry.Bottles",
        };
        int checked = 0;
        int failures = 0;
        for (String holder : holders) {
        Class<?> mod = Class.forName(holder, false, VerifyListeners.class.getClassLoader());
        for (Method method : mod.getDeclaredMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            Parameter parameter = method.getParameters()[0];
            Class<?> event = parameter.getType();
            if (!Class.forName("net.minecraftforge.eventbus.api.Event").isAssignableFrom(event)) {
                continue;
            }
            checked++;
            boolean generic = genericEvent.isAssignableFrom(event);
            System.out.printf("%-28s listens for %-60s generic=%s%n",
                    method.getName(), event.getName(), generic);
            if (generic) {
                System.out.println("  FAIL: addListener would throw for this event type");
                failures++;
            }
        }
        }
        System.out.println(failures == 0
                ? "OK: all " + checked + " listeners are registrable with addListener"
                : failures + " generic listener(s) would crash mod loading");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
