import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 错误日志工具类
 * 用于记录和输出系统运行时的错误信息
 */
public class ErrorLogger {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_LOG_FILE = "error.log";
    private static String logFilePath = DEFAULT_LOG_FILE;

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
     * 设置日志文件路径
     *
     * @param path 日志文件路径
     */
    public static void setLogFilePath(String path) {
        logFilePath = path;
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

        // 写入文件
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
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFilePath, true))) {
            writer.println(logEntry);
            if (e != null) {
                e.printStackTrace(writer);
            }
        } catch (IOException ex) {
            System.err.println("无法写入日志文件: " + logFilePath);
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