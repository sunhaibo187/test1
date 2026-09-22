import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Web前台页面健康检查工具
 *
 * 功能：
 * 1. 从配置文件(CSV)读取待检查的页面列表（URL、期望状态码、期望关键字、超时时间）
 * 2. 多线程并发发起 HTTP GET 请求
 * 3. 逐项检查：HTTP状态码是否匹配、关键内容是否存在、响应耗时
 * 4. 输出检查报告（控制台 + HTML 报告文件 + 文本报告文件）
 *
 * CSV 配置格式（UTF-8，逗号分隔，字段内勿含逗号）：
 *   URL,期望状态码,期望关键字(多个用分号分隔),超时毫秒
 * 例：
 *   http://192.168.22.22:8080/login,200,登录;用户登录,5000
 *
 * 用法：
 *   java WebPageHealthChecker pages.csv
 *   java WebPageHealthChecker           # 使用内置演示配置
 */
public class WebPageHealthChecker {

    /** 并发检查线程数 */
    private static final int CONCURRENCY = 5;
    /** 默认超时时间(毫秒) */
    private static final int DEFAULT_TIMEOUT = 5000;
    /** 读取响应体用于关键字匹配的最大字节数 */
    private static final int MAX_BODY_FOR_CHECK = 500 * 1024;
    /** 报告输出目录 */
    private static final String REPORT_DIR = "page_check_report";
    /** 日志开关 */
    private static boolean debugLog = true;

    // ==================== 配置模型 ====================

    /** 单个页面的检查配置 */
    static class PageConfig {
        String url;
        int expectStatus;
        List<String> keywords;
        int timeoutMs;

        PageConfig(String url, int expectStatus, List<String> keywords, int timeoutMs) {
            this.url = url;
            this.expectStatus = expectStatus;
            this.keywords = keywords;
            this.timeoutMs = timeoutMs;
        }
    }

    /** 单个页面的检查结果 */
    static class CheckResult {
        String url;
        int statusCode = -1;
        long costMs;
        boolean connected;
        String bodySample = "";
        List<String> missingKeywords = new ArrayList<>();
        boolean statusOk;
        boolean keywordOk;
        String error;
        boolean pass;

        /** 中文结论标签 */
        String conclusion() {
            if (!connected) return "连接异常";
            if (statusOk && keywordOk) return "通过";
            if (!statusOk) return "状态码错误";
            return "缺少关键字";
        }
    }

    // ==================== 入口 ====================

    public static void main(String[] args) {
        System.out.println("========== Web前台页面健康检查工具 ==========");

        // 1. 读取配置（外部CSV 或 内置演示）
        List<PageConfig> configs;
        String configFile = args.length > 0 ? args[0] : "pages.csv";
        File f = new File(configFile);
        if (args.length > 0 && f.exists()) {
            configs = readConfigsFromCsv(configFile);
            log("[INFO] 从配置文件读取 " + configs.size() + " 个页面: " + configFile);
        } else if (f.exists()) {
            configs = readConfigsFromCsv(configFile);
            log("[INFO] 从默认配置读取 " + configs.size() + " 个页面: " + configFile);
        } else {
            configs = buildDefaultConfigs();
            log("[WARN] 未找到 " + configFile + "，使用内置演示配置 " + configs.size() + " 个页面");
        }

        // 2. 并发检查
        long startTotal = System.currentTimeMillis();
        List<CheckResult> results = runCheck(configs);
        long totalMs = System.currentTimeMillis() - startTotal;

        // 3. 输出报告
        printConsoleReport(results, totalMs);
        writeReportFiles(results, totalMs);

        System.out.println();
        System.out.println("[完成] 报告已输出到: " + new File(REPORT_DIR).getAbsolutePath()
                + " (page_check_report.txt / page_check_report.html)");
        System.out.println("============================================================================");
    }

    // ==================== 配置读取 ====================

    /**
     * 从 CSV 读取页面配置
     * 格式: URL,期望状态码,期望关键字(分号分隔),超时毫秒
     */
    static List<PageConfig> readConfigsFromCsv(String file) {
        List<PageConfig> configs = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue; // 跳过空行和注释
                String[] cols = t.split(",");
                if (cols.length < 2) {
                    log("[WARN] 第" + lineNo + "行格式错误，已跳过: " + t);
                    continue;
                }
                String url = cols[0].trim();
                int expectStatus = 200;
                try { expectStatus = Integer.parseInt(cols[1].trim()); } catch (Exception ignored) {}
                List<String> keywords = new ArrayList<>();
                if (cols.length >= 3 && !cols[2].trim().isEmpty()) {
                    for (String k : cols[2].split(";")) {
                        if (!k.trim().isEmpty()) keywords.add(k.trim());
                    }
                }
                int timeout = DEFAULT_TIMEOUT;
                if (cols.length >= 4) {
                    try { timeout = Integer.parseInt(cols[3].trim()); } catch (Exception ignored) {}
                }
                configs.add(new PageConfig(url, expectStatus, keywords, timeout));
            }
        } catch (Exception e) {
            log("[ERROR] 读取配置文件失败: " + e.getMessage());
        }
        return configs;
    }

    /** 内置演示配置（无配置文件时使用） */
    static List<PageConfig> buildDefaultConfigs() {
        List<PageConfig> configs = new ArrayList<>();
        // 按实际前台页面替换
        configs.add(new PageConfig("http://192.192.2.101:8080/login", 200,
                List.of("登录", "用户名"), 5000));
        configs.add(new PageConfig("http://192.192.2.101:8080/home", 200,
                List.of("首页", "欢迎"), 5000));
        configs.add(new PageConfig("http://192.192.2.101:8080/api/health", 200,
                List.of("ok"), 3000));
        return configs;
    }

    // ==================== 并发检查 ====================

    static List<CheckResult> runCheck(List<PageConfig> configs) {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        List<Future<CheckResult>> futures = new ArrayList<>();
        for (PageConfig cfg : configs) {
            futures.add(pool.submit(() -> checkPage(cfg)));
        }
        pool.shutdown();

        List<CheckResult> results = new ArrayList<>();
        for (Future<CheckResult> fu : futures) {
            try {
                results.add(fu.get());
            } catch (Exception e) {
                log("[ERROR] 检查任务执行异常: " + e.getMessage());
            }
        }
        return results;
    }

    /** 检查单个页面 */
    static CheckResult checkPage(PageConfig cfg) {
        CheckResult r = new CheckResult();
        r.url = cfg.url;
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(cfg.url);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(cfg.timeoutMs);
            conn.setReadTimeout(cfg.timeoutMs);
            conn.setInstanceFollowRedirects(true);
            // 模拟浏览器 UA，部分前台页面拒绝非浏览器访问
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) WebPageHealthChecker/1.0");
            conn.setRequestProperty("Accept", "text/html,application/json,*/*");

            r.statusCode = conn.getResponseCode();
            r.costMs = System.currentTimeMillis() - start;
            r.connected = true;

            // 读取响应体（截取前 MAX_BODY_FOR_CHECK 字节用于关键字匹配）
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int n, total = 0;
                while ((n = br.read(buf)) > 0 && total < MAX_BODY_FOR_CHECK) {
                    sb.append(buf, 0, n);
                    total += n;
                }
            } catch (Exception ignored) {
                // 4xx/5xx 时 getInputStream 抛异常，改读错误流
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    char[] buf = new char[8192];
                    int n;
                    while ((n = br.read(buf)) > 0 && sb.length() < MAX_BODY_FOR_CHECK) {
                        sb.append(buf, 0, n);
                    }
                } catch (Exception ignored2) {}
            }
            r.bodySample = sb.toString();

            // 状态码检查
            r.statusOk = (r.statusCode == cfg.expectStatus);

            // 关键字检查
            r.keywordOk = true;
            if (!cfg.keywords.isEmpty()) {
                for (String kw : cfg.keywords) {
                    if (!r.bodySample.contains(kw)) {
                        r.missingKeywords.add(kw);
                        r.keywordOk = false;
                    }
                }
            }

            r.pass = r.statusOk && r.keywordOk;
            log("[INFO] " + (r.pass ? "通过" : "失败") + " | " + r.url
                    + " | 状态码 " + r.statusCode + " (期望 " + cfg.expectStatus + ")"
                    + " | " + r.costMs + "ms"
                    + (r.missingKeywords.isEmpty() ? "" : " | 缺少关键字: " + r.missingKeywords));
        } catch (Exception e) {
            r.costMs = System.currentTimeMillis() - start;
            r.connected = false;
            r.error = e.getMessage();
            r.pass = false;
            log("[ERROR] 请求异常 | " + cfg.url + " | " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return r;
    }

    // ==================== 报告输出 ====================

    /** 控制台报告 */
    static void printConsoleReport(List<CheckResult> results, long totalMs) {
        int passCnt = 0, failCnt = 0, errCnt = 0;
        for (CheckResult r : results) {
            if (!r.connected) errCnt++;
            else if (r.pass) passCnt++;
            else failCnt++;
        }
        System.out.println();
        System.out.println("========================== 检查结果汇总 ==========================");
        System.out.println("总页面数: " + results.size()
                + " | 通过: " + passCnt
                + " | 失败: " + failCnt
                + " | 连接异常: " + errCnt
                + " | 总耗时: " + totalMs + "ms");
        System.out.println("--------------------------------------------------------------------");
        System.out.printf("%-6s %-10s %-12s %-12s %s%n", "序号", "结论", "状态码", "耗时(ms)", "URL");
        int i = 1;
        for (CheckResult r : results) {
            String tag = !r.connected ? "连接异常" : (r.pass ? "✔ 通过" : "✘ 失败");
            System.out.printf("%-6s %-10s %-12s %-12s %s%n",
                    i++, tag, r.connected ? r.statusCode : "-",
                    r.costMs, r.url);
            if (!r.connected && r.error != null) {
                System.out.println("          └ 错误信息: " + r.error);
            } else if (!r.missingKeywords.isEmpty()) {
                System.out.println("          └ 缺少关键字: " + r.missingKeywords);
            }
        }
        System.out.println("--------------------------------------------------------------------");
    }

    /** 写 HTML 报告 + 文本报告 */
    static void writeReportFiles(List<CheckResult> results, long totalMs) {
        try {
            File dir = new File(REPORT_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                log("[WARN] 无法创建报告目录: " + dir.getAbsolutePath());
                return;
            }
            writeTextReport(new File(dir, "page_check_report.txt"), results, totalMs);
            writeHtmlReport(new File(dir, "page_check_report.html"), results, totalMs);
        } catch (Exception e) {
            log("[ERROR] 写报告失败: " + e.getMessage());
        }
    }

    static void writeTextReport(File file, List<CheckResult> results, long totalMs) throws Exception {
        try (PrintWriter pw = new PrintWriter(file, "UTF-8")) {
            pw.println("Web前台页面健康检查报告");
            pw.println("生成时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            int passCnt = 0, failCnt = 0, errCnt = 0;
            for (CheckResult r : results) {
                if (!r.connected) errCnt++;
                else if (r.pass) passCnt++;
                else failCnt++;
            }
            pw.println("汇总: 总 " + results.size() + " | 通过 " + passCnt
                    + " | 失败 " + failCnt + " | 异常 " + errCnt + " | 耗时 " + totalMs + "ms");
            pw.println("========================================================");
            for (CheckResult r : results) {
                pw.println("[" + r.conclusion() + "] " + r.url);
                pw.println("    状态码: " + (r.connected ? r.statusCode : "无")
                        + " | 耗时: " + r.costMs + "ms");
                if (!r.connected && r.error != null) {
                    pw.println("    错误: " + r.error);
                }
                if (!r.missingKeywords.isEmpty()) {
                    pw.println("    缺少关键字: " + r.missingKeywords);
                }
            }
        }
    }

    static void writeHtmlReport(File file, List<CheckResult> results, long totalMs) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"UTF-8\">")
          .append("<title>Web前台页面健康检查报告</title><style>")
          .append("body{font-family:'Microsoft YaHei',sans-serif;margin:20px;background:#f5f5f5;}")
          .append("h1{color:#333;border-bottom:2px solid #4CAF50;padding-bottom:10px;}")
          .append("table{border-collapse:collapse;width:100%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.1);}")
          .append("th,td{padding:10px 14px;text-align:left;border-bottom:1px solid #eee;}")
          .append("th{background:#4CAF50;color:#fff;}")
          .append(".pass{color:#4CAF50;font-weight:bold;}.fail{color:#f44336;font-weight:bold;}")
          .append(".err{color:#ff9800;font-weight:bold;}")
          .append(".summary{background:#fff;padding:15px;border-radius:6px;margin-bottom:15px;box-shadow:0 1px 3px rgba(0,0,0,.1);}")
          .append("</style></head><body>");
        sb.append("<h1>Web前台页面健康检查报告</h1>");
        int passCnt = 0, failCnt = 0, errCnt = 0;
        for (CheckResult r : results) {
            if (!r.connected) errCnt++;
            else if (r.pass) passCnt++;
            else failCnt++;
        }
        sb.append("<div class=\"summary\">生成时间: ")
          .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()))
          .append("<br>汇总: 总 <b>").append(results.size())
          .append("</b> | 通过 <b style=\"color:#4CAF50\">").append(passCnt)
          .append("</b> | 失败 <b style=\"color:#f44336\">").append(failCnt)
          .append("</b> | 连接异常 <b style=\"color:#ff9800\">").append(errCnt)
          .append("</b> | 总耗时 ").append(totalMs).append("ms</div>");
        sb.append("<table><tr><th>序号</th><th>结论</th><th>URL</th><th>状态码</th><th>耗时(ms)</th><th>详情</th></tr>");
        int i = 1;
        for (CheckResult r : results) {
            String cls = !r.connected ? "err" : (r.pass ? "pass" : "fail");
            String conclusion = r.conclusion();
            sb.append("<tr><td>").append(i++).append("</td>")
              .append("<td class=\"").append(cls).append("\">").append(conclusion).append("</td>")
              .append("<td>").append(r.url).append("</td>")
              .append("<td>").append(r.connected ? r.statusCode : "-").append("</td>")
              .append("<td>").append(r.costMs).append("</td><td>");
            if (!r.connected && r.error != null) {
                sb.append("错误: ").append(escapeHtml(r.error));
            }
            if (!r.missingKeywords.isEmpty()) {
                sb.append("缺少关键字: ").append(escapeHtml(r.missingKeywords.toString()));
            }
            sb.append("</td></tr>");
        }
        sb.append("</table></body></html>");
        Files.write(Paths.get(file.toURI()), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** HTML 转义 */
    static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ==================== 日志 ====================

    static void log(String msg) {
        if (debugLog) {
            System.out.println(new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()) + " - " + msg);
        }
    }
}
