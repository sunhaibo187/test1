import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * LoadRunner自动参数设置程序
 * 从CSV接口配置文件中读取接口信息，自动生成：
 *   1. LoadRunner Action.c 脚本（自动参数化 + 事务 + 思考时间）
 *   2. 参数数据文件（.dat，用于LR参数表导入）
 *   3. 参数化设置说明文档
 *
 * CSV格式（与ExcelInterfaceAssembler一致）：
 *   接口名称,接口编码,接口路径,请求方法,参数1|类型|描述|默认值,参数2|类型|描述|默认值,...
 *
 * 使用方式：
 *   java LoadRunnerParamGenerator <接口配置.csv> [输出目录]
 */
public class LoadRunnerParamGenerator {

    /** 服务地址前缀（可按实际环境修改） */
    private static final String BASE_URL = "http://your-server:port";

    /** 接口配置模型 */
    public static class ApiConfig {
        private String name;        // 接口名称
        private String code;        // 接口编码
        private String path;        // 接口路径
        private String method;      // 请求方法
        private List<ParamInfo> params; // 参数列表

        public ApiConfig() {
            this.method = "POST";
            this.params = new ArrayList<>();
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public List<ParamInfo> getParams() { return params; }
        public void addParam(ParamInfo param) { this.params.add(param); }
    }

    /** 参数模型 */
    public static class ParamInfo {
        private String name;     // 参数名称
        private String type;     // 参数类型
        private String desc;     // 参数描述
        private String value;    // 默认值

        public ParamInfo(String name, String type, String desc, String value) {
            this.name = name;
            this.type = type;
            this.desc = desc;
            this.value = value;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getDesc() { return desc; }
        public String getValue() { return value; }
    }

    /**
     * 从CSV文件读取接口配置
     */
    public static List<ApiConfig> readFromCsv(String csvFilePath) throws IOException {
        List<ApiConfig> apis = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(csvFilePath), StandardCharsets.UTF_8))) {
            reader.readLine(); // 跳过标题行
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 3) continue;

                ApiConfig api = new ApiConfig();
                api.setName(parts[0].trim());
                api.setCode(parts[1].trim());
                api.setPath(parts[2].trim());
                if (parts.length >= 4) api.setMethod(parts[3].trim().toUpperCase());

                for (int i = 4; i < parts.length; i++) {
                    String[] p = parts[i].split("\\|");
                    if (p.length >= 1) {
                        ParamInfo param = new ParamInfo(
                                p[0].trim(),
                                p.length >= 2 ? p[1].trim() : "String",
                                p.length >= 3 ? p[2].trim() : "",
                                p.length >= 4 ? p[3].trim() : "");
                        api.addParam(param);
                    }
                }
                apis.add(api);
            }
        }
        return apis;
    }

    /**
     * 生成单个接口的LoadRunner Action.c脚本
     */
    public static String generateActionScript(ApiConfig api) {
        StringBuilder sb = new StringBuilder();
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        // 文件头注释
        sb.append("/********************************************************************\n");
        sb.append(" * 接口名称: ").append(api.getName()).append("\n");
        sb.append(" * 接口编码: ").append(api.getCode()).append("\n");
        sb.append(" * 接口路径: ").append(api.getPath()).append("\n");
        sb.append(" * 请求方法: ").append(api.getMethod()).append("\n");
        sb.append(" * 生成时间: ").append(ts).append("\n");
        sb.append(" * 生成工具: LoadRunnerParamGenerator (自动参数化)\n");
        sb.append(" ********************************************************************/\n\n");

        // 引入头文件
        sb.append("#include \"web_api.h\"\n");
        sb.append("#include \"lrun.h\"\n\n");

        // 事务名（用接口编码做事务名，需符合LR命名规则）
        String transactionName = sanitizeTransactionName(api.getCode());

        // Action函数
        sb.append("Action()\n");
        sb.append("{\n");
        sb.append("    /* ============================================================\n");
        sb.append("     * [自动参数化] 方式一：从LR参数表读取（推荐，性能更好）\n");
        sb.append("     * 需在LR中创建参数表并导入 *.dat 数据文件，参数名与CSV列名一致\n");
        sb.append("     * ============================================================ */\n");
        for (ParamInfo param : api.getParams()) {
            String pn = param.getName().toUpperCase();
            sb.append("    // lr_save_string(lr_eval_string(\"{").append(pn).append("}\"), \"")
              .append(pn).append("\");  // 从参数表取值\n");
        }
        sb.append("\n");
        sb.append("    /* ============================================================\n");
        sb.append("     * [自动参数化] 方式二：脚本内直接赋值（适合单用户调试）\n");
        sb.append("     * ============================================================ */\n");
        for (ParamInfo param : api.getParams()) {
            String pn = param.getName().toUpperCase();
            String val = param.getValue() != null && !param.getValue().isEmpty() ? param.getValue() : "''";
            sb.append("    lr_save_string(\"").append(val).append("\", \"").append(pn).append("\");\n");
        }
        sb.append("\n");

        // 开始事务
        sb.append("    lr_start_transaction(\"").append(transactionName).append("\");\n\n");

        // 构造请求URL
        String url = BASE_URL + api.getPath();

        // 构造请求体
        String body = buildRequestBody(api);
        String queryString = buildQueryString(api);

        // web_custom_request
        sb.append("    web_custom_request(\"").append(api.getName()).append("\",\n");
        sb.append("        \"URL=").append(url);
        if (!queryString.isEmpty()) {
            sb.append("?").append(queryString);
        }
        sb.append("\",\n");
        sb.append("        \"Method=").append(api.getMethod()).append("\",\n");
        sb.append("        \"EncType=application/json\",\n");
        sb.append("        \"RecContentType=application/json\",\n");
        sb.append("        \"Body=").append(escapeCString(body)).append("\",\n");
        sb.append("        \"Snapshot=t1.inf\",\n");
        sb.append("        LAST);\n\n");

        // 事务结束
        sb.append("    lr_end_transaction(\"").append(transactionName).append("\", LR_AUTO);\n\n");

        // 思考时间
        sb.append("    /* 模拟真实用户思考时间（单位:秒），压测时建议注释掉 */\n");
        sb.append("    lr_think_time(2);\n\n");

        sb.append("    return 0;\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 构造JSON请求体（参数使用{参数名}占位符引用）
     */
    private static String buildRequestBody(ApiConfig api) {
        if (api.getParams().isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < api.getParams().size(); i++) {
            ParamInfo param = api.getParams().get(i);
            sb.append("\"").append(param.getName()).append("\":");
            // 数字类型不加引号，其余加引号
            String type = param.getType() != null ? param.getType().toLowerCase() : "";
            if (type.contains("int") || type.contains("long") || type.contains("double") || type.contains("number")) {
                sb.append("{").append(param.getName().toUpperCase()).append("}");
            } else {
                sb.append("\"{").append(param.getName().toUpperCase()).append("}\"");
            }
            if (i < api.getParams().size() - 1) {
                sb.append(",");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 构造URL查询参数串（GET请求时使用）
     */
    private static String buildQueryString(ApiConfig api) {
        if (!"GET".equalsIgnoreCase(api.getMethod()) || api.getParams().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < api.getParams().size(); i++) {
            ParamInfo param = api.getParams().get(i);
            sb.append(param.getName()).append("={").append(param.getName().toUpperCase()).append("}");
            if (i < api.getParams().size() - 1) {
                sb.append("&");
            }
        }
        return sb.toString();
    }

    /**
     * 生成参数数据文件（.dat），供LR参数表导入
     * 格式：首行为参数名，后续每行为一组测试数据
     */
    public static String generateParamDataFile(List<ApiConfig> apis) {
        StringBuilder sb = new StringBuilder();
        sb.append("# LoadRunner参数数据文件\n");
        sb.append("# 使用方法：LR -> Vuser -> Parameter Properties -> New -> File，选择本文件\n");
        sb.append("# 每行一列参数值，用逗号分隔多参数\n\n");

        // 统计所有参数（去重）
        List<String> allParams = new ArrayList<>();
        for (ApiConfig api : apis) {
            for (ParamInfo param : api.getParams()) {
                String pn = param.getName().toUpperCase();
                if (!allParams.contains(pn)) {
                    allParams.add(pn);
                }
            }
        }

        if (allParams.isEmpty()) {
            sb.append("# 无参数\n");
            return sb.toString();
        }

        // 写入参数名表头
        sb.append(String.join(",", allParams)).append("\n");

        // 写入默认值作为第一组数据
        List<String> firstRow = new ArrayList<>();
        for (String pn : allParams) {
            String val = findParamValue(apis, pn);
            firstRow.add(val);
        }
        sb.append(String.join(",", firstRow)).append("\n");

        // 写入扩展测试数据（示例）
        sb.append("13808427239,0,15874202674,13574156868,3111012426918715,3114072302894554\n");

        return sb.toString();
    }

    /**
     * 根据参数名查找默认值
     */
    private static String findParamValue(List<ApiConfig> apis, String paramName) {
        for (ApiConfig api : apis) {
            for (ParamInfo param : api.getParams()) {
                if (param.getName().toUpperCase().equals(paramName)) {
                    return param.getValue() != null && !param.getValue().isEmpty() ? param.getValue() : "";
                }
            }
        }
        return "";
    }

    /**
     * 生成参数化设置说明文档
     */
    public static String generateParamGuide(List<ApiConfig> apis) {
        StringBuilder sb = new StringBuilder();
        sb.append("# LoadRunner自动参数化设置说明\n\n");
        sb.append("## 一、总体说明\n\n");
        sb.append("本程序已为以下 ").append(apis.size()).append(" 个接口自动生成LoadRunner脚本和参数文件。\n\n");

        sb.append("## 二、文件清单\n\n");
        sb.append("| 文件 | 说明 |\n");
        sb.append("|------|------|\n");
        sb.append("| Action_<接口编码>.c | 各接口的LoadRunner Action脚本（自动参数化） |\n");
        sb.append("| loadrunner_params.dat | 参数数据文件（LR参数表导入用） |\n");
        sb.append("| param_guide.md | 本说明文档 |\n\n");

        sb.append("## 三、接口与参数清单\n\n");
        for (ApiConfig api : apis) {
            sb.append("### ").append(api.getName()).append("\n\n");
            sb.append("- 接口编码: ").append(api.getCode()).append("\n");
            sb.append("- 请求方法: ").append(api.getMethod()).append("\n");
            sb.append("- 接口路径: ").append(api.getPath()).append("\n");
            sb.append("- 事务名: ").append(sanitizeTransactionName(api.getCode())).append("\n\n");
            if (!api.getParams().isEmpty()) {
                sb.append("| 参数名 | 类型 | 描述 | 默认值 |\n");
                sb.append("|--------|------|------|--------|\n");
                for (ParamInfo param : api.getParams()) {
                    sb.append("| ").append(param.getName()).append(" | ")
                      .append(param.getType()).append(" | ")
                      .append(param.getDesc()).append(" | ")
                      .append(param.getValue()).append(" |\n");
                }
                sb.append("\n");
            }
        }

        sb.append("## 四、LoadRunner参数化配置步骤\n\n");
        sb.append("1. 打开VuGen，将 Action_*.c 脚本内容替换到 Action 中；\n");
        sb.append("2. 点击菜单 Vuser -> Parameter Properties（或按快捷键 F7）；\n");
        sb.append("3. 点击 New，输入参数名（与脚本中 {参数名} 一致，如 SERIAL_NUMBER）；\n");
        sb.append("4. 选择参数类型为 File，点击 Properties... 选择 loadrunner_params.dat；\n");
        sb.append("5. 在 File column 中配置参数对应的列号；\n");
        sb.append("6. 重复 3-5 步为所有参数创建参数表；\n");
        sb.append("7. 在 Select next row 选择取值策略（Sequential/Random/Unique）；\n");
        sb.append("8. 在 Update value on 选择更新时机（Each iteration/Each occurrence）；\n");
        sb.append("9. 点击 Create Test 创建场景，运行脚本验证参数化效果。\n\n");

        sb.append("## 五、注意事项\n\n");
        sb.append("1. 脚本中方式二（直接赋值）默认启用，方便单用户调试；\n");
        sb.append("2. 正式压测时，请将方式二注释，取消方式一（从参数表读取）注释；\n");
        sb.append("3. 修改 BASE_URL 为实际服务地址；\n");
        sb.append("4. 如接口有鉴权，需在脚本开头添加登录获取Token的逻辑。\n");

        return sb.toString();
    }

    /**
     * 转义C语言字符串中的特殊字符
     */
    private static String escapeCString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 事务名合法化（LR事务名不允许特殊字符和空格）
     */
    private static String sanitizeTransactionName(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "Transaction_" + System.currentTimeMillis();
        }
        return code.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * 写入文件
     */
    private static void writeFile(String filePath, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.write(content);
        }
    }

    public static void main(String[] args) {
        try {
            String csvPath = args.length >= 1 ? args[0] : null;
            String outputDir = args.length >= 2 ? args[1] : "loadrunner_output";
            File dir = new File(outputDir);
            if (!dir.exists() && !dir.mkdirs()) {
                System.err.println("❌ 无法创建输出目录: " + outputDir);
                return;
            }

            List<ApiConfig> apis;
            if (csvPath != null) {
                apis = readFromCsv(csvPath);
                System.out.println("✅ 已从CSV读取 " + apis.size() + " 个接口: " + csvPath);
            } else {
                // 演示数据
                apis = buildDemoApis();
                System.out.println("✅ 已使用演示数据（" + apis.size() + " 个接口），可通过命令行参数传入CSV");
            }

            // 1. 为每个接口生成Action脚本
            for (ApiConfig api : apis) {
                String script = generateActionScript(api);
                String fileName = outputDir + File.separator + "Action_" + api.getCode() + ".c";
                writeFile(fileName, script);
                System.out.println("✅ 已生成脚本: " + fileName);
            }

            // 2. 生成参数数据文件
            String paramFile = outputDir + File.separator + "loadrunner_params.dat";
            writeFile(paramFile, generateParamDataFile(apis));
            System.out.println("✅ 已生成参数文件: " + paramFile);

            // 3. 生成参数化设置说明
            String guideFile = outputDir + File.separator + "param_guide.md";
            writeFile(guideFile, generateParamGuide(apis));
            System.out.println("✅ 已生成说明文档: " + guideFile);

            System.out.println("\n🎉 LoadRunner自动参数设置完成！输出目录: " + outputDir);

        } catch (Exception e) {
            System.err.println("❌ 生成失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 构建演示数据
     */
    private static List<ApiConfig> buildDemoApis() {
        List<ApiConfig> apis = new ArrayList<>();

        ApiConfig api1 = new ApiConfig();
        api1.setName("全球通客户等级查询接口");
        api1.setCode("QUERY_CUST_LEVEL");
        api1.setPath("/order/IGoToneCustLevelOpenService/qryOnecustlevel");
        api1.setMethod("POST");
        api1.addParam(new ParamInfo("SERIAL_NUMBER", "String", "手机号码", "13574156868"));
        apis.add(api1);

        ApiConfig api2 = new ApiConfig();
        api2.setName("用户基本信息查询");
        api2.setCode("QUERY_USER_INFO");
        api2.setPath("/order/IQueryInfosOpenService/getUserInfo");
        api2.setMethod("POST");
        api2.addParam(new ParamInfo("X_GETMODE", "String", "查询类型", "0"));
        api2.addParam(new ParamInfo("SERIAL_NUMBER", "String", "手机号码", "15874202674"));
        apis.add(api2);

        ApiConfig api3 = new ApiConfig();
        api3.setName("用户已订购商品查询");
        api3.setCode("QUERY_SUBSCRIBE_INFO");
        api3.setPath("/order/IQueryInfosOpenService/querySubscribeInfo");
        api3.setMethod("POST");
        api3.addParam(new ParamInfo("SERIAL_NUMBER", "String", "手机号码", "15874202674"));
        api3.addParam(new ParamInfo("QUERY_TYPE", "String", "查询类型", "00"));
        api3.addParam(new ParamInfo("QUERY_MODE", "String", "查询模式", "0"));
        apis.add(api3);

        return apis;
    }
}