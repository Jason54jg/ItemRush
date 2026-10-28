package com.jason54.itemrush.client;

import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Reflective bridge over GuiGraphics (1.21.x) and GuiGraphicsExtractor (26.x).
 * <p>
 * Resolves draw methods by <em>signature</em> as well as by name, so production
 * Fabric/NeoForge jars (intermediary / SRG names) still work.
 */
public final class ReflectiveHudGraphics implements HudGraphics {

    private final Object graphics;
    private Method fillMethod;
    private Method textMethod;
    private Method centeredTextMethod;
    private Method itemMethod;
    private Object guiPipeline; // RenderPipelines.GUI when fill needs a pipeline arg

    private ReflectiveHudGraphics(Object graphics) {
        this.graphics = unwrap(graphics);
        resolveMethods();
    }

    public static HudGraphics wrap(Object graphics) {
        return new ReflectiveHudGraphics(graphics);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        if (fillMethod == null) {
            throw missing("fill", "5 ints, or RenderPipeline + 5 ints");
        }
        try {
            if (fillMethod.getParameterCount() == 5) {
                fillMethod.invoke(graphics, x1, y1, x2, y2, argb);
            } else {
                fillMethod.invoke(graphics, guiPipeline, x1, y1, x2, y2, argb);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("HUD fill failed", e);
        }
    }

    @Override
    public void text(Font font, String value, int x, int y, int argb, boolean shadow) {
        if (textMethod == null) {
            throw missing("text/drawString", "Font, String, int, int, int[, boolean]");
        }
        try {
            if (textMethod.getParameterCount() == 6) {
                textMethod.invoke(graphics, font, value, x, y, argb, shadow);
            } else {
                textMethod.invoke(graphics, font, value, x, y, argb);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("HUD text failed", e);
        }
    }

    @Override
    public void centeredText(Font font, String value, int x, int y, int argb) {
        if (centeredTextMethod != null) {
            try {
                centeredTextMethod.invoke(graphics, font, value, x, y, argb);
                return;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("HUD centeredText failed", e);
            }
        }
        // Fallback: manual center using width + text()
        int width = font.width(value);
        text(font, value, x - width / 2, y, argb, false);
    }

    @Override
    public void item(ItemStack stack, int x, int y) {
        if (itemMethod == null) {
            throw missing("item/renderItem", "ItemStack, int, int");
        }
        try {
            itemMethod.invoke(graphics, stack, x, y);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("HUD item failed", e);
        }
    }

    private void resolveMethods() {
        Class<?> clazz = graphics.getClass();
        Method[] methods = clazz.getMethods();

        fillMethod = findByNames(methods, new String[]{"fill"},
                params -> params.length == 5 && allInts(params));
        if (fillMethod == null) {
            // 26.x / newer: fill(RenderPipeline, int, int, int, int, int)
            fillMethod = findByNames(methods, new String[]{"fill"},
                    params -> params.length == 6
                            && !params[0].isPrimitive()
                            && allInts(Arrays.copyOfRange(params, 1, 6)));
            if (fillMethod != null) {
                guiPipeline = resolveGuiPipeline();
                if (guiPipeline == null) {
                    fillMethod = null;
                }
            }
        }
        if (fillMethod == null) {
            // Last resort: any 5-int public instance method (named or intermediary)
            fillMethod = findFirst(methods, params -> params.length == 5 && allInts(params));
        }
        if (fillMethod == null) {
            fillMethod = findFirst(methods, params -> params.length == 6
                    && !params[0].isPrimitive()
                    && allInts(Arrays.copyOfRange(params, 1, 6)));
            if (fillMethod != null) {
                guiPipeline = resolveGuiPipeline();
                if (guiPipeline == null) {
                    fillMethod = null;
                }
            }
        }

        textMethod = findByNames(methods, new String[]{"text", "drawString", "drawText"},
                params -> params.length == 6
                        && isFontLike(params[0])
                        && params[1] == String.class
                        && allInts(Arrays.copyOfRange(params, 2, 5))
                        && params[5] == boolean.class);
        if (textMethod == null) {
            textMethod = findByNames(methods, new String[]{"text", "drawString", "drawText", "drawTextWithShadow"},
                    params -> params.length == 5
                            && isFontLike(params[0])
                            && params[1] == String.class
                            && allInts(Arrays.copyOfRange(params, 2, 5)));
        }
        if (textMethod == null) {
            textMethod = findFirst(methods, params -> params.length == 6
                    && isFontLike(params[0])
                    && params[1] == String.class
                    && allInts(Arrays.copyOfRange(params, 2, 5))
                    && params[5] == boolean.class);
        }
        if (textMethod == null) {
            textMethod = findFirst(methods, params -> params.length == 5
                    && isFontLike(params[0])
                    && params[1] == String.class
                    && allInts(Arrays.copyOfRange(params, 2, 5)));
        }

        centeredTextMethod = findByNames(methods, new String[]{"centeredText", "drawCenteredString", "drawCenteredText"},
                params -> params.length == 5
                        && isFontLike(params[0])
                        && params[1] == String.class
                        && allInts(Arrays.copyOfRange(params, 2, 5)));

        itemMethod = findByNames(methods, new String[]{"item", "renderItem", "drawItem"},
                params -> params.length == 3
                        && ItemStack.class.isAssignableFrom(params[0])
                        && params[1] == int.class
                        && params[2] == int.class);
        if (itemMethod == null) {
            itemMethod = findFirst(methods, params -> params.length == 3
                    && ItemStack.class.isAssignableFrom(params[0])
                    && params[1] == int.class
                    && params[2] == int.class);
        }
    }

    private static Object unwrap(Object graphics) {
        if (graphics == null) {
            throw new IllegalArgumentException("gui graphics is null");
        }
        // Some wrappers expose the real context via guiGraphics() / getGuiGraphics()
        for (String name : new String[]{"guiGraphics", "getGuiGraphics", "graphics", "getGraphics", "asGuiGraphics"}) {
            try {
                Method m = graphics.getClass().getMethod(name);
                if (m.getParameterCount() == 0 && m.getReturnType() != void.class) {
                    Object inner = m.invoke(graphics);
                    if (inner != null && inner != graphics) {
                        return inner;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // continue
            }
        }
        return graphics;
    }

    private Object resolveGuiPipeline() {
        try {
            Class<?> clazz = Class.forName("net.minecraft.client.renderer.RenderPipelines");
            try {
                return clazz.getField("GUI").get(null);
            } catch (NoSuchFieldException ignored) {
                Class<?> pipelineType = fillMethod != null && fillMethod.getParameterCount() > 0
                        ? fillMethod.getParameterTypes()[0]
                        : null;
                for (Field field : clazz.getFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    if ("GUI".equals(field.getName())
                            || (pipelineType != null && pipelineType.isAssignableFrom(field.getType())
                            && field.getName().toUpperCase().contains("GUI")
                            && !field.getName().toUpperCase().contains("TEXTURE"))) {
                        return field.get(null);
                    }
                }
                // Any static RenderPipeline-typed field as last resort
                if (pipelineType != null) {
                    for (Field field : clazz.getFields()) {
                        if (Modifier.isStatic(field.getModifiers()) && pipelineType.isAssignableFrom(field.getType())) {
                            return field.get(null);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // unavailable
        }
        return null;
    }

    private interface ParamPredicate {
        boolean test(Class<?>[] params);
    }

    private static Method findByNames(Method[] methods, String[] names, ParamPredicate predicate) {
        for (String name : names) {
            for (Method method : methods) {
                if (!name.equals(method.getName())) {
                    continue;
                }
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (predicate.test(method.getParameterTypes())) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Method findFirst(Method[] methods, ParamPredicate predicate) {
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getReturnType() != void.class && method.getReturnType() != Void.TYPE) {
                // Prefer void draw helpers; skip getters
                continue;
            }
            if (predicate.test(method.getParameterTypes())) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static boolean allInts(Class<?>[] params) {
        for (Class<?> param : params) {
            if (param != int.class) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFontLike(Class<?> type) {
        return Font.class.isAssignableFrom(type) || type.getName().contains("Font") || type.getName().contains("TextRenderer");
    }

    private IllegalStateException missing(String label, String expected) {
        StringBuilder sb = new StringBuilder("Missing HUD method: ")
                .append(label)
                .append(" (expected ")
                .append(expected)
                .append(") on ")
                .append(graphics.getClass().getName())
                .append(". Public methods: ");
        for (Method method : graphics.getClass().getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            sb.append(method.getName()).append('(');
            Class<?>[] pts = method.getParameterTypes();
            for (int i = 0; i < pts.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(pts[i].getSimpleName());
            }
            sb.append("); ");
        }
        return new IllegalStateException(sb.toString());
    }
}
