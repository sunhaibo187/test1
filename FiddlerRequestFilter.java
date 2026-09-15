import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fiddler抓包脚本过滤器
 * 从Fiddler导出的抓包文本中过滤掉HTML、JS、CSS等静态资源请求，只保留HTTP接口请求
 *
 * 支持两种Fiddler导出格式：
 *   1. 纯文本导出（Fiddler -> File -> Export Sessions -> TextWizard）
 *   2. HAR格式（JSON）
 *
 * 使用方式：
 *   java FiddlerRequestFilter <输入文件> [输出文件]
 *   若不指定输出文件，默认输出到 <输入文件名>_filtered.txt
 */
public class FiddlerRequestFilter {

    /** HTTP请求方法正则 */
    private static final Pattern REQUEST_LINE_PATTERN = Pattern.compile(
            "^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+https?://\\S+");

    /** 静态资源扩展名黑名单（按URL路径判断） */
    private static final Set<String> STATIC_EXTENSIONS = new HashSet<>(Arrays.asList(
            "html", "htm", "js", "css", "png", "jpg", "jpeg", "gif", "ico",
            "svg", "webp", "avif", "woff", "woff2", "ttf", "eot", "map",
            "mp4", "mp3", "webm", "zip", "jar", "pdf", "doc", "docx", "xls", "xlsx"
    ));

    /** 需要过滤的Content-Type关键词 */
    private static final List<String> FILTER_CONTENT_TYPES = Arrays.asList(
            "text/html", "application/javascript", "text/css",
            "image/", "font/", "application/pdf"
    );

    /** 常见统计信息 */
    private static int totalRequests = 0;
    private static int keptRequests = 0;
    private static int filteredRequests = 0;

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            return;
        }

        String inputFile = args[0];
        String outputFile = args.length >= 2 ? args[1] : getDefaultOutputFile(inputFile);

        try {
            File file = new File(inputFile);
            if (!file.exists()) {
                System.err.println("❌ 输入文件不存在: " + inputFile);
                return;
            }

            String content = readFile(inputFile);
            String filtered = process(content);

            writeFile(outputFile, filtered);
            System.out.println("✅ 处理完成！");
            System.out.println("   总请求数:   " + totalRequests);
            System.out.println("   保留请求数: " + keptRequests + " (HTTP接口请求)");
            System.out.println("   过滤请求数: " + filteredRequests + " (静态资源/HTML/JS)");
            System.out.println("   输出文件:   " + outputFile);

        } catch (Exception e) {
            System.err.println("❌ 处理失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 核心处理逻辑：过滤静态资源，保留HTTP接口请求
     */
    public static String process(String content) {
        String trimmed = content.trim();

        // 判断是否为HAR格式（JSON）
        if (trimmed.startsWith("{") && content.contains("\"log\"") && content.contains("\"entries\"")) {
            return processHarFormat(content);
        }
        return processTextFormat(content);
    }

    /**
     * 处理Fiddler纯文本导出格式
     * 格式示例：
     *   GET http://example.com/api/user HTTP/1.1
     *   Host: example.com
     *   ...
     *   <空行>
     */
    private static String processTextFormat(String content) {
        StringBuilder result = new StringBuilder();
        String[] lines = content.split("\\r?\\n");

        boolean inRequest = false;
        boolean keepCurrent = false;
        StringBuilder currentRequest = new StringBuilder();
        String currentContentType = "";

        for (String line : lines) {
            Matcher matcher = REQUEST_LINE_PATTERN.matcher(line.trim());
            if (matcher.find()) {
                // 上一个请求处理结束，输出保留的请求
                if (inRequest && keepCurrent) {
                    result.append(currentRequest).append("\n");
                }

                // 开始新请求
                totalRequests++;
                inRequest = true;
                currentRequest = new StringBuilder();
                currentContentType = "";
                keepCurrent = false;

                String url = extractUrl(line);
                // 根据URL扩展名判断
                if (!isStaticByUrl(url)) {
                    keepCurrent = true;
                } else {
                    filteredRequests++;
                }
                currentRequest.append(line).append("\n");
                continue;
            }

            // 请求头中捕获Content-Type，辅助判断
            if (inRequest && line.toLowerCase().startsWith("content-type:")) {
                currentContentType = line.substring(line.indexOf(':') + 1).trim().toLowerCase();
            }

            // 请求头捕获后，如果没有被URL过滤，再根据Content-Type判断（针对无扩展名的动态资源）
            if (inRequest && keepCurrent && currentContentType.isEmpty()
                    && !line.trim().isEmpty() && !line.startsWith(" ")) {
                // 请求行后的首行非空且非请求头则进入请求体，不做判断
            }

            // 请求头结束（遇到空行）
            if (inRequest && line.trim().isEmpty()) {
                // 补充Content-Type判断：URL无扩展名但Content-Type是静态类型
                if (keepCurrent && isStaticByContentType(currentContentType)) {
                    keepCurrent = false;
                    filteredRequests++;
                }
                currentRequest.append(line).append("\n");
                continue;
            }

            if (inRequest) {
                currentRequest.append(line).append("\n");
            }
        }

        // 处理最后一个请求
        if (inRequest && keepCurrent) {
            result.append(currentRequest).append("\n");
        }

        // 重新统计保留数
        keptRequests = totalRequests - filteredRequests;
        return result.toString().replaceAll("\\n{3,}", "\n\n");
    }

    /**
     * 处理HAR格式（JSON）导出
     */
    private static String processHarFormat(String content) {
        StringBuilder result = new StringBuilder();

        // 用简单的方式解析HAR中的请求条目（避免依赖第三方JSON库）
        // 通过定位 "request" 对象中的 "method" 和 "url" 字段
        Scanner scanner = new Scanner(content);
        String currentMethod = null;
        String currentUrl = null;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.matches("\"method\"\\s*:\\s*\"(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\"")) {
                currentMethod = line.replaceAll(".*\\\"(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\\".*", "$1");
            } else if (line.startsWith("\"url\"") && currentMethod != null) {
                currentUrl = line.substring(line.indexOf(':') + 1).trim()
                        .replace("\"", "").replace(",", "");
                if (currentUrl.startsWith("http")) {
                    totalRequests++;
                    if (!isStaticByUrl(currentUrl)) {
                        keptRequests++;
                        result.append(currentMethod).append(" ").append(currentUrl).append(" HTTP/1.1\n\n");
                    } else {
                        filteredRequests++;
                    }
                }
                currentMethod = null;
            }
        }
        return result.toString();
    }

    /**
     * 根据URL扩展名判断是否为静态资源
     */
    private static boolean isStaticByUrl(String url) {
        try {
            // 去掉查询参数
            String path = url.split("\\?")[0];
            // 提取扩展名
            int lastDot = path.lastIndexOf('.');
            int lastSlash = path.lastIndexOf('/');
            if (lastDot > lastSlash && lastDot >= 0 && lastDot < path.length() - 1) {
                String ext = path.substring(lastDot + 1).toLowerCase();
                if (STATIC_EXTENSIONS.contains(ext)) {
                    return true;
                }
            }
            // 无扩展名或非静态扩展名 -> 认为是接口
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 根据Content-Type判断是否为静态资源
     */
    private static boolean isStaticByContentType(String contentType) {
        if (contentType.isEmpty()) {
            return false;
        }
        for (String filter : FILTER_CONTENT_TYPES) {
            if (contentType.contains(filter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从请求行中提取URL
     */
    private static String extractUrl(String requestLine) {
        String[] parts = requestLine.trim().split("\\s+");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "";
    }

    /**
     * 读取文件内容
     */
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

    /**
     * 写入文件内容
     */
    private static void writeFile(String filePath, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }

    /**
     * 生成默认输出文件名
     */
    private static String getDefaultOutputFile(String inputFile) {
        int dotIndex = inputFile.lastIndexOf('.');
        if (dotIndex > 0) {
            return inputFile.substring(0, dotIndex) + "_filtered" + inputFile.substring(dotIndex);
        }
        return inputFile + "_filtered.txt";
    }

    /**
     * 打印使用帮助
     */
    private static void printUsage() {
        System.out.println("Fiddler抓包脚本过滤器");
        System.out.println("功能：从Fiddler导出的抓包文本中过滤掉HTML、JS、CSS、图片等静态资源，只保留HTTP接口请求");
        System.out.println();
        System.out.println("用法：");
        System.out.println("  java FiddlerRequestFilter <输入文件> [输出文件]");
        System.out.println();
        System.out.println("参数：");
        System.out.println("  输入文件  - Fiddler导出的抓包文本文件（.txt 或 .har）");
        System.out.println("  输出文件  - (可选) 过滤后的结果文件，默认 <输入文件名>_filtered.txt");
        System.out.println();
        System.out.println("示例：");
        System.out.println("  java FiddlerRequestFilter fiddler_export.txt");
        System.out.println("  java FiddlerRequestFilter fiddler_export.har api_requests.txt");
        System.out.println();
        System.out.println("支持的静态资源类型：html、js、css、图片、字体、音视频等");
    }
}