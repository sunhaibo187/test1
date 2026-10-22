// 测试
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bug 录入规范检查工具
 *
 * 功能：
 *   1. 对单条 Bug 记录的 标题 / 内容 / 模块 / 严重级别 / 复现步骤 等字段做规范性校验
 *   2. 输出问题清单（错误 ERROR / 警告 WARN / 建议 INFO）与规范化得分
 *   3. 支持直接传入参数校验，或从本地文件批量校验（CSV: 标题|内容|模块|级别|复现步骤|期望结果）
 *
 * 校验维度：
 *   - 标题：长度、是否含模块前缀、【】标记、是否含无意义词、是否含缺陷描述
 *   - 内容：最小长度、是否包含关键要素（复现步骤/实际结果/期望结果/环境）
 *   - 格式：标点、空白、敏感/占位词（xxx、TODO、待补充）
 *   - 级别：是否为合法值（致命/严重/一般/提示 或 P0~P3）
 *
 * 使用方式：
 *   java BugReportValidator                        使用内置示例演示
 *   java BugReportValidator -f bugs.txt            从文件批量校验
 */
public class BugReportValidator {

    /** 校验结果级别 */
    private enum Level { ERROR, WARN, INFO }

    /** 单条问题 */
    private static class Issue {
        final Level level;
        final String field;
        final String message;

        Issue(Level level, String field, String message) {
            this.level = level;
            this.field = field;
            this.message = message;
        }
    }

    /** Bug 记录 */
    private static class BugReport {
        String title = "";
        String content = "";
        String module = "";
        String severity = "";
        String steps = "";
        String expected = "";

        static BugReport fromLine(String line) {
            BugReport r = new BugReport();
            String[] p = line.split("\\|", -1);
            r.title = p.length > 0 ? p[0].trim() : "";
            r.content = p.length > 1 ? p[1].trim() : "";
            r.module = p.length > 2 ? p[2].trim() : "";
            r.severity = p.length > 3 ? p[3].trim() : "";
            r.steps = p.length > 4 ? p[4].trim() : "";
            r.expected = p.length > 5 ? p[5].trim() : "";
            return r;
        }
    }

    /** 合法的严重级别取值 */
    private static final List<String> VALID_SEVERITY =
            Arrays.asList("致命", "严重", "一般", "提示", "p0", "p1", "p2", "p3");

    /** 无意义/占位词 */
    private static final List<String> PLACEHOLDER_WORDS =
            Arrays.asList("xxx", "todo", "待补充", "待定", "暂无", "无内容", "test", "测试一下");

    /** 标题中常见"只描述现象不描述问题"的弱词 */
    private static final List<String> WEAK_TITLE_WORDS =
            Arrays.asList("好像", "可能", "不清楚", "有问题", "不对", "报错", "异常");

    /** 内容应包含的关键要素（任一命中即视为部分覆盖） */
    private static final List<String> CONTENT_KEYWORDS =
            Arrays.asList("复现", "步骤", "实际操作", "实际结果", "期望", "预期", "环境", "版本", "接口", "日志");

    /** 严重级别推荐前缀，用于标题规范提示 */
    private static final Pattern MODULE_PREFIX_PATTERN = Pattern.compile("^[【\\[].+?[】\\]].*");

    /**
     * 校验单条 Bug 记录
     */
    public static List<Issue> validate(BugReport r) {
        List<Issue> issues = new ArrayList<>();

        // ---------- 标题校验 ----------
        if (isBlank(r.title)) {
            issues.add(new Issue(Level.ERROR, "标题", "标题不能为空"));
        } else {
            int len = r.title.length();
            if (len < 8) {
                issues.add(new Issue(Level.ERROR, "标题",
                        "标题过短（" + len + " 字），请描述清楚问题，建议 ≥ 8 字"));
            } else if (len > 50) {
                issues.add(new Issue(Level.WARN, "标题",
                        "标题过长（" + len + " 字），建议控制在 50 字以内，细节放到内容里"));
            }
            if (r.title.endsWith("。") || r.title.endsWith(".")) {
                issues.add(new Issue(Level.INFO, "标题", "标题结尾不建议加句号"));
            }
            if (!MODULE_PREFIX_PATTERN.matcher(r.title).matches()) {
                issues.add(new Issue(Level.INFO, "标题", "建议使用【模块】前缀标注，如【用户管理】XX 按钮点击无响应"));
            }
            String lower = r.title.toLowerCase();
            for (String w : PLACEHOLDER_WORDS) {
                if (lower.contains(w)) {
                    issues.add(new Issue(Level.ERROR, "标题", "标题含占位/无意义词：" + w));
                    break;
                }
            }
            for (String w : WEAK_TITLE_WORDS) {
                if (r.title.contains(w)) {
                    issues.add(new Issue(Level.WARN, "标题",
                            "标题含模糊表述“" + w + "”，建议改为可定位的具体描述"));
                    break;
                }
            }
        }

        // ---------- 内容校验 ----------
        if (isBlank(r.content)) {
            issues.add(new Issue(Level.ERROR, "内容", "内容不能为空，需包含问题描述"));
        } else {
            if (r.content.length() < 20) {
                issues.add(new Issue(Level.WARN, "内容",
                        "内容过短（" + r.content.length() + " 字），建议补充问题现象、影响范围"));
            }
            boolean hasKeyword = false;
            for (String k : CONTENT_KEYWORDS) {
                if (r.content.contains(k)) {
                    hasKeyword = true;
                    break;
                }
            }
            if (!hasKeyword) {
                issues.add(new Issue(Level.WARN, "内容",
                        "内容未包含关键要素（复现步骤/实际结果/期望结果/环境等），建议补齐"));
            }
            checkPlaceholder(r.content, "内容", issues);
        }

        // ---------- 复现步骤校验 ----------
        if (isBlank(r.steps)) {
            issues.add(new Issue(Level.WARN, "复现步骤", "缺少复现步骤，建议按 1. 2. 3. 分步描述"));
        } else {
            if (!r.steps.contains("1") || !r.steps.matches("(?s).*[\\d①-⑩].*")) {
                issues.add(new Issue(Level.INFO, "复现步骤", "复现步骤建议编号（1. 2. 3.）便于逐步执行"));
            }
            if (r.steps.length() < 10) {
                issues.add(new Issue(Level.WARN, "复现步骤", "复现步骤过于简略，建议细化到可操作"));
            }
            checkPlaceholder(r.steps, "复现步骤", issues);
        }

        // ---------- 期望结果校验 ----------
        if (isBlank(r.expected)) {
            issues.add(new Issue(Level.WARN, "期望结果", "缺少期望结果，无法判断什么是正确行为"));
        } else {
            checkPlaceholder(r.expected, "期望结果", issues);
        }

        // ---------- 模块校验 ----------
        if (isBlank(r.module)) {
            issues.add(new Issue(Level.WARN, "所属模块", "未填写所属模块，不利于分派与统计"));
        }

        // ---------- 严重级别校验 ----------
        if (isBlank(r.severity)) {
            issues.add(new Issue(Level.ERROR, "严重级别", "未填写严重级别"));
        } else if (!VALID_SEVERITY.contains(r.severity.toLowerCase())) {
            issues.add(new Issue(Level.ERROR, "严重级别",
                    "非法严重级别“" + r.severity + "”，合法值：致命/严重/一般/提示 或 P0~P3"));
        }

        return issues;
    }

    /** 占位词检查 */
    private static void checkPlaceholder(String text, String field, List<Issue> issues) {
        String lower = text.toLowerCase();
        for (String w : PLACEHOLDER_WORDS) {
            if (lower.contains(w)) {
                issues.add(new Issue(Level.ERROR, field, "含占位/无意义词：" + w + "，请补充真实内容"));
                break;
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 根据问题清单计算规范化得分（满分 100，ERROR 扣 15，WARN 扣 5，INFO 扣 1）
     */
    public static int score(List<Issue> issues) {
        int score = 100;
        for (Issue i : issues) {
            switch (i.level) {
                case ERROR: score -= 15; break;
                case WARN:  score -= 5;  break;
                case INFO:  score -= 1;  break;
            }
        }
        return Math.max(0, score);
    }

    /** 输出单条 Bug 的校验结果 */
    public static void report(String index, BugReport r) {
        List<Issue> issues = validate(r);
        int sc = score(issues);
        System.out.println("--------------------------------------------------");
        System.out.println("【" + index + "】标题：" + (isBlank(r.title) ? "(空)" : r.title));
        System.out.println("规范化得分：" + sc + " / 100   " + grade(sc));
        if (issues.isEmpty()) {
            System.out.println("  ✅ 未发现问题，录入规范。");
            return;
        }
        for (Issue i : issues) {
            System.out.println("  " + "[" + i.level + "] " + i.field + "：" + i.message);
        }
    }

    private static String grade(int score) {
        if (score >= 90) return "（优秀）";
        if (score >= 75) return "（良好）";
        if (score >= 60) return "（合格）";
        return "（不合格，需整改）";
    }

    /** 内置演示数据 */
    private static List<BugReport> demoData() {
        List<BugReport> list = new ArrayList<>();
        list.add(BugReport.fromLine(
                "【用户管理】点击保存按钮后页面无响应|" +
                "复现步骤：1.进入用户管理 2.编辑任意用户 3.点击保存。实际结果：按钮无反应。" +
                "期望结果：保存成功并提示。环境：Chrome 120，测试环境|" +
                "用户管理|严重|1.进入用户管理 2.编辑用户 3.点击保存|保存成功并toast提示"));

        list.add(BugReport.fromLine(
                "有问题|好像不对| |xxx| | "));   // 反例：几乎全不达标

        list.add(BugReport.fromLine(
                "登录接口返回500|复现：调用 /login 接口，传参正确。实际结果：返回500，日志见附件。期望：返回200|登录|P1|1.调用登录接口|返回200并携带token"));
        return list;
    }

    public static void main(String[] args) {
        System.out.println("========== Bug 录入规范检查 ==========");
        if (args.length >= 2 && "-f".equals(args[0])) {
            String path = args[1];
            File f = new File(path);
            if (!f.exists()) {
                System.err.println("文件不存在：" + path);
                return;
            }
            try {
                List<String> lines = new ArrayList<>(
                        Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8));
                int idx = 0;
                int total = 0;
                int passed = 0;
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    idx++;
                    total++;
                    BugReport r = BugReport.fromLine(line);
                    report("第" + idx + "条", r);
                    if (score(validate(r)) >= 60) {
                        passed++;
                    }
                }
                System.out.println("==================================================");
                System.out.println("合计：" + total + " 条，合格：" + passed + " 条，不合格：" + (total - passed) + " 条");
            } catch (IOException e) {
                System.err.println("读取失败：" + e.getMessage());
            }
        } else {
            List<BugReport> list = demoData();
            int idx = 1;
            for (BugReport r : list) {
                report("示例" + idx++, r);
            }
            System.out.println("==================================================");
            System.out.println("提示：使用 -f <文件> 可从文件批量校验");
            System.out.println("文件格式（每行一条，用 | 分隔）：标题|内容|模块|严重级别|复现步骤|期望结果");
        }
    }
}
