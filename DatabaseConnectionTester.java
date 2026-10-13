// 测试
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * 数据库连接测试工具
 *
 * 功能：
 * 1. 支持 MySQL / Oracle / PostgreSQL / SQLServer 等多种数据库
 * 2. 从配置文件（properties 格式）读取数据库连接信息
 * 3. 测试连接是否正常，输出耗时、连接状态、数据库版本等诊断信息
 * 4. 支持执行探活 SQL 验证连接可用性
 * 5. 输出汇总报告（成功/失败/耗时）
 *
 * 使用方式：
 *   1) 直接在代码中修改 DB_CONFIGS 中的连接信息（快速验证）
 *      java DatabaseConnectionTester
 *   2) 从配置文件读取（推荐）
 *      java DatabaseConnectionTester db.properties
 *
 * db.properties 示例：
 *   # 多个数据库用 .1 .2 后缀区分
 *   db.1.name=BOSS核心库
 *   db.1.driver=com.mysql.cj.jdbc.Driver
 *   db.1.url=jdbc:mysql://192.168.22.22:3306/boss?useSSL=false&serverTimezone=Asia/Shanghai
 *   db.1.username=boss_user
 *   db.1.password=boss_pwd
 *   db.1.testSql=SELECT 1 FROM DUAL
 */
public class DatabaseConnectionTester {

    /** 单个数据库连接配置 */
    public static class DbConfig {
        String name;         // 配置名称（便于识别）
        String driver;       // JDBC 驱动类
        String url;          // 连接地址
        String username;     // 用户名
        String password;     // 密码
        String testSql;      // 探活 SQL（可选）

        public DbConfig(String name, String driver, String url, String username, String password, String testSql) {
            this.name = name;
            this.driver = driver;
            this.url = url;
            this.username = username;
            this.password = password;
            this.testSql = testSql;
        }
    }

    /** 单个数据库测试结果 */
    public static class TestResult {
        String name;
        String url;
        boolean success;
        long costMs;          // 连接耗时（毫秒）
        String dbProduct;     // 数据库产品名
        String dbVersion;     // 数据库版本
        String errorMessage;  // 失败原因

        @Override
        public String toString() {
            if (success) {
                return String.format("[成功] %-20s 耗时=%4dms  产品=%s  版本=%s",
                        name, costMs, dbProduct, dbVersion);
            } else {
                return String.format("[失败] %-20s 耗时=%4dms  原因=%s",
                        name, costMs, errorMessage);
            }
        }
    }

    // ==================== 快速验证用的内置配置（可直接修改后运行） ====================
    private static final List<DbConfig> DB_CONFIGS = new ArrayList<>();

    static {
        // MySQL 示例
        DB_CONFIGS.add(new DbConfig(
                "MySQL-BOSS库",
                "com.mysql.cj.jdbc.Driver",
                "jdbc:mysql://192.168.22.22:3306/boss?useSSL=false&serverTimezone=Asia/Shanghai",
                "root",
                "root",
                "SELECT 1"
        ));

        // Oracle 示例
        DB_CONFIGS.add(new DbConfig(
                "Oracle-BOSS库",
                "oracle.jdbc.OracleDriver",
                "jdbc:oracle:thin:@192.168.22.22:1521:orcl",
                "boss_user",
                "boss_pwd",
                "SELECT 1 FROM DUAL"
        ));

        // PostgreSQL 示例
        DB_CONFIGS.add(new DbConfig(
                "PostgreSQL-测试库",
                "org.postgresql.Driver",
                "jdbc:postgresql://192.168.22.22:5432/testdb",
                "postgres",
                "postgres",
                "SELECT 1"
        ));
    }

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println("              数据库连接测试工具");
        System.out.println("==========================================================");

        List<DbConfig> configs;
        if (args.length > 0) {
            // 从配置文件读取
            String configFile = args[0];
            System.out.println("读取配置文件: " + configFile);
            configs = loadConfigFromFile(configFile);
            if (configs.isEmpty()) {
                System.out.println("[警告] 配置文件未解析到任何数据库配置，改用内置配置");
                configs = DB_CONFIGS;
            }
        } else {
            System.out.println("使用内置配置（如需自定义请传入 db.properties 文件路径）");
            configs = DB_CONFIGS;
        }

        System.out.println("待测试数据库数量: " + configs.size());
        System.out.println("----------------------------------------------------------");

        List<TestResult> results = new ArrayList<>();
        for (DbConfig config : configs) {
            TestResult result = testConnection(config);
            results.add(result);
            System.out.println(result);
        }

        // 输出汇总报告
        printSummary(results);

        // 输出 HTML 报告
        try {
            String reportFile = "db_connection_report.html";
            writeHtmlReport(results, reportFile);
            System.out.println("\nHTML 报告已生成: " + reportFile);
        } catch (IOException e) {
            System.out.println("[警告] HTML 报告生成失败: " + e.getMessage());
        }
    }

    /**
     * 测试单个数据库连接
     */
    public static TestResult testConnection(DbConfig config) {
        TestResult result = new TestResult();
        result.name = config.name;
        result.url = config.url;

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        long start = System.currentTimeMillis();

        try {
            // 1. 加载驱动
            try {
                Class.forName(config.driver);
            } catch (ClassNotFoundException e) {
                result.costMs = System.currentTimeMillis() - start;
                result.success = false;
                result.errorMessage = "驱动类未找到: " + config.driver
                        + "（请确认对应 JDBC 驱动 jar 已加入 classpath）";
                return result;
            }

            // 2. 获取连接（带超时，避免长时间卡住）
            DriverManager.setLoginTimeout(10);
            conn = DriverManager.getConnection(config.url, config.username, config.password);

            // 3. 读取数据库元信息
            DatabaseMetaData metaData = conn.getMetaData();
            result.dbProduct = metaData.getDatabaseProductName();
            result.dbVersion = metaData.getDatabaseProductVersion();

            // 4. 校验连接是否有效
            if (!conn.isValid(5)) {
                throw new SQLException("连接无效（isValid 返回 false）");
            }

            // 5. 执行探活 SQL（如果配置了）
            if (config.testSql != null && !config.testSql.trim().isEmpty()) {
                stmt = conn.createStatement();
                stmt.setQueryTimeout(5);
                rs = stmt.executeQuery(config.testSql);
                if (!rs.next()) {
                    throw new SQLException("探活 SQL 未返回结果: " + config.testSql);
                }
            }

            result.costMs = System.currentTimeMillis() - start;
            result.success = true;

        } catch (SQLException e) {
            result.costMs = System.currentTimeMillis() - start;
            result.success = false;
            result.errorMessage = buildFriendlyError(e);
        } catch (Exception e) {
            result.costMs = System.currentTimeMillis() - start;
            result.success = false;
            result.errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            closeQuietly(rs, stmt, conn);
        }

        return result;
    }

    /**
     * 将 SQL 异常转为更友好的提示，便于快速定位问题
     */
    private static String buildFriendlyError(SQLException e) {
        String msg = e.getMessage();
        StringBuilder sb = new StringBuilder();
        sb.append(msg);

        String upper = msg == null ? "" : msg.toUpperCase();
        String hint = null;
        if (upper.contains("ACCESS DENIED") || upper.contains("AUTHENTICATION")) {
            hint = "用户名或密码错误";
        } else if (upper.contains("UNKNOWN DATABASE") || upper.contains("DATABASE") && upper.contains("NOT EXIST")) {
            hint = "数据库名不存在";
        } else if (upper.contains("CONNECTION REFUSED") || upper.contains("CONNECT")) {
            hint = "网络不通或端口未开放（检查 IP/端口/防火墙）";
        } else if (upper.contains("TIMEOUT") || upper.contains("TIMED OUT")) {
            hint = "连接超时（检查网络或数据库负载）";
        } else if (upper.contains("COMMUNICATIONS LINK FAILURE")) {
            hint = "通信链路失败（网络中断或数据库不可达）";
        } else if (upper.contains("ORA-01017")) {
            hint = "Oracle 用户名或密码错误";
        } else if (upper.contains("ORA-12541") || upper.contains("ORA-12514")) {
            hint = "Oracle 监听未启动或服务名错误";
        }
        if (hint != null) {
            sb.append("  → 可能原因: ").append(hint);
        }
        sb.append("  (SQLState=").append(e.getSQLState())
                .append(", ErrorCode=").append(e.getErrorCode()).append(")");
        return sb.toString();
    }

    /**
     * 从 properties 文件加载数据库配置
     * 支持 db.1.xxx / db.2.xxx 这样的多库配置
     */
    public static List<DbConfig> loadConfigFromFile(String filePath) {
        List<DbConfig> configs = new ArrayList<>();
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("[警告] 配置文件不存在: " + filePath);
            return configs;
        }

        Properties props = new Properties();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            System.out.println("[错误] 读取配置文件失败: " + e.getMessage());
            return configs;
        }

        // 找所有 db.<序号>.name 的序号
        Set<String> indexes = new TreeSet<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("db.") && key.endsWith(".name")) {
                String idx = key.substring("db.".length(), key.length() - ".name".length());
                indexes.add(idx);
            }
        }

        for (String idx : indexes) {
            String prefix = "db." + idx + ".";
            DbConfig config = new DbConfig(
                    props.getProperty(prefix + "name", "未命名数据库"),
                    props.getProperty(prefix + "driver", ""),
                    props.getProperty(prefix + "url", ""),
                    props.getProperty(prefix + "username", ""),
                    props.getProperty(prefix + "password", ""),
                    props.getProperty(prefix + "testSql", "")
            );
            configs.add(config);
        }

        System.out.println("从配置文件解析到 " + configs.size() + " 个数据库配置");
        return configs;
    }

    /**
     * 打印汇总报告
     */
    private static void printSummary(List<TestResult> results) {
        int successCount = 0;
        long totalCost = 0;
        for (TestResult r : results) {
            if (r.success) {
                successCount++;
            }
            totalCost += r.costMs;
        }

        System.out.println("----------------------------------------------------------");
        System.out.println("汇总: 共 " + results.size() + " 个数据库, 成功 "
                + successCount + " 个, 失败 " + (results.size() - successCount)
                + " 个, 总耗时 " + totalCost + "ms");
        if (successCount == results.size()) {
            System.out.println("结论: 所有数据库连接正常");
        } else {
            System.out.println("结论: 存在连接异常的数据库，请检查上方失败项");
        }
        System.out.println("==========================================================");
    }

    /**
     * 生成 HTML 报告
     */
    private static void writeHtmlReport(List<TestResult> results, String filePath) throws IOException {
        int successCount = 0;
        for (TestResult r : results) {
            if (r.success) {
                successCount++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        sb.append("<title>数据库连接测试报告</title>");
        sb.append("<style>");
        sb.append("body{font-family:'Microsoft YaHei',Arial,sans-serif;margin:24px;}")
                .append("h1{font-size:20px;}table{border-collapse:collapse;width:100%;}")
                .append("th,td{border:1px solid #ddd;padding:8px;font-size:13px;text-align:left;}")
                .append("th{background:#f5f5f5;}tr.success{background:#e8f7e8;}")
                .append("tr.fail{background:#fde8e8;}.ok{color:#1a8f1a;font-weight:bold;}")
                .append(".bad{color:#c0392b;font-weight:bold;}");
        sb.append("</style></head><body>");
        sb.append("<h1>数据库连接测试报告</h1>");
        sb.append("<p>共 ").append(results.size()).append(" 个数据库，成功 ")
                .append(successCount).append(" 个，失败 ")
                .append(results.size() - successCount).append(" 个</p>");
        sb.append("<table><tr><th>名称</th><th>连接地址</th><th>状态</th>")
                .append("<th>耗时(ms)</th><th>数据库版本</th><th>说明</th></tr>");

        for (TestResult r : results) {
            sb.append("<tr class=\"").append(r.success ? "success" : "fail").append("\">");
            sb.append("<td>").append(escapeHtml(r.name)).append("</td>");
            sb.append("<td>").append(escapeHtml(r.url)).append("</td>");
            sb.append("<td class=\"").append(r.success ? "ok" : "bad").append("\">")
                    .append(r.success ? "成功" : "失败").append("</td>");
            sb.append("<td>").append(r.costMs).append("</td>");
            sb.append("<td>").append(r.success ? escapeHtml(r.dbProduct + " " + r.dbVersion) : "-").append("</td>");
            sb.append("<td>").append(r.success ? "-" : escapeHtml(r.errorMessage)).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table></body></html>");

        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void closeQuietly(ResultSet rs, Statement stmt, Connection conn) {
        if (rs != null) {
            try { rs.close(); } catch (SQLException ignored) { }
        }
        if (stmt != null) {
            try { stmt.close(); } catch (SQLException ignored) { }
        }
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) { }
        }
    }
}