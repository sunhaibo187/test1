import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java代码业务逻辑分析器
 *
 * 功能：
 * 1. 扫描指定目录下所有Java文件
 * 2. 解析每个文件的类结构、方法签名、注释、关键逻辑
 * 3. 自动识别业务模式（Controller/Service/DAO/Entity/DTO/工具类等）
 * 4. 提取方法间的调用关系
 * 5. 生成Markdown文档（单文件一份 + 汇总一份）
 *
 * 用法：
 *   java JavaCodeAnalyzer src/main/java          # 分析整个目录
 *   java JavaCodeAnalyzer src/Main.java           # 分析单个文件
 *   java JavaCodeAnalyzer src/main/java output/   # 指定输出目录
 */
public class JavaCodeAnalyzer {

    /** 文档输出目录 */
    private static final String DEFAULT_OUTPUT_DIR = "code_analysis_report";
    /** 日志开关 */
    private static boolean debugLog = true;

    // ==================== 分析结果模型 ====================

    /** 单个Java文件的分析结果 */
    static class ClassAnalysis {
        String fileName;
        String className;
        String packageName;
        String classType;         // Controller/Service/DAO/Entity/DTO/Util/Config/Other
        String classComment;
        String superClass;
        List<String> interfaces = new ArrayList<>();
        List<FieldInfo> fields = new ArrayList<>();
        List<MethodInfo> methods = new ArrayList<>();
        List<String> imports = new ArrayList<>();
        List<String> annotations = new ArrayList<>();
        int lineCount;
        int methodCount;
        int complexity;           // 圈复杂度估算
    }

    /** 字段信息 */
    static class FieldInfo {
        String name;
        String type;
        String modifier;          // private/public/protected/static/final
        String comment;
        List<String> annotations = new ArrayList<>();
    }

    /** 方法信息 */
    static class MethodInfo {
        String name;
        String returnType;
        String modifier;
        List<String> paramTypes = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();
        String comment;
        List<String> annotations = new ArrayList<>();
        boolean isConstructor;
        int complexity;
        List<String> calledMethods = new ArrayList<>();    // 本方法内调用的方法
        List<String> calledClasses = new ArrayList<>();    // 本方法内使用的类
        List<String> keywords = new ArrayList<>();         // 关键业务关键字
        int startLine;
        int endLine;
    }

    // ==================== 业务类型识别规则 ====================

    /** 类类型识别规则（按优先级） */
    static final String[][] CLASS_TYPE_RULES = {
        {"@RestController", "@Controller", "Controller"},
        {"@Service", "Service"},
        {"@Repository", "Dao", "Mapper", "Repository"},
        {"@Entity", "@Table", "Entity", "Model"},
        {"@DTO", "DTO", "VO", "Request", "Response", "Param"},
        {"@Configuration", "@Bean", "Config"},
        {"@Component", "Util", "Helper", "Tool", "Handler", "Filter", "Interceptor"},
    };

    /** 业务关键字提取规则 */
    static final String[] BUSINESS_KEYWORDS = {
        "查询", "新增", "修改", "删除", "保存", "更新", "校验", "验证",
        "导入", "导出", "下载", "上传", "审批", "审核", "提交", "撤销",
        "登录", "注册", "授权", "鉴权", "加密", "解密",
        "发送", "接收", "同步", "异步", "通知", "回调",
        "统计", "汇总", "报表", "分析",
        "create", "update", "delete", "query", "select", "insert",
        "save", "find", "get", "list", "add", "remove", "check", "validate",
        "import", "export", "download", "upload", "approve", "reject",
        "login", "register", "auth", "encrypt", "decrypt",
        "send", "receive", "sync", "notify", "callback",
        "count", "sum", "report", "analyze"
    };

    // ==================== 入口 ====================

    public static void main(String[] args) {
        System.out.println("========== Java代码业务逻辑分析器 ==========");

        if (args.length < 1) {
            System.out.println("用法: java JavaCodeAnalyzer <Java文件或目录> [输出目录]");
            System.out.println("示例: java JavaCodeAnalyzer src/main/java");
            System.out.println("      java JavaCodeAnalyzer src/Main.java output/");
            return;
        }

        String inputPath = args[0];
        String outputDir = args.length > 1 ? args[1] : DEFAULT_OUTPUT_DIR;

        File input = new File(inputPath);
        if (!input.exists()) {
            log("[ERROR] 路径不存在: " + inputPath);
            return;
        }

        // 1. 收集Java文件
        List<File> javaFiles = new ArrayList<>();
        if (input.isFile() && input.getName().endsWith(".java")) {
            javaFiles.add(input);
        } else if (input.isDirectory()) {
            collectJavaFiles(input, javaFiles);
        }
        log("[INFO] 共发现 " + javaFiles.size() + " 个Java文件");

        if (javaFiles.isEmpty()) {
            log("[WARN] 未找到Java文件");
            return;
        }

        // 2. 逐个分析
        List<ClassAnalysis> analyses = new ArrayList<>();
        for (File f : javaFiles) {
            log("[INFO] 正在分析: " + f.getName());
            ClassAnalysis analysis = analyzeFile(f);
            analyses.add(analysis);
        }

        // 3. 生成文档
        generateReports(analyses, outputDir);

        // 4. 输出汇总
        printSummary(analyses);

        System.out.println();
        System.out.println("[完成] 文档已输出到: " + new File(outputDir).getAbsolutePath());
        System.out.println("============================================================================");
    }

    // ==================== 文件收集 ====================

    static void collectJavaFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectJavaFiles(f, result);
            } else if (f.getName().endsWith(".java")) {
                result.add(f);
            }
        }
    }

    // ==================== 核心分析 ====================

    static ClassAnalysis analyzeFile(File file) {
        ClassAnalysis result = new ClassAnalysis();
        result.fileName = file.getName();

        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            result.lineCount = lines.size();
            String fullText = String.join("\n", lines);

            // 1. 包名
            for (String line : lines) {
                Matcher m = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;").matcher(line);
                if (m.find()) { result.packageName = m.group(1); break; }
            }

            // 2. 类名
            Matcher classMatcher = Pattern.compile(
                "(?:public\\s+|abstract\\s+|final\\s+)*(?:class|interface|enum)\\s+(\\w+)").matcher(fullText);
            if (classMatcher.find()) {
                result.className = classMatcher.group(1);
            } else {
                result.className = file.getName().replace(".java", "");
            }

            // 3. 父类
            Matcher superMatcher = Pattern.compile("extends\\s+(\\w+)").matcher(fullText);
            if (superMatcher.find()) result.superClass = superMatcher.group(1);

            // 4. 实现的接口
            Matcher implMatcher = Pattern.compile("implements\\s+([\\w,\\s]+?)(?:\\{|extends|<|$)").matcher(fullText);
            if (implMatcher.find()) {
                for (String iface : implMatcher.group(1).split(",")) {
                    result.interfaces.add(iface.trim());
                }
            }

            // 5. 类级别注解
            for (String line : lines) {
                Matcher annMatcher = Pattern.compile("@(\\w+)").matcher(line.trim());
                while (annMatcher.find()) {
                    result.annotations.add(annMatcher.group(1));
                }
            }

            // 6. 类注释（类声明前的注释块）
            result.classComment = extractClassComment(lines, result.className);

            // 7. 识别类类型
            result.classType = identifyClassType(result);

            // 8. import
            for (String line : lines) {
                Matcher impMatcher = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+)\\s*;").matcher(line);
                if (impMatcher.find()) result.imports.add(impMatcher.group(1));
            }

            // 9. 字段
            extractFields(lines, result);

            // 10. 方法
            extractMethods(lines, result);
            result.methodCount = result.methods.size();

            // 11. 圈复杂度估算
            result.complexity = estimateComplexity(fullText);

        } catch (Exception e) {
            log("[ERROR] 分析文件失败: " + file.getName() + " - " + e.getMessage());
        }

        return result;
    }

    /** 提取类注释 */
    static String extractClassComment(List<String> lines, String className) {
        StringBuilder sb = new StringBuilder();
        boolean inComment = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("/**")) {
                inComment = true;
                sb = new StringBuilder();
                continue;
            }
            if (inComment) {
                if (t.startsWith("*/")) break;
                String content = t.replaceAll("^\\s*\\*\\s?", "");
                if (!content.isEmpty()) sb.append(content).append("\n");
            }
            // 遇到类声明就停止
            if (t.contains("class " + className) || t.contains("interface " + className)
                || t.contains("enum " + className)) {
                break;
            }
        }
        return sb.toString().trim();
    }

    /** 识别类类型（注解精确匹配优先，其次类名后缀匹配） */
    static String identifyClassType(ClassAnalysis c) {
        String name = c.className == null ? "" : c.className;
        // 第一轮：注解精确命中（组内 @ 开头元素）
        for (String[] rule : CLASS_TYPE_RULES) {
            for (String keyword : rule) {
                if (keyword.startsWith("@") && c.annotations.contains(keyword)) {
                    return rule[rule.length - 1];
                }
            }
        }
        // 第二轮：类名后缀匹配（endsWith 避免 "Request" 等中间字母误判）
        for (String[] rule : CLASS_TYPE_RULES) {
            for (String keyword : rule) {
                if (!keyword.startsWith("@") && name.endsWith(keyword)) {
                    return rule[rule.length - 1];
                }
            }
        }
        return "Other";
    }

    /** 提取字段 */
    static void extractFields(List<String> lines, ClassAnalysis c) {
        boolean inClass = false;
        int braceDepth = 0;
        List<String> pendingAnnotations = new ArrayList<>();
        String pendingComment = "";

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // 收集注解
            if (line.startsWith("@")) {
                pendingAnnotations.add(line);
                continue;
            }

            // 收集注释
            if (line.startsWith("//")) {
                pendingComment = line.substring(2).trim();
                continue;
            }
            if (line.startsWith("/**")) {
                StringBuilder cb = new StringBuilder();
                for (int j = i; j < lines.size(); j++) {
                    String cl = lines.get(j).trim();
                    if (cl.startsWith("*/")) { i = j; break; }
                    String content = cl.replaceAll("^\\s*\\*\\s?", "");
                    if (!content.isEmpty()) cb.append(content).append(" ");
                }
                pendingComment = cb.toString().trim();
                continue;
            }

            // 类声明
            if (line.contains("class ") || line.contains("interface ") || line.contains("enum ")) {
                inClass = true;
            }

            if (!inClass) continue;

            // 括号计数
            for (char ch : line.toCharArray()) {
                if (ch == '{') braceDepth++;
                if (ch == '}') braceDepth--;
            }
            if (braceDepth <= 0 && inClass && braceDepth == 0) break;

            // 字段匹配（在类内第一层，非方法内）
            if (braceDepth == 1) {
                Matcher fieldMatcher = Pattern.compile(
                    "((?:public|private|protected|static|final|transient|volatile)\\s+)+(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*[;=]")
                    .matcher(line);
                if (fieldMatcher.find() && !line.contains("(") && !line.contains("class ")) {
                    FieldInfo fi = new FieldInfo();
                    fi.modifier = fieldMatcher.group(1).trim();
                    fi.type = fieldMatcher.group(2);
                    fi.name = fieldMatcher.group(3);
                    fi.comment = pendingComment;
                    fi.annotations.addAll(pendingAnnotations);
                    c.fields.add(fi);
                }
            }

            // 遇到方法声明就清空pending
            if (line.contains("(") && line.contains(")")) {
                pendingAnnotations.clear();
                pendingComment = "";
            }
        }
    }

    /** 提取方法 */
    static void extractMethods(List<String> lines, ClassAnalysis c) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // 收集方法前的注释
            String comment = collectMethodComment(lines, i);

            // 收集方法前的注解
            List<String> annotations = collectMethodAnnotations(lines, i);

            // 方法签名匹配
            Matcher methodMatcher = Pattern.compile(
                "((?:public|private|protected|static|final|abstract|synchronized|native)\\s+)*" +
                "(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{?")
                .matcher(line);

            if (methodMatcher.find() && !line.startsWith("//") && !line.startsWith("/*")
                && !line.startsWith("@") && !line.startsWith("package") && !line.startsWith("import")) {
                MethodInfo mi = new MethodInfo();
                mi.modifier = methodMatcher.group(1) != null ? methodMatcher.group(1).trim() : "";
                mi.returnType = methodMatcher.group(2);
                mi.name = methodMatcher.group(3);
                mi.isConstructor = mi.name.equals(c.className);
                mi.comment = comment;
                mi.annotations.addAll(annotations);
                mi.startLine = i + 1;

                // 解析参数
                String params = methodMatcher.group(4).trim();
                if (!params.isEmpty()) {
                    for (String param : params.split(",")) {
                        param = param.trim();
                        String[] parts = param.split("\\s+");
                        if (parts.length >= 2) {
                            mi.paramTypes.add(parts[parts.length - 2]);
                            mi.paramNames.add(parts[parts.length - 1]);
                        } else if (parts.length == 1) {
                            // 形如变量名缺失（例如裸类型），补占位名避免后续越界
                            mi.paramTypes.add(parts[0]);
                            mi.paramNames.add("arg" + (mi.paramTypes.size() - 1));
                        }
                    }
                }

                // 找到方法结束行
                mi.endLine = findMethodEnd(lines, i);

                // 提取方法体用于分析
                String methodBody = extractMethodBody(lines, i, mi.endLine);
                mi.complexity = estimateMethodComplexity(methodBody);

                // 提取调用的方法
                extractCalledMethods(methodBody, mi);

                // 提取业务关键字
                extractBusinessKeywords(methodBody, mi);

                c.methods.add(mi);
            }
        }
    }

    static String collectMethodComment(List<String> lines, int methodLine) {
        StringBuilder sb = new StringBuilder();
        for (int i = methodLine - 1; i >= 0; i--) {
            String t = lines.get(i).trim();
            if (t.startsWith("*/")) {
                // 向前找 /**
                for (int j = i - 1; j >= 0; j--) {
                    String cl = lines.get(j).trim();
                    if (cl.startsWith("/**")) break;
                    String content = cl.replaceAll("^\\s*\\*\\s?", "");
                    if (!content.isEmpty()) sb.insert(0, content + "\n");
                }
                break;
            } else if (t.startsWith("//")) {
                sb.insert(0, t.substring(2).trim() + "\n");
            } else if (t.startsWith("@") || t.isEmpty()) {
                continue;
            } else {
                break;
            }
        }
        return sb.toString().trim();
    }

    static List<String> collectMethodAnnotations(List<String> lines, int methodLine) {
        List<String> result = new ArrayList<>();
        for (int i = methodLine - 1; i >= 0; i--) {
            String t = lines.get(i).trim();
            if (t.startsWith("@")) {
                result.add(0, t);
            } else if (t.isEmpty()) {
                continue;
            } else {
                break;
            }
        }
        return result;
    }

    static int findMethodEnd(List<String> lines, int startLine) {
        int depth = 0;
        boolean started = false;
        for (int i = startLine; i < lines.size(); i++) {
            for (char ch : lines.get(i).toCharArray()) {
                if (ch == '{') { depth++; started = true; }
                if (ch == '}') depth--;
            }
            if (started && depth == 0) return i + 1;
        }
        return lines.size();
    }

    static String extractMethodBody(List<String> lines, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end && i < lines.size(); i++) {
            sb.append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }

    /** 提取方法内调用的其他方法 */
    static void extractCalledMethods(String body, MethodInfo mi) {
        // 匹配 xxx.methodName( 或 methodName(
        Matcher m = Pattern.compile("(\\w+)\\.(\\w+)\\s*\\(").matcher(body);
        Set<String> called = new LinkedHashSet<>();
        while (m.find()) {
            String target = m.group(1) + "." + m.group(2);
            if (!target.startsWith("System.") && !target.startsWith("log.")
                && !target.startsWith("this.") && !target.equals("super." + m.group(2))) {
                called.add(target);
            }
        }
        // 也匹配直接调用的方法名
        Matcher m2 = Pattern.compile("(?<!\\w)(\\w+)\\s*\\(").matcher(body);
        while (m2.find()) {
            String name = m2.group(1);
            if (!isJavaKeyword(name) && !name.equals(mi.name)) {
                called.add(name);
            }
        }
        mi.calledMethods.addAll(called);
    }

    /** 提取业务关键字 */
    static void extractBusinessKeywords(String body, MethodInfo mi) {
        String lower = body.toLowerCase();
        Set<String> found = new LinkedHashSet<>();
        for (String kw : BUSINESS_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                found.add(kw);
            }
        }
        // 提取SQL关键字
        for (String sql : new String[]{"SELECT", "INSERT", "UPDATE", "DELETE", "FROM", "WHERE", "JOIN"}) {
            if (body.toUpperCase().contains(sql)) found.add("SQL:" + sql);
        }
        mi.keywords.addAll(found);
    }

    /** 估算圈复杂度 */
    static int estimateComplexity(String code) {
        int c = 1;
        for (String kw : new String[]{"if", "else if", "for", "while", "case", "catch", "&&", "||", "?"}) {
            c += countOccurrences(code, kw);
        }
        return c;
    }

    static int estimateMethodComplexity(String code) {
        return estimateComplexity(code);
    }

    static int countOccurrences(String text, String sub) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    static boolean isJavaKeyword(String word) {
        Set<String> keywords = new HashSet<>(Arrays.asList(
            "if", "else", "for", "while", "do", "switch", "case", "return", "break", "continue",
            "try", "catch", "finally", "throw", "throws", "new", "class", "interface", "enum",
            "public", "private", "protected", "static", "final", "abstract", "void", "int",
            "long", "double", "float", "boolean", "char", "byte", "short", "String", "Object",
            "null", "true", "false", "this", "super", "instanceof", "import", "package"
        ));
        return keywords.contains(word);
    }

    // ==================== 报告生成 ====================

    static void generateReports(List<ClassAnalysis> analyses, String outputDir) {
        try {
            File dir = new File(outputDir);
            if (!dir.exists() && !dir.mkdirs()) {
                log("[WARN] 无法创建输出目录: " + outputDir);
                return;
            }

            // 单文件文档
            for (ClassAnalysis c : analyses) {
                String safeName = c.className != null ? c.className : c.fileName.replace(".java", "");
                File outFile = new File(dir, safeName + ".md");
                writeSingleReport(outFile, c);
            }

            // 汇总文档
            writeSummaryReport(new File(dir, "SUMMARY.md"), analyses);

        } catch (Exception e) {
            log("[ERROR] 生成报告失败: " + e.getMessage());
        }
    }

    static void writeSingleReport(File file, ClassAnalysis c) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(c.className != null ? c.className : c.fileName).append("\n\n");
        sb.append("> 文件: `").append(c.fileName).append("`\n\n");

        // 基本信息
        sb.append("## 基本信息\n\n");
        sb.append("| 属性 | 值 |\n|------|-----|\n");
        sb.append("| 类名 | `").append(c.className).append("` |\n");
        sb.append("| 包名 | `").append(c.packageName).append("` |\n");
        sb.append("| 类型 | **").append(c.classType).append("** |\n");
        if (c.superClass != null) sb.append("| 父类 | `").append(c.superClass).append("` |\n");
        if (!c.interfaces.isEmpty()) sb.append("| 接口 | ").append(String.join(", ", c.interfaces)).append(" |\n");
        sb.append("| 行数 | ").append(c.lineCount).append(" |\n");
        sb.append("| 方法数 | ").append(c.methodCount).append(" |\n");
        sb.append("| 圈复杂度 | ").append(c.complexity).append(" |\n");
        sb.append("\n");

        // 类注释
        if (!c.classComment.isEmpty()) {
            sb.append("## 类说明\n\n").append(c.classComment).append("\n\n");
        }

        // 注解
        if (!c.annotations.isEmpty()) {
            sb.append("## 注解\n\n");
            for (String a : c.annotations) sb.append("- `").append(a).append("`\n");
            sb.append("\n");
        }

        // 字段
        if (!c.fields.isEmpty()) {
            sb.append("## 字段\n\n");
            sb.append("| 修饰符 | 类型 | 名称 | 说明 |\n|--------|------|------|------|\n");
            for (FieldInfo f : c.fields) {
                sb.append("| `").append(f.modifier).append("` | `").append(f.type).append("` | `")
                  .append(f.name).append("` | ").append(f.comment).append(" |\n");
            }
            sb.append("\n");
        }

        // 方法
        if (!c.methods.isEmpty()) {
            sb.append("## 方法列表\n\n");
            for (MethodInfo m : c.methods) {
                sb.append("### ").append(m.name).append("()\n\n");

                // 方法签名
                sb.append("```java\n");
                sb.append(m.modifier).append(" ").append(m.returnType).append(" ").append(m.name).append("(");
                List<String> params = new ArrayList<>();
                for (int i = 0; i < m.paramTypes.size(); i++) {
                    params.add(m.paramTypes.get(i) + " " + m.paramNames.get(i));
                }
                sb.append(String.join(", ", params));
                sb.append(")\n```\n\n");

                // 注释
                if (!m.comment.isEmpty()) {
                    sb.append("**说明**: ").append(m.comment).append("\n\n");
                }

                // 注解
                if (!m.annotations.isEmpty()) {
                    sb.append("**注解**: ");
                    for (String a : m.annotations) sb.append("`").append(a).append("` ");
                    sb.append("\n\n");
                }

                // 参数
                if (!m.paramNames.isEmpty()) {
                    sb.append("| 参数 | 类型 |\n|------|------|\n");
                    for (int i = 0; i < m.paramNames.size(); i++) {
                        sb.append("| `").append(m.paramNames.get(i)).append("` | `")
                          .append(m.paramTypes.get(i)).append("` |\n");
                    }
                    sb.append("\n");
                }

                // 业务关键字
                if (!m.keywords.isEmpty()) {
                    sb.append("**业务关键字**: ");
                    for (String kw : m.keywords) sb.append("`").append(kw).append("` ");
                    sb.append("\n\n");
                }

                // 调用关系
                if (!m.calledMethods.isEmpty()) {
                    sb.append("**调用关系**:\n");
                    for (String called : m.calledMethods) {
                        sb.append("- `").append(called).append("()`\n");
                    }
                    sb.append("\n");
                }

                // 复杂度
                sb.append("> 圈复杂度: ").append(m.complexity).append(" | 行范围: ").append(m.startLine).append("-").append(m.endLine).append("\n\n");
                sb.append("---\n\n");
            }
        }

        // Import
        if (!c.imports.isEmpty()) {
            sb.append("## 依赖导入\n\n");
            for (String imp : c.imports) {
                sb.append("- `").append(imp).append("`\n");
            }
            sb.append("\n");
        }

        Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        log("[INFO] 已生成文档: " + file.getName());
    }

    static void writeSummaryReport(File file, List<ClassAnalysis> analyses) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# 代码业务逻辑分析汇总\n\n");
        sb.append("> 生成时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n\n");

        // 统计
        Map<String, Integer> typeCount = new LinkedHashMap<>();
        int totalMethods = 0, totalLines = 0, totalComplexity = 0;
        for (ClassAnalysis c : analyses) {
            typeCount.merge(c.classType, 1, Integer::sum);
            totalMethods += c.methodCount;
            totalLines += c.lineCount;
            totalComplexity += c.complexity;
        }

        sb.append("## 项目概览\n\n");
        sb.append("| 指标 | 值 |\n|------|-----|\n");
        sb.append("| Java文件数 | ").append(analyses.size()).append(" |\n");
        sb.append("| 总行数 | ").append(totalLines).append(" |\n");
        sb.append("| 总方法数 | ").append(totalMethods).append(" |\n");
        sb.append("| 总圈复杂度 | ").append(totalComplexity).append(" |\n");
        sb.append("\n");

        // 类型分布
        sb.append("## 类类型分布\n\n");
        sb.append("| 类型 | 数量 |\n|------|------|\n");
        for (Map.Entry<String, Integer> e : typeCount.entrySet()) {
            sb.append("| **").append(e.getKey()).append("** | ").append(e.getValue()).append(" |\n");
        }
        sb.append("\n");

        // 类清单
        sb.append("## 类清单\n\n");
        sb.append("| 序号 | 类名 | 类型 | 方法数 | 复杂度 | 说明 |\n");
        sb.append("|------|------|------|--------|--------|------|\n");
        int i = 1;
        for (ClassAnalysis c : analyses) {
            String desc = c.classComment.length() > 30 ? c.classComment.substring(0, 30) + "..." : c.classComment;
            sb.append("| ").append(i++).append(" | `").append(c.className).append("` | ")
              .append(c.classType).append(" | ").append(c.methodCount)
              .append(" | ").append(c.complexity).append(" | ").append(desc).append(" |\n");
        }
        sb.append("\n");

        // 高复杂度方法排行
        sb.append("## 高复杂度方法 TOP 10\n\n");
        List<MethodInfo> allMethods = new ArrayList<>();
        for (ClassAnalysis c : analyses) {
            for (MethodInfo m : c.methods) allMethods.add(m);
        }
        allMethods.sort((a, b) -> b.complexity - a.complexity);
        sb.append("| 序号 | 方法 | 所属类 | 复杂度 |\n|------|------|--------|--------|\n");
        int limit = Math.min(10, allMethods.size());
        for (int j = 0; j < limit; j++) {
            MethodInfo m = allMethods.get(j);
            sb.append("| ").append(j + 1).append(" | `").append(m.name).append("()` | ")
              .append(analyses.stream().filter(a -> a.methods.contains(m)).findFirst().map(a -> a.className).orElse("?"))
              .append(" | **").append(m.complexity).append("** |\n");
        }
        sb.append("\n");

        // 调用关系图（简化文本版）
        sb.append("## 方法调用关系\n\n");
        for (ClassAnalysis c : analyses) {
            for (MethodInfo m : c.methods) {
                if (!m.calledMethods.isEmpty()) {
                    sb.append("- `").append(c.className).append(".").append(m.name).append("()` → ");
                    sb.append(String.join(", ", m.calledMethods.stream()
                        .map(x -> "`" + x + "()`").toArray(String[]::new)));
                    sb.append("\n");
                }
            }
        }

        Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        log("[INFO] 已生成汇总文档: SUMMARY.md");
    }

    // ==================== 控制台汇总 ====================

    static void printSummary(List<ClassAnalysis> analyses) {
        System.out.println();
        System.out.println("========================== 分析汇总 ==========================");
        System.out.println("文件数: " + analyses.size());

        Map<String, Integer> typeCount = new LinkedHashMap<>();
        int totalMethods = 0, totalLines = 0;
        for (ClassAnalysis c : analyses) {
            typeCount.merge(c.classType, 1, Integer::sum);
            totalMethods += c.methodCount;
            totalLines += c.lineCount;
        }

        System.out.println("总行数: " + totalLines + " | 总方法数: " + totalMethods);
        System.out.println("------------------------------------------------------------");
        System.out.println("类类型分布:");
        for (Map.Entry<String, Integer> e : typeCount.entrySet()) {
            System.out.println("  " + e.getKey() + ": " + e.getValue() + " 个");
        }
        System.out.println("------------------------------------------------------------");
        System.out.println("类清单:");
        int i = 1;
        for (ClassAnalysis c : analyses) {
            System.out.printf("  %2d. [%-10s] %-30s (%d方法, %d行)%n",
                i++, c.classType, c.className, c.methodCount, c.lineCount);
        }
        System.out.println("================================================================");
    }

    // ==================== 日志 ====================

    static void log(String msg) {
        if (debugLog) {
            System.out.println(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + " - " + msg);
        }
    }
}
