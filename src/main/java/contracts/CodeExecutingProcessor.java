package contracts;

import javax.tools.JavaCompiler;
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
import java.util.List;

public class CodeExecutingProcessor implements ContractProcessor {

    private final static String SUPPORTED_OPERATION = "javacodeexecution";

    /**
     * Proof of Concept to run arbitrary source code as a "contract".
     * This does not include packages or method parameters, to achieve those, it would be best to introduce a new contract type
     * to ease parsing the different components needed to execute the code.
     *
     * @param contract Contains the source code
     * @return nothing
     */
    @Override
    public Object processContract(Contract contract) {
        // Get the source code from the args, line for line
        String sourceCode = contract.getArgs().stream().reduce("", (accu, s) -> accu += "\n" + s);

        // Save a .java file
        File javaFile = new File("target/TestClass.java");
        //System.out.println("Javafile path: " + javaFile.getAbsolutePath());
        if (!javaFile.exists()) {
            try {
                if (javaFile.createNewFile()) {
                    //System.out.println("New File created successfully");
                } else {
                    System.out.println("File could not be created!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
        //System.out.println("Java file has been successfully compiled!");

        // Load the compiled file with a ClassLoader
        File compiledFile = new File("target/TestClass.class");
        if (!compiledFile.exists()) {
            System.out.println("Compiled file does not exist!");
            return null;
        }

        URLClassLoader loader = null;
        try {
            URL[] urls = new URL[]{new File("target/").toURI().toURL()};
            //System.out.println("Compiled class path: " + compiledFile.getAbsolutePath());
            loader = new URLClassLoader(urls);
            //loader.findResource("TestClass.class");

            // Double check that the loader is pointing to the right path
           /* for (URL url : loader.getURLs()) {
                System.out.println("URLs path: " + url.getPath());
            } */

        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
        // Try to execute the method on a instance of the class
        try {
            Class clazz = loader.loadClass("TestClass");
            //System.out.println("Class has been loaded successfully");
            Method method = clazz.getMethod("callMe", null);
            Object object = clazz.newInstance();
            method.invoke(object, null);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public List<String> getSupportedOperations() {
        return Collections.singletonList(SUPPORTED_OPERATION);
    }
}
