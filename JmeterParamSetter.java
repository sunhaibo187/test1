import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JMeter参数设置工具类
 * 用于批量设置和配置JMeter接口测试参数
 */
public class JmeterParamSetter {

    /**
     * 接口参数配置
     */
    public static class ApiConfig {
        private String apiName;
        private String method;
        private String url;
        private Map<String, String> params;
        private Map<String, String> headers;
        private String body;
        private String expectedResponse;

        public ApiConfig(String apiName, String method, String url) {
            this.apiName = apiName;
            this.method = method;
            this.url = url;
            this.params = new HashMap<>();
            this.headers = new HashMap<>();
        }

        public String getApiName() {
            return apiName;
        }

        public void setApiName(String apiName) {
            this.apiName = apiName;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Map<String, String> getParams() {
            return params;
        }

        public void setParams(Map<String, String> params) {
            this.params = params;
        }

        public void addParam(String key, String value) {
            this.params.put(key, value);
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public void addHeader(String key, String value) {
            this.headers.put(key, value);
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getExpectedResponse() {
            return expectedResponse;
        }

        public void setExpectedResponse(String expectedResponse) {
            this.expectedResponse = expectedResponse;
        }

        @Override
        public String toString() {
            return "【" + apiName + "】 " + method.toUpperCase() + " " + url;
        }
    }

    /**
     * 批量设置JMeter接口参数并生成测试文档
     *
     * @param apiConfigs 接口配置列表
     * @return 批量测试计划文档
     */
    public static String batchSetParams(List<ApiConfig> apiConfigs) {
        if (apiConfigs == null || apiConfigs.isEmpty()) {
            throw new IllegalArgumentException("apiConfigs不能为空");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("============================================================\n");
        sb.append("JMeter批量测试计划 (共").append(apiConfigs.size()).append("个接口)\n");
        sb.append("============================================================\n\n");
        sb.append("JMeter批量配置步骤:\n");
        sb.append("1) 新建测试计划，添加线程组\n");
        sb.append("2) 在线程组下依次添加HTTP请求取样器，按以下顺序配置:\n\n");

        for (int i = 0; i < apiConfigs.size(); i++) {
            ApiConfig config = apiConfigs.get(i);
            sb.append("============================================================\n");
            sb.append("  [").append(i + 1).append("] ").append(config.toString()).append("\n");
            sb.append("============================================================\n");
            sb.append(generateSingleApiDoc(config));
            sb.append("\n");
        }

        sb.append("\n3) 添加查看结果树监听器\n");
        sb.append("4) 添加聚合报告监听器\n");
        sb.append("5) 配置线程数、循环次数、Ramp-Up时间等运行参数\n");
        sb.append("============================================================\n");

        return sb.toString();
    }

    /**
     * 生成单个接口的JMeter测试配置文档
     */
    private static String generateSingleApiDoc(ApiConfig config) {
        StringBuilder doc = new StringBuilder();
        doc.append("1. 接口基本信息\n");
        doc.append("   - 接口名称: ").append(config.getApiName()).append("\n");
        doc.append("   - 请求方法: ").append(config.getMethod().toUpperCase()).append("\n");
        doc.append("   - 请求URL: ").append(config.getUrl()).append("\n");

        Map<String, String> params = config.getParams();
        if (params != null && !params.isEmpty()) {
            doc.append("\n2. URL查询参数\n");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                doc.append("   - ").append(entry.getKey()).append(": ").append(entry.getValue());
                doc.append(" (描述: 请填写参数说明)\n");
            }
        }

        Map<String, String> headers = config.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            doc.append("\n3. 请求头信息\n");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                doc.append("   - ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        String body = config.getBody();
        if (body != null && !body.isEmpty()) {
            doc.append("\n4. 请求体内容\n");
            doc.append("   内容:\n").append(body).append("\n");
        }

        doc.append("\n5. JMeter配置步骤\n");
        doc.append("   1) 添加HTTP请求取样器\n");
        doc.append("   2) 设置服务器名称/IP和端口号\n");
        doc.append("   3) 设置HTTP请求方法为 ").append(config.getMethod().toUpperCase()).append("\n");
        doc.append("   4) 设置请求路径\n");
        doc.append("   5) 根据上述参数/请求头/请求体进行配置\n");

        String expectedResponse = config.getExpectedResponse();
        if (expectedResponse != null && !expectedResponse.isEmpty()) {
            doc.append("\n6. 预期结果验证\n");
            doc.append("   - 响应状态码: 200\n");
            doc.append("   - 响应内容: ").append(expectedResponse).append("\n");
        }

        return doc.toString();
    }

    public static void main(String[] args) {
        // 演示：批量设置JMeter接口参数
        ApiConfig loginApi = new ApiConfig("登录接口", "POST", "https://api.example.com/login");
        loginApi.setBody("{\"username\":\"admin\",\"password\":\"123456\"}");
        loginApi.setExpectedResponse("返回token");

        ApiConfig userListApi = new ApiConfig("用户列表", "GET", "https://api.example.com/users");
        userListApi.addParam("page", "1");
        userListApi.addParam("size", "10");
        userListApi.setExpectedResponse("返回用户列表数组");

        ApiConfig createUserApi = new ApiConfig("创建用户", "POST", "https://api.example.com/users");
        createUserApi.addHeader("Content-Type", "application/json");
        createUserApi.setBody("{\"name\":\"张三\",\"email\":\"zhangsan@example.com\"}");
        createUserApi.setExpectedResponse("返回创建的用户信息");

        List<ApiConfig> configs = List.of(loginApi, userListApi, createUserApi);
        String result = batchSetParams(configs);
        System.out.println(result);
    }
}