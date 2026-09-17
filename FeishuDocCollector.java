import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书文档采集与邮件汇总程序
 *
 * 功能：
 *   1. 通过飞书开放平台API获取 tenant_access_token（带缓存）
 *   2. 根据文档ID/文档URL读取飞书文档标题与纯文本内容
 *   3. 将多个文档内容汇总，生成规范化的邮件内容（复用 EmailFormatter）
 *
 * 前置条件：
 *   1. 在飞书开放平台创建企业自建应用，获取 app_id 与 app_secret
 *   2. 在应用权限中开通：获取文档内容（docx:document:readonly）
 *   3. 将目标文档/文件夹授权给应用
 *
 * 使用方式：
 *   java FeishuDocCollector "app_id" "app_secret" "文档URL1,文档URL2,..."
 *   或通过环境变量 FEISHU_APP_ID / FEISHU_APP_SECRET 配置
 */
public class FeishuDocCollector {

    /** 飞书开放平台地址 */
    private static final String FEISHU_BASE_URL = "https://open.feishu.cn";

    /** 获取tenant_access_token接口 */
    private static final String TOKEN_URL = FEISHU_BASE_URL + "/open-apis/auth/v3/tenant_access_token/internal";

    /** 文档标题接口 */
    private static final String DOCUMENT_INFO_URL = FEISHU_BASE_URL + "/open-apis/docx/v1/documents/";

    /** 文档纯文本内容接口 */
    private static final String DOCUMENT_RAW_CONTENT_URL = FEISHU_BASE_URL + "/open-apis/docx/v1/documents/%s/raw_content";

    /** 请求超时时间（毫秒） */
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;

    private final String appId;
    private final String appSecret;
    private String cachedToken;
    private long tokenExpireTime;

    public FeishuDocCollector(String appId, String appSecret) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.cachedToken = null;
        this.tokenExpireTime = 0;
    }

    /**
     * 获取tenant_access_token（带缓存，有效期前5分钟自动刷新）
     */
    public synchronized String getTenantAccessToken() throws IOException {
        long now = System.currentTimeMillis();
        // 缓存有效期内直接返回
        if (cachedToken != null && now < tokenExpireTime - 5 * 60 * 1000) {
            return cachedToken;
        }

        String body = "{\"app_id\":\"" + appId + "\",\"app_secret\":\"" + appSecret + "\"}";
        String response = doPost(TOKEN_URL, body, null);
        String token = extractJsonString(response, "tenant_access_token");

        if (token == null || token.isEmpty()) {
            throw new IOException("获取token失败，响应: " + truncate(response, 500));
        }
        long expire = parseLong(extractJsonString(response, "expire"), 7200);
        cachedToken = token;
        tokenExpireTime = now + expire * 1000;
        System.out.println("✅ 已获取tenant_access_token，有效期 " + expire + " 秒");
        return cachedToken;
    }

    /**
     * 获取文档标题
     */
    public String getDocumentTitle(String token, String documentId) throws IOException {
        String response = doGet(DOCUMENT_INFO_URL + documentId, token);
        return extractJsonString(response, "title");
    }

    /**
     * 获取文档纯文本内容
     */
    public String getDocumentRawContent(String token, String documentId) throws IOException {
        String url = String.format(DOCUMENT_RAW_CONTENT_URL, documentId);
        String response = doGet(url, token);
        String content = extractJsonString(response, "content");
        if (content == null) {
            String code = extractJsonString(response, "code");
            String msg = extractJsonString(response, "msg");
            throw new IOException("获取文档内容失败，code=" + code + ", msg=" + msg);
        }
        return content;
    }

    /**
     * 采集单个文档，返回标题+内容
     */
    public Map<String, String> collectDocument(String token, String documentId) throws IOException {
        String title = getDocumentTitle(token, documentId);
        String content = getDocumentRawContent(token, documentId);
        if (title == null) {
            title = documentId;
        }
        Map<String, String> doc = new HashMap<>();
        doc.put("id", documentId);
        doc.put("title", title);
        doc.put("content", content);
        return doc;
    }

    /**
     * 批量采集多个文档
     */
    public List<Map<String, String>> collectDocuments(String token, List<String> documentIds) {
        List<Map<String, String>> docs = new ArrayList<>();
        for (String docId : documentIds) {
            try {
                System.out.println("📄 正在采集文档: " + docId);
                docs.add(collectDocument(token, docId));
            } catch (Exception e) {
                System.err.println("❌ 采集文档失败 [" + docId + "]: " + e.getMessage());
            }
        }
        return docs;
    }

    /**
     * 将采集到的文档汇总成邮件内容
     * 复用 EmailFormatter 生成标准邮件格式
     */
    public String buildEmailContent(List<Map<String, String>> docs, String subjectPrefix) {
        StringBuilder content = new StringBuilder();
        content.append("本次共汇总 ").append(docs.size()).append(" 篇飞书文档，内容如下：\n\n");

        int index = 1;
        for (Map<String, String> doc : docs) {
            content.append("【文档").append(index).append("】").append(doc.get("title")).append("\n");
            content.append(doc.get("content")).append("\n\n");
            index++;
        }

        // 复用 EmailFormatter 生成邮件
        String subject = subjectPrefix + " - 飞书文档汇总";
        EmailFormatter.EmailInfo email = new EmailFormatter.EmailInfo()
                .subject(subject)
                .content(content.toString())
                .progress("已完成 " + docs.size() + " 篇文档采集与汇总")
                .solution("如需补充文档，请在飞书中将文档授权给本应用后重新运行程序");

        // 生成HTML邮件（带样式）和纯文本邮件
        String htmlEmail = EmailFormatter.generateHtmlEmail(email);
        String textEmail = EmailFormatter.generateTextEmail(email);

        // 返回HTML邮件内容（兼容富文本邮件客户端）
        return htmlEmail;
    }

    /**
     * 从文档URL中提取文档ID
     * 支持格式：
     *   https://xxx.feishu.cn/docx/AbCdEf123
     *   https://xxx.feishu.cn/wiki/AbCdEf123
     *   https://xxx.feishu.cn/docs/AbCdEf123
     *   纯ID: AbCdEf123
     */
    public static String extractDocumentId(String docUrl) {
        if (docUrl == null || docUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("文档URL不能为空");
        }
        String url = docUrl.trim();
        // 已经是纯ID形式（不含/和:）
        if (!url.contains("/") && !url.contains(":") && !url.contains("?")) {
            return url;
        }
        // 提取URL最后一段
        String id = url.substring(url.lastIndexOf('/') + 1);
        // 去掉查询参数
        int queryIndex = id.indexOf('?');
        if (queryIndex > 0) {
            id = id.substring(0, queryIndex);
        }
        return id;
    }

    // ==================== HTTP工具方法 ====================

    private static String doGet(String urlStr, String token) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("Accept", "application/json");
            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            return readResponse(conn);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String doPost(String urlStr, String body, String token) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            if (token != null) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            return readResponse(conn);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        if (code >= 400) {
            throw new IOException("HTTP " + code + ": " + truncate(sb.toString(), 500));
        }
        return sb.toString();
    }

    // ==================== JSON工具方法（无第三方依赖） ====================

    /**
     * 从JSON字符串中提取指定key的字符串值（支持转义字符）
     */
    private static String extractJsonString(String json, String key) {
        if (json == null) {
            return null;
        }
        // 查找 "key" :
        String pattern = "\"" + key + "\"\\s*:";
        int keyIndex = findPattern(json, pattern);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex + key.length() + 2);
        if (colonIndex < 0) {
            return null;
        }
        // 跳过空白
        int start = colonIndex + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) {
            return null;
        }
        // 如果是字符串值
        if (json.charAt(start) == '"') {
            return unescapeJsonString(json, start + 1);
        }
        // 如果是数字或布尔值，截取到逗号或括号
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        return json.substring(start, end).trim();
    }

    private static int findPattern(String text, String pattern) {
        return text.indexOf(pattern);
    }

    /**
     * 从JSON字符串中提取字符串值（处理转义）
     */
    private static String unescapeJsonString(String json, int start) {
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'u':
                        if (i + 5 < json.length()) {
                            try {
                                String hex = json.substring(i + 2, i + 6);
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append('u');
                            }
                        } else {
                            sb.append('u');
                        }
                        break;
                    default: sb.append(next);
                }
                i += 2;
            } else if (c == '"') {
                // 字符串结束
                return sb.toString();
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static long parseLong(String str, long defaultValue) {
        if (str == null || str.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String truncate(String str, int maxLen) {
        if (str == null || str.length() <= maxLen) {
            return str;
        }
        return str.substring(0, maxLen) + "...";
    }

    /**
     * 将汇总邮件内容保存到文件
     */
    private static void writeFile(String filePath, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }

    public static void main(String[] args) {
        try {
            // 解析参数：app_id, app_secret, 文档URL列表（逗号分隔）
            String appId = args.length >= 1 ? args[0] : System.getenv("FEISHU_APP_ID");
            String appSecret = args.length >= 2 ? args[1] : System.getenv("FEISHU_APP_SECRET");

            // 文档列表
            List<String> documentIds = new ArrayList<>();
            if (args.length >= 3) {
                String[] ids = args[2].split(",");
                for (String id : ids) {
                    String extracted = extractDocumentId(id.trim());
                    if (!extracted.isEmpty()) {
                        documentIds.add(extracted);
                    }
                }
            }

            // 凭证或文档缺失时进入演示模式（不实际调用飞书API）
            if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
                System.err.println("⚠️ 未提供app_id/app_secret，进入演示模式（不调用飞书API）");
                System.err.println("正式使用: java FeishuDocCollector <app_id> <app_secret> \"文档URL1,文档URL2,...\"");
                demoMode();
                return;
            }

            if (documentIds.isEmpty()) {
                System.err.println("⚠️ 未提供文档列表，进入演示模式（不调用飞书API）");
                demoMode();
                return;
            }

            FeishuDocCollector collector = new FeishuDocCollector(appId, appSecret);
            String token = collector.getTenantAccessToken();
            List<Map<String, String>> docs = collector.collectDocuments(token, documentIds);

            if (docs.isEmpty()) {
                System.err.println("❌ 未成功采集到任何文档");
                return;
            }

            String emailContent = collector.buildEmailContent(docs, "接口文档汇总");
            String outputFile = "feishu_docs_summary.html";
            writeFile(outputFile, emailContent);
            System.out.println("✅ 已生成邮件汇总文件: " + outputFile);
            System.out.println("   " + docs.size() + " 篇文档，共 "
                    + docs.stream().mapToInt(d -> d.get("content").length()).sum() + " 字符");

        } catch (Exception e) {
            System.err.println("❌ 程序执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 演示模式：模拟采集2篇文档，验证邮件汇总格式
     */
    private static void demoMode() {
        try {
            List<Map<String, String>> docs = new ArrayList<>();
            Map<String, String> doc1 = new HashMap<>();
            doc1.put("id", "DEMO_001");
            doc1.put("title", "接口联调规范");
            doc1.put("content", "一、接口约定\n1. 所有接口统一使用POST请求\n2. 请求体使用JSON格式\n3. 统一返回结构: {code,msg,data}\n\n二、错误码约定\n1. 200-成功 2. 400-参数错误 3. 500-服务异常");
            docs.add(doc1);

            Map<String, String> doc2 = new HashMap<>();
            doc2.put("id", "DEMO_002");
            doc2.put("title", "性能测试方案");
            doc2.put("content", "一、测试目标\n1. 单接口并发500用户\n2. 平均响应时间<500ms\n3. 错误率<0.1%\n\n二、测试步骤\n1. 使用LoadRunner录制脚本\n2. 配置参数化数据\n3. 阶梯加压验证");
            docs.add(doc2);

            FeishuDocCollector collector = new FeishuDocCollector("demo_app_id", "demo_app_secret");
            String emailContent = collector.buildEmailContent(docs, "演示模式");
            String outputFile = "feishu_docs_summary_demo.html";
            writeFile(outputFile, emailContent);
            System.out.println("✅ [演示模式] 已生成邮件汇总: " + outputFile);
            System.out.println("（正式使用请传入 app_id / app_secret / 文档URL）");
        } catch (Exception e) {
            System.err.println("❌ 演示模式失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}