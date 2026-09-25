package pl.mrstudios.commons.reflection;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Reflections<T> {

    private final String packageName;

    public Reflections(@NotNull String packageName) {
        this.packageName = packageName;
    }

    public @NotNull Set<Class<? extends T>> getClassesImplementing(@NotNull Class<T> type) {
        Set<Class<? extends T>> result = new HashSet<>();

        for (Class<?> candidate : this.scan()) {
            if (!type.isAssignableFrom(candidate) || candidate.equals(type)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Class<? extends T> casted = (Class<? extends T>) candidate;
            result.add(casted);
        }

        return result;
    }

    public @NotNull Set<Class<?>> getClassesAnnotatedWith(@NotNull Class<? extends java.lang.annotation.Annotation> annotation) {
        Set<Class<?>> result = new HashSet<>();

        for (Class<?> candidate : this.scan()) {
            if (candidate.isAnnotationPresent(annotation)) {
                result.add(candidate);
            }
        }

        return result;
    }

    private @NotNull Set<Class<?>> scan() {
        String packagePath = this.packageName.replace('.', '/');
        Set<Class<?>> classes = new HashSet<>();
        ClassLoader classLoader = this.getClass().getClassLoader();

        try {
            Enumeration<URL> resources = classLoader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if ("file".equals(protocol)) {
                    String filePath = URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8);
                    this.scanFileTree(this.packageName, new File(filePath), classes);
                }

                if ("jar".equals(protocol)) {
                    this.scanJar(resource, packagePath, classes);
                }
            }
        }
        catch (IOException exception) {
            throw new RuntimeException("Unable to scan package: " + this.packageName, exception);
        }

        return classes;
    }

    private void scanFileTree(@NotNull String currentPackage, @NotNull File directory, @NotNull Set<Class<?>> classes) {
        File[] entries = directory.listFiles();
        if (entries == null) {
            return;
        }

        for (File entry : entries) {
            if (entry.isDirectory()) {
                this.scanFileTree(currentPackage + "." + entry.getName(), entry, classes);
                continue;
            }

            if (!entry.getName().endsWith(".class") || entry.getName().contains("$")) {
                continue;
            }

            String simpleName = entry.getName().substring(0, entry.getName().length() - 6);
            this.tryLoad(currentPackage + "." + simpleName, classes);
        }
    }

    private void scanJar(@NotNull URL resource, @NotNull String packagePath, @NotNull Set<Class<?>> classes) {
        try {
            JarURLConnection connection = (JarURLConnection) resource.openConnection();
            try (JarFile jarFile = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (!name.startsWith(packagePath) || !name.endsWith(".class") || name.contains("$")) {
                        continue;
                    }

                    String className = name.substring(0, name.length() - 6).replace('/', '.');
                    this.tryLoad(className, classes);
                }
            }
        }
        catch (Exception exception) {
            throw new RuntimeException("Unable to scan jar for package: " + this.packageName, exception);
        }
    }

    private void tryLoad(@NotNull String className, @NotNull Set<Class<?>> classes) {
        try {
            classes.add(Class.forName(className, false, this.getClass().getClassLoader()));
        }
        catch (ClassNotFoundException ignored) {
        }
    }
}
