import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 错误日志工具类
 * 用于记录和输出系统运行时的错误信息，支持按日期滚动日志文件
 */
public class ErrorLogger {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_LOG_BASE_NAME = "error";
    private static final String DEFAULT_LOG_EXTENSION = ".log";

    /** 日志基础文件名（不含日期和扩展名） */
    private static String logBaseName = DEFAULT_LOG_BASE_NAME;
    /** 日志文件目录 */
    private static String logDir = ".";
    /** 当前日志文件对应的日期 */
    private static LocalDate currentLogDate = LocalDate.now();
    /** 日志文件保留天数（0表示不限制，始终保留） */
    private static int maxHistory = 30;

    /**
     * 错误级别枚举
     */
    public enum Level {
        DEBUG("DEBUG"),
        INFO("INFO"),
        WARN("WARN"),
        ERROR("ERROR"),
        FATAL("FATAL");

        private final String label;

        Level(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 设置日志基础文件名（不含日期和扩展名）
     *
     * @param baseName 基础文件名，如 "error" 会生成 error_2026-09-10.log
     */
    public static void setLogBaseName(String baseName) {
        logBaseName = baseName;
    }

    /**
     * 设置日志文件目录
     *
     * @param dir 日志目录路径
     */
    public static void setLogDir(String dir) {
        logDir = dir;
    }

    /**
     * 设置日志文件保留天数（滚动时自动清理过期文件）
     *
     * @param days 保留天数，0表示不限制
     */
    public static void setMaxHistory(int days) {
        maxHistory = Math.max(0, days);
    }

    /**
     * 获取当前日期的日志文件路径
     *
     * @return 当前日志文件路径，如 "./error_2026-09-10.log"
     */
    public static String getCurrentLogFilePath() {
        return logDir + File.separator + logBaseName + "_" + LocalDate.now().format(FILE_DATE_FORMAT) + DEFAULT_LOG_EXTENSION;
    }

    /**
     * 检查并执行日志日期滚动
     * 如果当前日期与上次日志日期不同，则切换到新日期的日志文件，并清理过期文件
     */
    private static synchronized void rollIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentLogDate)) {
            currentLogDate = today;
            cleanOldLogs();
        }
    }

    /**
     * 清理超过保留天数的旧日志文件
     */
    private static void cleanOldLogs() {
        if (maxHistory <= 0) {
            return;
        }
        LocalDate cutoffDate = LocalDate.now().minusDays(maxHistory);
        try {
            Path dirPath = Paths.get(logDir);
            if (!Files.exists(dirPath)) {
                return;
            }
            try (Stream<Path> files = Files.list(dirPath)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.startsWith(logBaseName + "_") && name.endsWith(DEFAULT_LOG_EXTENSION);
                        })
                        .filter(path -> {
                            try {
                                String datePart = path.getFileName().toString()
                                        .replace(logBaseName + "_", "")
                                        .replace(DEFAULT_LOG_EXTENSION, "");
                                LocalDate fileDate = LocalDate.parse(datePart, FILE_DATE_FORMAT);
                                return fileDate.isBefore(cutoffDate);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException ignored) {
                                System.err.println("无法删除过期日志文件: " + path);
                            }
                        });
            }
        } catch (IOException e) {
            System.err.println("清理旧日志文件时出错: " + e.getMessage());
        }
    }

    /**
     * 记录错误日志（默认ERROR级别）
     *
     * @param message 错误信息
     */
    public static void log(String message) {
        log(Level.ERROR, message, null);
    }

    /**
     * 记录指定级别的日志
     *
     * @param level   日志级别
     * @param message 日志信息
     */
    public static void log(Level level, String message) {
        log(level, message, null);
    }

    /**
     * 记录异常日志（默认ERROR级别）
     *
     * @param message 错误信息
     * @param e       异常对象
     */
    public static void log(String message, Throwable e) {
        log(Level.ERROR, message, e);
    }

    /**
     * 记录指定级别的异常日志
     *
     * @param level   日志级别
     * @param message 日志信息
     * @param e       异常对象
     */
    public static void log(Level level, String message, Throwable e) {
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String threadName = Thread.currentThread().getName();
        String className = getCallerClassName();
        String logEntry = formatLogEntry(timestamp, level, threadName, className, message);

        // 输出到控制台
        System.err.println(logEntry);

        // 输出异常堆栈
        if (e != null) {
            e.printStackTrace(System.err);
        }

        // 按日期滚动并写入文件
        rollIfNeeded();
        writeToFile(logEntry, e);
    }

    /**
     * 格式化日志条目
     */
    private static String formatLogEntry(String timestamp, Level level, String threadName,
                                          String className, String message) {
        return String.format("[%s] [%s] [%s] [%s] - %s",
                timestamp, level.getLabel(), threadName, className, message);
    }

    /**
     * 获取调用者的类名
     */
    private static String getCallerClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (!className.equals(ErrorLogger.class.getName())
                    && !className.equals(Thread.class.getName())) {
                return className;
            }
        }
        return "Unknown";
    }

    /**
     * 将日志写入文件
     */
    private static void writeToFile(String logEntry, Throwable e) {
        String filePath = getCurrentLogFilePath();
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, true))) {
            writer.println(logEntry);
            if (e != null) {
                e.printStackTrace(writer);
            }
        } catch (IOException ex) {
            System.err.println("无法写入日志文件: " + filePath);
            ex.printStackTrace(System.err);
        }
    }

    /**
     * 格式化异常堆栈为字符串
     *
     * @param e 异常对象
     * @return 异常堆栈字符串
     */
    public static String getStackTraceString(Throwable e) {
        if (e == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        Throwable cause = e.getCause();
        if (cause != null) {
            sb.append("Caused by: ");
            sb.append(getStackTraceString(cause));
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        // 配置日志：基础名称为 "app"，存放目录为 "./logs"，保留7天
        ErrorLogger.setLogBaseName("app");
        ErrorLogger.setLogDir("./logs");
        ErrorLogger.setMaxHistory(7);

        System.out.println("当前日志文件: " + ErrorLogger.getCurrentLogFilePath());

        // 演示：记录不同级别的错误日志
        ErrorLogger.log(Level.DEBUG, "系统启动，开始初始化配置");
        ErrorLogger.log(Level.INFO, "用户登录成功，用户名: admin");
        ErrorLogger.log(Level.WARN, "数据库连接池使用率超过80%");

        try {
            int result = 10 / 0;
        } catch (Exception e) {
            ErrorLogger.log(Level.ERROR, "执行除法运算时发生异常", e);
        }

        try {
            throw new IOException("读取配置文件失败: config.properties 不存在");
        } catch (IOException e) {
            ErrorLogger.log(Level.FATAL, "系统关键文件缺失，无法继续运行", e);
        }

        // 演示获取异常堆栈字符串
        try {
            String str = null;
            str.length();
        } catch (Exception e) {
            String stackTrace = ErrorLogger.getStackTraceString(e);
            ErrorLogger.log(Level.ERROR, "空指针异常详情:\n" + stackTrace);
        }
    }
}