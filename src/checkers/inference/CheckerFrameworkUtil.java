package checkers.inference;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.Objects;

import javax.tools.JavaFileManager;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.Main;
import com.sun.tools.javac.main.Main.Result;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Context.Factory;

public class CheckerFrameworkUtil {

    public static boolean invokeCheckerFramework(String[] args, PrintWriter outputCapture) {
        Main compiler = new Main("javac", outputCapture);

        // copied from https://github.com/google/error-prone-javac/blob/a53d069bbdb2c60232ed3811c19b65e41c3e60e0/src/jdk.compiler/share/classes/com/sun/tools/javac/main/Main.java#L159
        Context context = new Context();
        ClassloaderMaskingFileManager.preRegister(context); // can't create it until Log has been set up
        Result compilerResult = compiler.compile(args, context);

        return compilerResult == Result.OK;
    }

    // copied from https://github.com/bazelbuild/bazel/blob/a1d758d615ffb0cc77518e37fed36b2950c32ca9/src/java_tools/buildjar/java/com/google/devtools/build/buildjar/javac/BlazeJavacMain.java#L236
    private static class ClassloaderMaskingFileManager extends JavacFileManager {
        public ClassloaderMaskingFileManager(Context context, boolean register, Charset charset) {
            super(context, register, charset);
        }

        // adopted from https://github.com/google/error-prone-javac/blob/a53d069bbdb2c60232ed3811c19b65e41c3e60e0/src/jdk.compiler/share/classes/com/sun/tools/javac/file/JavacFileManager.java#L137
        public static void preRegister(Context context) {
            context.put(JavaFileManager.class,
                    (Factory<JavaFileManager>)c -> new ClassloaderMaskingFileManager(c, true, null));
        }

        @Override
        protected ClassLoader getClassLoader(URL[] urls) {
            return new URLClassLoader(
                urls,
                new ClassLoader(null) {
                    @Override
                    protected Class<?> findClass(String name) throws ClassNotFoundException {
                    Class<?> c = Class.forName(name);
                    if (name.startsWith("com.google.errorprone.")
                        || name.startsWith("org.checkerframework.")
                        || name.startsWith("checkers.inference")
                        || name.startsWith("com.sun.source.")
                        || name.startsWith("com.sun.tools.")) {
                        return c;
                    }
                    if (c.getClassLoader() == null
                        || Objects.equals(getClassLoaderName(c.getClassLoader()), "platform")) {
                        return c;
                    }
                    throw new ClassNotFoundException(name);
                    }
                });
        }

        private static String getClassLoaderName(ClassLoader classLoader) {
            Method method;
            try {
                method = ClassLoader.class.getMethod("getName");
            } catch (NoSuchMethodException e) {
                // ClassLoader#getName doesn't exist in JDK 8 and earlier.
                return null;
            }
            try {
                return (String) method.invoke(classLoader, new Object[] {});
            } catch (ReflectiveOperationException e) {
                throw new LinkageError(e.getMessage(), e);
            }
        }

    }
}
