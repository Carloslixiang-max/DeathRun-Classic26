package pl.mrstudios.commons.inject;

import org.jetbrains.annotations.NotNull;
import pl.mrstudios.commons.inject.annotation.Inject;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

public class Injector {

    private final Map<Class<?>, Object> registry = new HashMap<>();

    public <T> Injector register(@NotNull Class<T> type, @NotNull T instance) {
        this.registry.put(type, instance);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T inject(@NotNull Class<T> type) {
        Object registered = this.resolveRegistered(type);
        if (registered != null) {
            return (T) registered;
        }

        Constructor<?> constructor = this.selectConstructor(type);
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] args = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Object dependency = this.resolveRegistered(parameterTypes[i]);
            if (dependency == null) {
                dependency = this.inject(parameterTypes[i]);
            }
            args[i] = dependency;
        }

        try {
            constructor.setAccessible(true);
            Object instance = constructor.newInstance(args);
            this.registry.put(type, instance);
            return (T) instance;
        }
        catch (Exception exception) {
            throw new RuntimeException("Unable to inject instance for type: " + type.getName(), exception);
        }
    }

    private Object resolveRegistered(@NotNull Class<?> type) {
        Object exact = this.registry.get(type);
        if (exact != null) {
            return exact;
        }

        for (Map.Entry<Class<?>, Object> entry : this.registry.entrySet()) {
            if (type.isAssignableFrom(entry.getKey()) || type.isInstance(entry.getValue())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private Constructor<?> selectConstructor(@NotNull Class<?> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();

        for (Constructor<?> constructor : constructors) {
            if (constructor.isAnnotationPresent(Inject.class)) {
                return constructor;
            }
        }

        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() == 0) {
                return constructor;
            }
        }

        if (constructors.length == 1) {
            return constructors[0];
        }

        throw new RuntimeException("No injectable constructor found for type: " + type.getName());
    }
}
