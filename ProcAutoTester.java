import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * 存储过程翻译Java程序自动测试器
 *
 * 功能：
 *   1. 调用ProcTranslator将Oracle存储过程翻译为Java程序
 *   2. 自动编译翻译生成的Java源码（JDK自带编译器）
 *   3. 读取测试用例文件，反射调用翻译后的方法
 *   4. 断言实际输出与期望值，生成测试报告
 *
 * 测试用例CSV格式：
 *   用例名,输入参数,期望输出
 *   例如: CASE001,13574156868,GOLD
 *   多参数用分号分隔: CASE002,15874202674;0,DIAMOND
 *
 * 使用方式：
 *   java ProcAutoTester [存储过程.sql] [测试用例.csv]
 */
public class ProcAutoTester {

    /** 测试用例模型 */
    static class TestCase {
        String name;
        List<String> inputs;
        String expected;

        TestCase(String name, List<String> inputs, String expected) {
            this.name = name;
            this.inputs = inputs;
            this.expected = expected;
        }
    }

    /** 测试统计 */
    static int passed = 0;
    static int failed = 0;
    static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        String procFile = args.length >= 1 ? args[0] : "sample_proc.sql";
        String caseFile = args.length >= 2 ? args[1] : "test_cases.csv";

        System.out.println("==========================================");
        System.out.println(" Oracle存储过程翻译Java自动测试");
        System.out.println("==========================================");

        try {
            // 第一步：翻译存储过程为Java
            System.out.println("\n【第1步】翻译存储过程: " + procFile);
            String procSql = readFile(procFile);
            ProcTranslator.TranslationResult result = ProcTranslator.translate(procSql);
            if (result.isError()) {
                System.err.println("❌ 翻译失败: " + String.join("; ", result.warnings));
                return;
            }
            String javaSource = ProcTranslator.generateJavaClass(result);
            String javaFile = result.className + ".java";
            writeFile(javaFile, javaSource);
            System.out.println("✅ 翻译成功，生成: " + javaFile);

            // 第二步：编译Java源码
            System.out.println("\n【第2步】编译Java源码");
            if (!compile(javaFile)) {
                System.err.println("❌ 编译失败");
                return;
            }
            System.out.println("✅ 编译成功: " + result.className + ".class");

            // 第三步：读取测试用例
            System.out.println("\n【第3步】读取测试用例: " + caseFile);
            List<TestCase> testCases = readTestCases(caseFile);
            if (testCases.isEmpty()) {
                System.out.println("⚠️ 未找到测试用例文件，使用内置用例");
                testCases = buildDefaultCases(result);
            }
            System.out.println("✅ 共 " + testCases.size() + " 条用例");

            // 第四步：执行测试
            System.out.println("\n【第4步】执行测试");
            runTests(result.className, testCases);

            // 第五步：输出报告
            printReport(testCases.size());

        } catch (Exception e) {
            System.err.println("❌ 测试执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 编译Java源码（使用JDK自带编译器）
     */
    private static boolean compile(String javaFile) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("❌ 未找到JDK编译器，请使用JDK运行而非JRE");
            return false;
        }
        int result = compiler.run(null, null, null, javaFile);
        return result == 0;
    }

    /**
     * 反射调用翻译后的Java类并断言
     */
    @SuppressWarnings("unchecked")
    private static void runTests(String className, List<TestCase> testCases) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[]{new File(".").toURI().toURL()});
        Class<?> clazz = Class.forName(className, true, loader);
        Object instance = clazz.getDeclaredConstructor().newInstance();

        Method executeMethod = findExecuteMethod(clazz);
        executeMethod.setAccessible(true);

        for (TestCase tc : testCases) {
            Object[] args = new Object[tc.inputs.size()];
            Class<?>[] paramTypes = executeMethod.getParameterTypes();
            for (int i = 0; i < tc.inputs.size(); i++) {
                if (paramTypes[i] == int.class || paramTypes[i] == Integer.class) {
                    args[i] = Integer.parseInt(tc.inputs.get(i).trim());
                } else {
                    args[i] = tc.inputs.get(i).trim();
                }
            }

            Object output = executeMethod.invoke(instance, args);
            // 输出是Map，取第一个值作为结果
            String actual = extractResult(output);

            boolean ok = tc.expected.trim().equals(actual);
            if (ok) {
                passed++;
                System.out.println("  ✅ [" + tc.name + "] 输入=" + String.join(",", tc.inputs)
                        + " 期望=" + tc.expected + " 实际=" + actual);
            } else {
                failed++;
                failures.add(tc.name);
                System.out.println("  ❌ [" + tc.name + "] 输入=" + String.join(",", tc.inputs)
                        + " 期望=" + tc.expected + " 实际=" + actual);
            }
        }
        loader.close();
    }

    /**
     * 查找execute方法（参数个数匹配用例输入数）
     */
    private static Method findExecuteMethod(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals("execute") && !m.isBridge()) {
                return m;
            }
        }
        throw new IllegalStateException("未找到execute方法");
    }

    /**
     * 从输出Map中提取首个输出值
     */
    private static String extractResult(Object output) {
        if (output instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) output;
            if (!map.isEmpty()) {
                Object value = map.values().iterator().next();
                return value == null ? "null" : value.toString();
            }
        }
        return output == null ? "null" : output.toString();
    }

    /**
     * 读取测试用例CSV
     */
    private static List<TestCase> readTestCases(String caseFile) throws IOException {
        List<TestCase> cases = new ArrayList<>();
        File file = new File(caseFile);
        if (!file.exists()) {
            return cases;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (firstLine) {  // 跳过表头
                    firstLine = false;
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                List<String> inputs = new ArrayList<>();
                for (String in : parts[1].split(";")) {
                    inputs.add(in.trim());
                }
                cases.add(new TestCase(parts[0].trim(), inputs, parts[2].trim()));
            }
        }
        return cases;
    }

    /**
     * 内置默认用例（演示）
     */
    private static List<TestCase> buildDefaultCases(ProcTranslator.TranslationResult result) {
        List<TestCase> cases = new ArrayList<>();
        cases.add(new TestCase("CASE001", List.of("13574156868"), "GOLD"));      // 5次消费
        cases.add(new TestCase("CASE002", List.of("13808427239"), "PLATINUM"));  // 20次消费
        cases.add(new TestCase("CASE003", List.of("15874202674"), "DIAMOND"));   // 100次消费
        cases.add(new TestCase("CASE004", List.of("13707310007"), "NONE"));      // 0次消费
        cases.add(new TestCase("CASE005", List.of("00000000000"), "NONE"));      // 无记录
        return cases;
    }

    /**
     * 打印测试报告
     */
    private static void printReport(int total) {
        System.out.println("\n==========================================");
        System.out.println(" 测试报告");
        System.out.println("==========================================");
        System.out.println("  总用例数: " + total);
        System.out.println("  通过: " + passed);
        System.out.println("  失败: " + failed);
        System.out.println("  通过率: " + (total == 0 ? 0 : (passed * 100 / total)) + "%");
        if (!failures.isEmpty()) {
            System.out.println("\n  失败用例: " + String.join(", ", failures));
        }
        System.out.println("==========================================");
    }

    // ==================== 工具方法 ====================

    private static String readFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
    }

    private static void writeFile(String filePath, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }
}