package client;

import contracts.CodeExecutingProcessor;
import contracts.Contract;
import contracts.ContractProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CodeExecutingProcessorTest {

    @Test
    public void test() {
        // Catch the output to compare it
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(out));

        ContractProcessor processor = new CodeExecutingProcessor();
        String sourceCode =
                "public class TestClass {\n" +
                "\n" +
                "public void callMe() {\n" +
                "System.out.println(\"Method callMe has been called\");\n" +
                "}\n" +
                "\n" +
                "}";

        List<String> args = Arrays.asList(sourceCode.split("\n"));
        Contract contract = new Contract("test", Contract.REQUESTED_PROCESSOR_ANY, "javacodeexecution", args);
        processor.processContract(contract);

        Assert.assertEquals("Method callMe has been called", out.toString().trim());
    }
}
