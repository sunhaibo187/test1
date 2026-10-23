// 测试
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * LoadRunner 脚本转 Fiddler 脚本格式工具
 *
 * 功能：
 *   将 VuGen 录制的 LoadRunner 脚本（web_url / web_submit_data / web_custom_request）
 *   解析为统一的 HTTP 请求模型，并输出为 Fiddler 可用的两种格式：
 *     1. FiddlerScript（CustomRules.js）—— 内置规则，Fiddler 启动后自动重放这些请求
 *     2. Raw HTTP 请求文本 —— 可直接粘贴到 Fiddler 的 Composer（Composer -> Raw）中发送
 *
 * 支持的 LoadRunner 函数：
 *   - web_url            普通 GET/资源请求
 *   - web_submit_data    表单提交（ITEMDATA: Name/Value 多个参数）
 *   - web_custom_request 自定义请求（Body 直接携带报文）
 *
 * 支持的环境参数：
 *   - 通过 -base <url> 覆盖脚本中录制时的服务器地址（换环境时很实用）
 *   - 参数化占位符 {param} 会原样保留并在输出头注释中列出，方便替换
 *
 * 使用方式：
 *   java LoadRunnerToFiddlerConverter                        使用内置示例演示
 *   java LoadRunnerToFiddlerConverter script.c               解析脚本并打印两种格式
 *   java LoadRunnerToFiddlerConverter script.c out.js        解析并写出 CustomRules.js
 *   java LoadRunnerToFiddlerConverter script.c out.js -base http://10.0.0.1:8080
 */
public class LoadRunnerToFiddlerConverter {

    /** 解析出的单个 HTTP 请求 */
    public static class HttpRequest {
        String name = "";                       // 事务/请求名（LR 中的函数名）
        String method = "GET";                  // 请求方法
        String url = "";                        // 完整 URL
        String contentType = "";                // 请求 Content-Type
        String referer = "";                    // 来源页
        String body = "";                       // 请求体（自定义请求）
        List<String[]> params = new ArrayList<>(); // 表单参数 [name, value]
        String source = "";                     // 来源函数名

        boolean hasBody() {
            return body != null && !body.isEmpty();
        }

        boolean hasParams() {
            return params != null && !params.isEmpty();
        }
    }

    /** 解析出的参数化占位符（去重） */
    private static final List<String> PARAM_PLACEHOLDERS = new ArrayList<>();

    public static void main(String[] args) {
        String lrScript;
        String outputFile = null;
        String baseUrl = null;

        if (args.length == 0) {
            lrScript = DEMO_SCRIPT;
            System.out.println(">>> 未指定输入文件，使用内置示例脚本演示\n");
        } else {
            try {
                lrScript = readFile(args[0]);
            } catch (IOException e) {
                System.err.println("读取脚本文件失败: " + e.getMessage());
                return;
            }
            if (args.length >= 2 && !args[1].startsWith("-")) {
                outputFile = args[1];
            }
        }

        // 解析 -base 参数
        for (int i = 0; i < args.length; i++) {
            if ("-base".equals(args[i]) && i + 1 < args.length) {
                baseUrl = args[i + 1];
            }
        }

        // 1. 解析
        List<HttpRequest> requests = parse(lrScript);
        if (requests.isEmpty()) {
            System.out.println("未从脚本中解析到任何 LoadRunner HTTP 请求（web_url / web_submit_data / web_custom_request）");
            return;
        }

        // 2. 换环境：统一替换 host
        if (baseUrl != null && !baseUrl.isEmpty()) {
            applyBaseUrl(requests, baseUrl);
            System.out.println("已将所有请求的服务器地址替换为: " + baseUrl);
        }

        System.out.println("共解析到 " + requests.size() + " 个 HTTP 请求");
        if (!PARAM_PLACEHOLDERS.isEmpty()) {
            System.out.println("检测到参数化占位符（请按需替换为真实值）: " + String.join(", ", PARAM_PLACEHOLDERS));
        }
        System.out.println();

        // 3. 生成两种格式
        String fiddlerScript = toFiddlerScript(requests);
        String rawHttp = toRawHttp(requests);

        // 4. 输出
        System.out.println("==================== 格式一：FiddlerScript (CustomRules.js) ====================");
        System.out.println(fiddlerScript);
        System.out.println("==================== 格式二：Raw HTTP 请求（粘贴到 Fiddler Composer）==================== ");
        System.out.println(rawHttp);

        if (outputFile != null) {
            try {
                Files.write(Paths.get(outputFile), fiddlerScript.getBytes(StandardCharsets.UTF_8));
                System.out.println("已写出 FiddlerScript 文件: " + outputFile);
                String rawFile = outputFile.replaceAll("\\.[^.]+$", "") + "_raw.txt";
                Files.write(Paths.get(rawFile), rawHttp.getBytes(StandardCharsets.UTF_8));
                System.out.println("已写出 Raw HTTP 文件:     " + rawFile);
            } catch (IOException e) {
                System.err.println("写出文件失败: " + e.getMessage());
            }
        }

        System.out.println("\n完成。");
    }

    // ==================================================================
    //  一、解析 LoadRunner 脚本
    // ==================================================================

    /**
     * 解析 LoadRunner 脚本，抽取所有 HTTP 请求
     */
    public static List<HttpRequest> parse(String lrScript) {
        PARAM_PLACEHOLDERS.clear();
        List<HttpRequest> result = new ArrayList<>();
        if (lrScript == null) {
            return result;
        }

        String[] functions = {"web_custom_request", "web_submit_data", "web_url"};

        int idx = 0;
        while (idx < lrScript.length()) {
            // 找到下一个出现的 LR 函数
            int nextPos = -1;
            String nextFunc = null;
            for (String f : functions) {
                int p = lrScript.indexOf(f + "(", idx);
                if (p >= 0 && (nextPos < 0 || p < nextPos)) {
                    nextPos = p;
                    nextFunc = f;
                }
            }
            if (nextPos < 0) {
                break;
            }

            // 截取本次函数调用的完整参数块（到 "LAST);" 结束）
            int end = lrScript.indexOf("LAST)", nextPos);
            if (end < 0) {
                end = lrScript.length();
            }
            String block = lrScript.substring(nextPos, end);

            HttpRequest req = parseBlock(nextFunc, block);
            if (req != null && !req.url.isEmpty()) {
                result.add(req);
            }
            idx = end + 5;
        }

        return result;
    }

    /** 解析单个函数调用块 */
    private static HttpRequest parseBlock(String func, String block) {
        HttpRequest req = new HttpRequest();
        req.source = func;

        // 提取所有被双引号包裹的参数串
        List<String> tokens = extractStringLiterals(block);

        boolean itemMode = false;
        for (String token : tokens) {
            String t = token.trim();

            if (t.equalsIgnoreCase("ITEMDATA")) {
                itemMode = true;
                continue;
            }
            if (t.equalsIgnoreCase("ENDITEM")) {
                itemMode = false;
                continue;
            }

            if (itemMode) {
                // ITEMDATA 内部：交替出现 Name=xxx, Value=yyy
                continue;
            }

            if (req.name.isEmpty() && !t.contains("=") && func.equalsIgnoreCase(t)) {
                // 函数名本身不作为 token 处理（extractStringLiterals 不含函数名）
            }

            // 第一个不含 "=" 的引号串视作请求名
            if (req.name.isEmpty() && !t.contains("=")
                    && !t.equalsIgnoreCase("ITEMDATA") && !t.equalsIgnoreCase("ENDITEM")) {
                req.name = t;
                continue;
            }

            int eq = t.indexOf('=');
            if (eq > 0) {
                String key = t.substring(0, eq).trim();
                String value = t.substring(eq + 1).trim();
                switch (key) {
                    case "URL":
                    case "Action":
                        req.url = value;
                        break;
                    case "Method":
                        req.method = value.toUpperCase();
                        break;
                    case "Body":
                        req.body = value;
                        break;
                    case "EncType":
                    case "ContentType":
                        req.contentType = value;
                        break;
                    case "Referer":
                        req.referer = value;
                        break;
                    default:
                        break;
                }
            }
        }

        // 单独解析 ITEMDATA 的 Name/Value 对
        req.params = parseItemData(block);

        // web_url 默认 GET
        if ("web_url".equalsIgnoreCase(func)) {
            req.method = "GET";
        }

        // 表单提交若无显式方法，默认 POST
        if ("web_submit_data".equalsIgnoreCase(func) && req.hasParams() && "GET".equals(req.method)) {
            req.method = "POST";
        }

        // 收集参数化占位符 {xxx}
        collectPlaceholders(req.url);
        collectPlaceholders(req.body);
        for (String[] p : req.params) {
            collectPlaceholders(p[1]);
        }

        return req;
    }

    /** 解析 ITEMDATA 中的 Name / Value 参数对 */
    private static List<String[]> parseItemData(String block) {
        List<String[]> params = new ArrayList<>();
        int start = block.indexOf("ITEMDATA");
        if (start < 0) {
            return params;
        }
        String section = block.substring(start);
        List<String> tokens = extractStringLiterals(section);

        String pendingName = null;
        for (String token : tokens) {
            String t = token.trim();
            if (t.equalsIgnoreCase("ITEMDATA") || t.equalsIgnoreCase("ENDITEM")) {
                continue;
            }
            int eq = t.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = t.substring(0, eq).trim();
            String value = t.substring(eq + 1).trim();
            if ("Name".equalsIgnoreCase(key)) {
                pendingName = value;
            } else if ("Value".equalsIgnoreCase(key) && pendingName != null) {
                params.add(new String[]{pendingName, value});
                pendingName = null;
            }
        }
        return params;
    }

    /** 提取字符串中的所有双引号字面量内容（自动处理转义 \" 与 \\） */
    private static List<String> extractStringLiterals(String text) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inStr = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!inStr) {
                if (c == '"') {
                    inStr = true;
                    sb.setLength(0);
                }
            } else {
                if (c == '\\' && i + 1 < text.length()) {
                    char n = text.charAt(i + 1);
                    // 还原常见转义
                    switch (n) {
                        case '"':  sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        default:   sb.append('\\').append(n); break;
                    }
                    i++;
                } else if (c == '"') {
                    inStr = false;
                    list.add(sb.toString());
                } else {
                    sb.append(c);
                }
            }
        }
        return list;
    }

    /** 参数化占位符正则：仅匹配 {字母数字下划线}，避免把 JSON 报文的 {} 误判为参数 */
    private static final java.util.regex.Pattern PLACEHOLDER_PATTERN =
            java.util.regex.Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    /** 收集 {参数} 占位符 */
    private static void collectPlaceholders(String text) {
        if (text == null) {
            return;
        }
        java.util.regex.Matcher m = PLACEHOLDER_PATTERN.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            if (!PARAM_PLACEHOLDERS.contains(name)) {
                PARAM_PLACEHOLDERS.add(name);
            }
        }
    }

    /** 用新的 base 地址替换请求 URL 中的协议+host+port 部分 */
    private static void applyBaseUrl(List<HttpRequest> requests, String baseUrl) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        for (HttpRequest r : requests) {
            int schemeIdx = r.url.indexOf("://");
            if (schemeIdx < 0) {
                continue;
            }
            int pathStart = r.url.indexOf('/', schemeIdx + 3);
            String path = pathStart >= 0 ? r.url.substring(pathStart) : "/";
            r.url = normalizedBase + path;
        }
    }

    // ==================================================================
    //  二、转换为 FiddlerScript（CustomRules.js）
    // ==================================================================

    /**
     * 生成 FiddlerScript（CustomRules.js）代码：
     * 在 Fiddler 启动时自动重放脚本中的全部请求，并打印每个请求的响应码
     */
    public static String toFiddlerScript(List<HttpRequest> requests) {
        StringBuilder sb = new StringBuilder();
        sb.append("// ============================================================\n");
        sb.append("// 由 LoadRunner 脚本自动转换生成的 FiddlerScript (CustomRules.js)\n");
        sb.append("// 使用方式：Fiddler -> Rules -> Customize Rules... 覆盖保存后重启 Fiddler\n");
        sb.append("// 生成时间：").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date())).append("\n");
        sb.append("// 请求数量：").append(requests.size()).append("\n");
        if (!PARAM_PLACEHOLDERS.isEmpty()) {
            sb.append("// 参数化占位符（请在使用前替换为真实值）：").append(String.join(", ", PARAM_PLACEHOLDERS)).append("\n");
        }
        sb.append("// ============================================================\n\n");

        sb.append("import System;\nimport System.Windows.Forms;\n\n");

        sb.append("class Handlers\n{\n");
        sb.append("    // 是否只在启动时执行一次重放\n");
        sb.append("    static var replayed = false;\n\n");

        sb.append("    static function OnBoot() {\n");
        sb.append("        if (replayed) return;\n");
        sb.append("        replayed = true;\n");
        sb.append("        FiddlerObject.StatusText = \"LoadRunner 脚本重放中...\";\n");

        for (int i = 0; i < requests.size(); i++) {
            HttpRequest r = requests.get(i);
            String comment = sanitizeComment(r.name.isEmpty() ? ("request_" + (i + 1)) : r.name);
            sb.append("\n        // ---------- [").append(i + 1).append("] ").append(comment).append(" ----------\n");
            sb.append("        try {\n");
            sb.append("            ").append(buildIssueRequest(r, "            ")).append("\n");
            sb.append("            FiddlerObject.log(\"[").append(i + 1).append("] ").append(comment)
              .append(" => \" + oSes.oResponse[\"x-responseCode\"]);\n");
            sb.append("        } catch (e) {\n");
            sb.append("            FiddlerObject.log(\"[").append(i + 1).append("] ").append(comment)
              .append(" 执行失败: \" + e);\n");
            sb.append("        }\n");
        }

        sb.append("\n        FiddlerObject.StatusText = \"LoadRunner 脚本重放完成\";\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** 构造 FiddlerObject.utilIssueRequest 语句（含请求头与请求体） */
    private static String buildIssueRequest(HttpRequest r, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("var oSes = FiddlerObject.utilIssueRequest(\"")
          .append(escapeJs(r.method + " " + r.url + " HTTP/1.1")).append("\\r\\n");

        sb.append(escapeJs("Host: " + extractHost(r.url))).append("\\r\\n");
        sb.append("User-Agent: Fiddler-Replay\\r\\n");

        if (r.hasParams() && !r.hasBody()) {
            String form = buildFormBody(r.params);
            sb.append("Content-Type: application/x-www-form-urlencoded\\r\\n");
            sb.append("Content-Length: ").append(form.getBytes(StandardCharsets.UTF_8).length).append("\\r\\n");
            sb.append("\\r\\n");
            sb.append(escapeJs(form)).append("\");");
            return sb.toString();
        }

        if (r.hasBody()) {
            String ct = r.contentType.isEmpty() ? "application/json" : r.contentType;
            sb.append("Content-Type: ").append(escapeJs(ct)).append("\\r\\n");
            sb.append("Content-Length: ").append(r.body.getBytes(StandardCharsets.UTF_8).length).append("\\r\\n");
            sb.append("\\r\\n");
            sb.append(escapeJs(r.body)).append("\");");
            return sb.toString();
        }

        // 无请求体
        sb.append("\\r\\n\").utilSetRequestBody(\"\");");
        return sb.toString();
    }

    /** 生成 form 表单请求体 user=admin&pwd=123 */
    private static String buildFormBody(List<String[]> params) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append(params.get(i)[0]).append('=').append(params.get(i)[1]);
        }
        return sb.toString();
    }

    // ==================================================================
    //  三、转换为 Raw HTTP 请求文本（供 Fiddler Composer 使用）
    // ==================================================================

    /**
     * 生成原始 HTTP 请求文本，多个请求之间用分隔线隔开，
     * 可直接复制单段粘贴到 Fiddler 的 Composer -> Raw 中发送
     */
    public static String toRawHttp(List<HttpRequest> requests) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < requests.size(); i++) {
            HttpRequest r = requests.get(i);
            sb.append("#################### [").append(i + 1).append("] ")
              .append(r.name.isEmpty() ? ("request_" + (i + 1)) : r.name)
              .append(" ####################\n");

            sb.append(r.method).append(' ').append(extractPath(r.url)).append(" HTTP/1.1\r\n");
            sb.append("Host: ").append(extractHost(r.url)).append("\r\n");
            sb.append("User-Agent: Fiddler-Replay\r\n");

            if (r.referer != null && !r.referer.isEmpty()) {
                sb.append("Referer: ").append(r.referer).append("\r\n");
            }

            if (r.hasParams() && !r.hasBody()) {
                String form = buildFormBody(r.params);
                sb.append("Content-Type: application/x-www-form-urlencoded\r\n");
                sb.append("Content-Length: ").append(form.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
                sb.append("\r\n").append(form).append("\r\n");
            } else if (r.hasBody()) {
                String ct = r.contentType.isEmpty() ? "application/json" : r.contentType;
                sb.append("Content-Type: ").append(ct).append("\r\n");
                sb.append("Content-Length: ").append(r.body.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
                sb.append("\r\n").append(r.body).append("\r\n");
            } else {
                sb.append("\r\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ==================================================================
    //  工具方法
    // ==================================================================

    /** 从完整 URL 中提取 host[:port] */
    private static String extractHost(String url) {
        int s = url.indexOf("://");
        if (s < 0) {
            return url;
        }
        int start = s + 3;
        int end = url.indexOf('/', start);
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }

    /** 从完整 URL 中提取路径（含 query） */
    private static String extractPath(String url) {
        int s = url.indexOf("://");
        if (s < 0) {
            return url;
        }
        int start = url.indexOf('/', s + 3);
        return start < 0 ? "/" : url.substring(start);
    }

    /** JS 字符串转义 */
    private static String escapeJs(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 去掉注释中的换行，避免破坏生成代码结构 */
    private static String sanitizeComment(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String readFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ==================================================================
    //  内置演示脚本
    // ==================================================================

    private static final String DEMO_SCRIPT =
            "web_url(\"login_page\",\n" +
            "    \"URL=http://10.20.30.40:8080/boss/login\",\n" +
            "    \"Resource=0\",\n" +
            "    \"RecContentType=text/html\",\n" +
            "    \"Referer=\",\n" +
            "    \"Snapshot=t1.inf\",\n" +
            "    \"Mode=HTTP\",\n" +
            "    LAST);\n\n" +
            "web_submit_data(\"do_login\",\n" +
            "    \"Action=http://10.20.30.40:8080/boss/api/login\",\n" +
            "    \"Method=POST\",\n" +
            "    \"EncType=application/x-www-form-urlencoded\",\n" +
            "    \"Referer=http://10.20.30.40:8080/boss/login\",\n" +
            "    \"Snapshot=t2.inf\",\n" +
            "    \"Mode=HTTP\",\n" +
            "    ITEMDATA,\n" +
            "        \"Name=userName\", \"Value={user}\", ENDITEM,\n" +
            "        \"Name=password\", \"Value={pwd}\", ENDITEM,\n" +
            "    LAST);\n\n" +
            "web_custom_request(\"query_order\",\n" +
            "    \"URL=http://10.20.30.40:8080/boss/api/order/query\",\n" +
            "    \"Method=POST\",\n" +
            "    \"Resource=0\",\n" +
            "    \"Referer=http://10.20.30.40:8080/boss/order\",\n" +
            "    \"Body={\\\"orderId\\\":\\\"123456\\\",\\\"pageNo\\\":1,\\\"pageSize\\\":20}\",\n" +
            "    \"Snapshot=t3.inf\",\n" +
            "    LAST);\n";
}
