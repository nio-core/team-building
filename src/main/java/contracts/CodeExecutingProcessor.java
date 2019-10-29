package contracts;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class CodeExecutingProcessor implements ContractProcessor {

    private final static String SUPPORTED_OPERATION = "javacodeexecution";

    @Override
    public Object processContract(Contract contract) {
        // Get the source code from the args, line for line
        String sourceCode = contract.getArgs().stream().reduce("", (accu, s) -> accu += "\n" + s);

        // Save a .java file
        File javaFile = new File("TestClass.java");
        BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter(javaFile));
            writer.write(sourceCode);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        // Compile the .java file
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable javaFileObjects = fileManager.getJavaFileObjects(javaFile);
        String[] compilerOptions = new String[]{};
        compiler.getTask(null, null, null, Arrays.asList(compilerOptions), null, javaFileObjects)
                .call();
        try {
            fileManager.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Java file has been successfully compiled!");

        // Load the compiled file with a ClassLoader
        File compiledFile = new File("TestClass.class");
        if (!compiledFile.exists()) {
            System.out.println("Compiled file does not exist!");
            return null;
        }
        URLClassLoader loader = null;
        try {
            URL[] urls = new URL[]{compiledFile.toURI().toURL()};
            System.out.println("Compiled class URL: " + compiledFile.toURI().toURL().toString());
            ClassLoader parentCL = Thread.currentThread().getContextClassLoader();
            loader = new URLClassLoader(urls, parentCL);
            //loader.findResource("TestClass.class");
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
        // Try to execute the method on a instance of the class
        try {
            for (URL l : loader.getURLs()) {
                System.out.println("URL: " + l.toString());
            }
            Class clazz = loader.loadClass("test.TestClass");
            System.out.println("Class has been loaded successfully");
            Method method = clazz.getMethod("callMe", null);
            Object object = clazz.newInstance();
            method.invoke(object, null);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }


        return true;
    }

    @Override
    public List<String> getSupportedOperations() {
        return Collections.singletonList(SUPPORTED_OPERATION);
    }
}
