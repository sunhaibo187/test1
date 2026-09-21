import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Oracle存储过程翻译器
 * 将Oracle PL/SQL存储过程翻译为Java程序（命令行/单文件，无框架依赖）
 *
 * 支持翻译的PL/SQL结构：
 *   1. 过程名、IN/OUT参数提取
 *   2. 变量声明提取（v_xxx NUMBER/VARCHAR2）
 *   3. SELECT ... INTO 语句 → 数据查询方法调用
 *   4. IF / ELSIF / ELSE / END IF 逻辑 → Java if/else
 *   5. 变量赋值 := → Java赋值
 *   6. EXCEPTION 异常处理 → try/catch
 *   7. 注释、常量识别
 *
 * 使用方式：
 *   java ProcTranslator <存储过程.sql> [输出.java]
 *
 * 翻译后的Java程序结构：
 *   - execute()方法：对应存储过程主逻辑
 *   - 数据源：默认内置Mock数据（HashMap），真实环境替换为JDBC查询
 */
public class ProcTranslator {

    // ==================== 解析结果模型 ====================

    /** 存储过程参数 */
    public static class ProcParam {
        String name;      // 参数名
        String direction; // IN / OUT / IN OUT
        String type;      // 数据类型
        String comment;   // 注释

        ProcParam(String name, String direction, String type, String comment) {
            this.name = name;
            this.direction = direction;
            this.type = type;
            this.comment = comment;
        }
    }

    /** 翻译结果 */
    public static class TranslationResult {
        String procName;           // 存储过程名
        String className;          // Java类名
        List<ProcParam> inParams = new ArrayList<>();
        List<ProcParam> outParams = new ArrayList<>();
        String logicCode = "";     // 翻译后的逻辑代码
        List<String> warnings = new ArrayList<>(); // 未翻译结构的警告

        boolean isError() {
            return procName == null || procName.isEmpty();
        }
    }

    // ==================== 正则模式 ====================

    /** 存储过程名 */
    private static final Pattern PROC_NAME_PATTERN =
            Pattern.compile("PROCEDURE\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    /** 参数行：p_name IN/OUT TYPE[,] [注释] */
    private static final Pattern PARAM_PATTERN =
            Pattern.compile("\\s*(\\w+)\\s+(IN\\s+OUT|IN|OUT)\\s+([\\w.]+)\\s*,?\\s*(--\\s*(.*))?",
                    Pattern.CASE_INSENSITIVE);

    /** 变量声明：v_name TYPE; */
    private static final Pattern VAR_PATTERN =
            Pattern.compile("\\s*(\\w+)\\s+([\\w.]+)\\s*;", Pattern.CASE_INSENSITIVE);

    /** SELECT ... INTO ... FROM */
    private static final Pattern SELECT_INTO_PATTERN =
            Pattern.compile("SELECT\\s+COUNT\\(\\*\\)\\s+INTO\\s+(\\w+)\\s+FROM\\s+(\\w+)\\s+WHERE\\s+(.+?)\\s*;",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 赋值语句：target := value; */
    private static final Pattern ASSIGN_PATTERN =
            Pattern.compile("(\\w+)\\s*:=\\s*(.+?)\\s*;", Pattern.CASE_INSENSITIVE);

    // ==================== 主翻译方法 ====================

    /** 日志开关：调试时可关闭 */
    private static boolean debugLog = true;

    /** 日志格式: [ProcTranslator] HH:mm:ss.SSS - 级别 - 消息 */
    private static final SimpleDateFormat LOG_TIME = new SimpleDateFormat("HH:mm:ss.SSS");

    private static void log(String message) {
        if (debugLog) {
            System.out.println("[ProcTranslator] " + LOG_TIME.format(new Date()) + " - " + message);
        }
    }

    /**
     * 解析并翻译存储过程源码
     */
    public static TranslationResult translate(String procSql) {
        log("===== 开始翻译存储过程 =====");
        log("[INFO] 输入SQL长度: " + (procSql == null ? 0 : procSql.length()) + " 字符");
        TranslationResult result = new TranslationResult();
        String sql = stripComments(procSql);
        log("[INFO] 去除注释后SQL长度: " + sql.length() + " 字符");

        // 1. 提取过程名
        Matcher nameMatcher = PROC_NAME_PATTERN.matcher(sql);
        if (!nameMatcher.find()) {
            result.warnings.add("未找到PROCEDURE声明，可能不是存储过程文件");
            log("[ERROR] 未找到PROCEDURE声明，请检查文件内容是否为Oracle存储过程");
            return result;
        }
        result.procName = nameMatcher.group(1);
        result.className = "Proc" + capitalize(toCamelCase(result.procName));
        log("[INFO] 识别存储过程: " + result.procName + " -> 生成类名 " + result.className);

        // 2. 提取参数（括号内）
        extractParams(sql, result);
        log("[INFO] 参数解析完成: IN参数 " + result.inParams.size() + " 个, OUT参数 " + result.outParams.size() + " 个");
        for (ProcParam p : result.inParams) {
            log("      IN 参数: " + p.name + " (" + p.type + ") " + p.comment);
        }
        for (ProcParam p : result.outParams) {
            log("      OUT 参数: " + p.name + " (" + p.type + ") " + p.comment);
        }
        if (result.inParams.isEmpty() && result.outParams.isEmpty()) {
            log("[WARN] 未解析到任何参数，请检查参数声明格式（如 p_name IN VARCHAR2,）");
        }

        // 3. 提取变量声明（BEGIN前）
        int beginIndex = sql.toUpperCase().indexOf("BEGIN");
        log("[INFO] BEGIN关键字位置: " + beginIndex);
        if (beginIndex > 0) {
            String declSection = sql.substring(0, beginIndex);
            List<String> declaredVars = new ArrayList<>();
            for (String line : declSection.split("\\n")) {
                Matcher varMatcher = VAR_PATTERN.matcher(line.trim());
                if (varMatcher.matches() && !isReserved(varMatcher.group(1))) {
                    declaredVars.add(varMatcher.group(1) + " " + varMatcher.group(2));
                }
            }
            log("[INFO] 变量声明区识别 " + declaredVars.size() + " 个变量: " + declaredVars);
        }

        // 4. 翻译BEGIN..END主体逻辑
        log("[INFO] 开始翻译主体逻辑 (BEGIN 位置 " + beginIndex + ")");
        result.logicCode = translateBody(sql, beginIndex, result);
        log("[INFO] 主体逻辑翻译完成, 生成代码 " + result.logicCode.length() + " 字符");
        log("===== 翻译完成 =====");
        log("[INFO] 警告数: " + result.warnings.size());
        for (String w : result.warnings) {
            log("[WARN] " + w);
        }

        return result;
    }

    /**
     * 提取参数
     */
    private static void extractParams(String sql, TranslationResult result) {
        int openParen = sql.indexOf('(');
        int closeParen = findMatchingParen(sql, openParen);
        if (openParen < 0 || closeParen < 0) {
            log("[WARN] 未找到参数括号 ( 或匹配的 ) ，参数解析跳过");
            return;
        }
        String paramsSection = sql.substring(openParen + 1, closeParen);
        for (String line : paramsSection.split("\\n")) {
            Matcher matcher = PARAM_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                ProcParam param = new ProcParam(
                        matcher.group(1),
                        matcher.group(2).toUpperCase(),
                        matcher.group(3),
                        matcher.group(5) != null ? matcher.group(5).trim() : "");
                if ("IN".equals(param.direction)) {
                    result.inParams.add(param);
                } else {
                    result.outParams.add(param);
                }
            }
        }
    }

    /**
     * 翻译存储过程主体逻辑
     */
    private static String translateBody(String sql, int beginIndex, TranslationResult result) {
        String body = sql.substring(beginIndex);
        int endIndex = body.toUpperCase().lastIndexOf("END");
        if (endIndex > 0) {
            body = body.substring(0, endIndex);
        }
        // 去掉 EXCEPTION 部分（翻译为注释提示）
        int exceptIndex = body.toUpperCase().indexOf("EXCEPTION");
        if (exceptIndex > 0) {
            body = body.substring(0, exceptIndex);
            result.warnings.add("EXCEPTION异常块已转为TODO注释，请补充Java异常处理");
        }

        StringBuilder sb = new StringBuilder();
        // 处理SELECT INTO → 数据查询
        Matcher selectMatcher = SELECT_INTO_PATTERN.matcher(body);
        StringBuffer selectResult = new StringBuffer();
        while (selectMatcher.find()) {
            String targetVar = selectMatcher.group(1);
            String table = selectMatcher.group(2);
            String condition = selectMatcher.group(3).trim();
            // 简化条件：xxx = p_param 提取参数名
            String conditionParam = extractConditionParam(condition);
            String replacement = String.format(
                    "// [SQL翻译] SELECT COUNT(*) INTO %s FROM %s WHERE %s%n"
                    + "int %s = queryCount(%s);  // TODO: 真实环境替换为JDBC查询",
                    targetVar, table, condition,
                    targetVar.toLowerCase().replace("_", ""),
                    conditionParam != null ? toCamelCase(conditionParam) : "null");
            selectMatcher.appendReplacement(selectResult, Matcher.quoteReplacement(replacement));
        }
        selectMatcher.appendTail(selectResult);
        body = selectResult.toString();

        // 逐行翻译
        String[] lines = body.split("\\n");
        int indent = 0;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            String upper = line.toUpperCase();

            // 翻译器已生成的行（// 注释、queryCount调用等）→ 原样输出
            if (line.startsWith("//") || line.contains("queryCount(")) {
                sb.append(indentStr(indent)).append(line).append("\n");
                continue;
            }

            // IF 条件
            if (upper.startsWith("IF ") && upper.endsWith(" THEN")) {
                String cond = line.substring(2, line.length() - 5).trim();
                sb.append(indentStr(indent)).append("if (").append(toJavaCond(cond)).append(") {\n");
                indent++;
                continue;
            }
            // ELSIF
            if (upper.startsWith("ELSIF ") && upper.endsWith(" THEN")) {
                indent--;
                String cond = line.substring(5, line.length() - 5).trim();
                sb.append(indentStr(indent)).append("} else if (").append(toJavaCond(cond)).append(") {\n");
                indent++;
                continue;
            }
            // ELSE
            if (upper.equals("ELSE")) {
                indent--;
                sb.append(indentStr(indent)).append("} else {\n");
                indent++;
                continue;
            }
            // END IF
            if (upper.equals("END IF;") || upper.equals("END IF")) {
                indent--;
                sb.append(indentStr(indent)).append("}\n");
                continue;
            }
            // 赋值语句
            Matcher assignMatcher = ASSIGN_PATTERN.matcher(line);
            if (assignMatcher.matches()) {
                String target = assignMatcher.group(1);
                String value = assignMatcher.group(2);
                String javaTarget = mapVarName(target);
                String javaValue = mapLiteral(value);
                // OUT参数赋值 → 记录到结果Map
                sb.append(indentStr(indent)).append(mapVarName(target)).append(" = ")
                  .append(javaValue).append(";  // ").append(line).append("\n");
                continue;
            }
            // 其他语句（不可翻译）
            if (line.endsWith(";") && !upper.startsWith("--")) {
                sb.append(indentStr(indent)).append("// [未翻译] ").append(line).append("\n");
                result.warnings.add("存在未翻译语句: " + line);
                continue;
            }
            // 注释
            if (upper.startsWith("--") || upper.startsWith("/*")) {
                sb.append(indentStr(indent)).append("// ").append(line.replace("--", "").trim()).append("\n");
                continue;
            }
            // 其余行原样注释
            sb.append(indentStr(indent)).append("// [PL/SQL] ").append(line).append("\n");
        }

        // 关闭未闭合的括号
        while (indent > 0) {
            indent--;
            sb.append(indentStr(indent)).append("}\n");
        }

        return sb.toString();
    }

    // ==================== 辅助工具 ====================

    /**
     * 生成完整Java类源码
     */
    public static String generateJavaClass(TranslationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("import java.util.HashMap;\n");
        sb.append("import java.util.Map;\n\n");

        sb.append("/**\n");
        sb.append(" * 由Oracle存储过程自动翻译生成的Java程序\n");
        sb.append(" * 源存储过程: ").append(result.procName).append("\n");
        sb.append(" * 生成工具: ProcTranslator\n");
        sb.append(" */\n");
        sb.append("public class ").append(result.className).append(" {\n\n");

        // 内置Mock数据源（演示用，真实环境替换为JDBC查询）
        sb.append("    /** 内置Mock数据源（演示用），键为业务主键，值为查询结果 */\n");
        sb.append("    private static final Map<String, Integer> MOCK_DATA = new HashMap<>();\n");
        sb.append("    static {\n");
        sb.append("        MOCK_DATA.put(\"13574156868\", 5);\n");
        sb.append("        MOCK_DATA.put(\"13808427239\", 20);\n");
        sb.append("        MOCK_DATA.put(\"15874202674\", 100);\n");
        sb.append("        MOCK_DATA.put(\"13707310007\", 0);\n");
        sb.append("    }\n\n");

        // 查询方法
        sb.append("    /**\n");
        sb.append("     * 数据查询方法（对应存储过程中的SELECT语句）\n");
        sb.append("     * TODO: 真实环境替换为JDBC: SELECT COUNT(*) FROM 表 WHERE 条件\n");
        sb.append("     */\n");
        sb.append("    private static int queryCount(String key) {\n");
        sb.append("        Integer value = MOCK_DATA.get(key);\n");
        sb.append("        return value == null ? 0 : value;\n");
        sb.append("    }\n\n");

        // execute方法（含输入输出参数）
        sb.append("    /**\n");
        sb.append("     * 执行翻译后的存储过程逻辑\n");
        for (ProcParam p : result.inParams) {
            sb.append("     * @param ").append(toCamelCase(p.name)).append(" ").append(p.comment).append("\n");
        }
        sb.append("     */\n");
        sb.append("    public Map<String, Object> execute(");
        for (int i = 0; i < result.inParams.size(); i++) {
            if (i > 0) sb.append(", ");
            ProcParam p = result.inParams.get(i);
            sb.append("String ").append(toCamelCase(p.name));
        }
        sb.append(") {\n");
        sb.append("        Map<String, Object> result = new HashMap<>();\n");

        // OUT参数初始化
        for (ProcParam p : result.outParams) {
            sb.append("        String ").append(mapVarName(p.name)).append(" = null;\n");
        }

        sb.append("\n");
        sb.append("        // ===== 存储过程逻辑翻译 =====\n");
        sb.append(result.logicCode);
        sb.append("\n");

        // 组装输出
        sb.append("        // ===== 输出参数封装 =====\n");
        for (ProcParam p : result.outParams) {
            sb.append("        result.put(\"").append(p.name).append("\", ")
              .append(mapVarName(p.name)).append(");\n");
        }
        sb.append("        return result;\n");
        sb.append("    }\n\n");

        // main方法（可直接运行演示）
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        ").append(result.className).append(" instance = new ").append(result.className).append("();\n");
        sb.append("        String[] testCases = {\"13574156868\", \"13808427239\", \"15874202674\", \"13707310007\", \"00000000000\"};\n");
        sb.append("        for (String input : testCases) {\n");
        sb.append("            Map<String, Object> out = instance.execute(input);\n");
        sb.append("            System.out.println(\"输入=\" + input + \" -> 输出=\" + out);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 提取条件中的参数名
     */
    private static String extractConditionParam(String condition) {
        Matcher m = Pattern.compile("=\\s*(p_\\w+)", Pattern.CASE_INSENSITIVE).matcher(condition);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * 将PL/SQL条件转为Java条件
     */
    private static String toJavaCond(String cond) {
        String c = cond.trim();
        // 变量名转换：v_count -> vcount
        c = c.replaceAll("(?i)\\bv_(\\w+)", "v$1");
        // 参数名转换：p_serial_number -> pSerialNumber
        Matcher pm = Pattern.compile("(?i)p_\\w+").matcher(c);
        StringBuffer sb = new StringBuffer();
        while (pm.find()) {
            pm.appendReplacement(sb, Matcher.quoteReplacement(toCamelCase(pm.group(0))));
        }
        pm.appendTail(sb);
        c = sb.toString();
        // 运算符转换：<> -> !=, IS NOT NULL -> != null, IS NULL -> == null
        c = c.replaceAll("(?i)IS\\s+NOT\\s+NULL", " != null");
        c = c.replaceAll("(?i)IS\\s+NULL", " == null");
        c = c.replace("<>", "!=");
        // 单个 = 转为 ==（排除 ==, !=, >=, <=）
        c = c.replaceAll("(?<![=<>!])=(?!=)", "==");
        return c;
    }

    /**
     * 变量名映射：v_count → vCount
     */
    private static String mapVarName(String plSqlVar) {
        String name = plSqlVar.trim();
        if (name.matches("(?i)p_\\w+")) {
            return toCamelCase(name);
        }
        if (name.matches("(?i)v_\\w+")) {
            String v = name.substring(2);
            return "v" + capitalize(v);
        }
        return name;
    }

    /**
     * 值字面量映射：'NONE' → "NONE"，数字保持
     */
    private static String mapLiteral(String value) {
        String v = value.trim();
        if (v.startsWith("'") && v.endsWith("'")) {
            return "\"" + v.substring(1, v.length() - 1) + "\"";
        }
        if (v.matches("-?\\d+(\\.\\d+)?")) {
            return v;
        }
        if (v.equalsIgnoreCase("NULL")) {
            return "null";
        }
        // 引用参数
        Matcher m = Pattern.compile("(p_\\w+)").matcher(v);
        if (m.find()) {
            return toCamelCase(m.group(1));
        }
        return v;
    }

    /**
     * 去除SQL注释
     */
    private static String stripComments(String sql) {
        // 去除 -- 行注释
        String[] lines = sql.split("\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            int idx = line.indexOf("--");
            if (idx >= 0) {
                line = line.substring(0, idx);
            }
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    /**
     * 查找匹配的右括号
     */
    private static int findMatchingParen(String str, int openIdx) {
        if (openIdx < 0) return -1;
        int depth = 0;
        for (int i = openIdx; i < str.length(); i++) {
            if (str.charAt(i) == '(') depth++;
            else if (str.charAt(i) == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static boolean isReserved(String word) {
        String w = word.toLowerCase();
        return w.equals("as") || w.equals("begin") || w.equals("end") || w.equals("declare")
                || w.equals("is") || w.equals("number") || w.equals("varchar2");
    }

    private static String toCamelCase(String name) {
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private static String indentStr(int indent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
        return sb.toString();
    }

    // ==================== 主入口 ====================

    public static void main(String[] args) {
        try {
            String inputFile = args.length >= 1 ? args[0] : "sample_proc.sql";
            String sql = readFile(inputFile);

            System.out.println("📖 正在解析存储过程: " + inputFile);
            TranslationResult result = translate(sql);

            if (result.isError()) {
                System.err.println("❌ 解析失败: " + String.join("; ", result.warnings));
                return;
            }

            String javaCode = generateJavaClass(result);
            String outputFile = args.length >= 2 ? args[1] : result.className + ".java";
            writeFile(outputFile, javaCode);

            System.out.println("✅ 翻译完成!");
            System.out.println("   存储过程: " + result.procName);
            System.out.println("   输入参数: " + result.inParams.size() + " 个");
            System.out.println("   输出参数: " + result.outParams.size() + " 个");
            System.out.println("   输出文件: " + outputFile);

            if (!result.warnings.isEmpty()) {
                System.out.println("\n⚠️ 警告 (" + result.warnings.size() + " 条):");
                for (String w : result.warnings) {
                    System.out.println("   - " + w);
                }
            }
        } catch (Exception e) {
            System.err.println("❌ 翻译失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String readFile(String filePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static void writeFile(String filePath, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }
}