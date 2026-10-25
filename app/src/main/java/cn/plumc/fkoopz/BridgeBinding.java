package cn.plumc.fkoopz;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Resolve Flutter types from the plugin's actual signatures, not R8 package names. */
final class BridgeBinding {
    final Field channel, splash, command;
    final Method dispatch, reply, emit, load, show, destroy, detach;

    BridgeBinding(Class<?> plugin) throws ReflectiveOperationException {
        channel = plugin.getDeclaredField("channel");
        splash = plugin.getDeclaredField("splashAd");
        dispatch = named(plugin, "onMethodCall", 2);
        detach = named(plugin, "onDetachedFromEngine", 1);
        Class<?>[] args = dispatch.getParameterTypes();
        Field stringField = null;
        for (Field field : args[0].getDeclaredFields()) {
            if (field.getType() != String.class || Modifier.isStatic(field.getModifiers())) continue;
            if (stringField != null) throw new NoSuchFieldException("Ambiguous MethodCall string fields");
            stringField = field;
        }
        if (stringField == null) throw new NoSuchFieldException("MethodCall string field missing");
        command = stringField;
        reply = signature(args[1], Object.class);
        emit = signature(channel.getType(), String.class, Object.class);
        load = plugin.getDeclaredMethod("loadSplashAd", boolean.class, String.class);
        show = plugin.getDeclaredMethod("showSplashAd");
        destroy = plugin.getDeclaredMethod("destorySplash");
        for (Method m : new Method[]{load, show, destroy}) {
            if (m.getReturnType() != void.class || Modifier.isStatic(m.getModifiers()))
                throw new NoSuchMethodException("Unexpected splash method signature: " + m.getName());
        }
        channel.setAccessible(true); splash.setAccessible(true); command.setAccessible(true);
        reply.setAccessible(true); emit.setAccessible(true);
    }

    private static Method named(Class<?> owner, String name, int count) throws NoSuchMethodException {
        Method result = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterTypes().length != count
                    || method.getReturnType() != void.class || Modifier.isStatic(method.getModifiers())
                    || method.isSynthetic()) continue;
            if (result != null) throw new NoSuchMethodException("Ambiguous " + name);
            result = method;
        }
        if (result == null) throw new NoSuchMethodException(name);
        return result;
    }

    private static Method signature(Class<?> owner, Class<?>... params) throws NoSuchMethodException {
        Method result = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (!Arrays.equals(method.getParameterTypes(), params) || method.getReturnType() != void.class
                    || Modifier.isStatic(method.getModifiers()) || method.isSynthetic()) continue;
            if (result != null) throw new NoSuchMethodException("Ambiguous Flutter method in " + owner.getName());
            result = method;
        }
        if (result == null) throw new NoSuchMethodException("Flutter method missing in " + owner.getName());
        return result;
    }
}
