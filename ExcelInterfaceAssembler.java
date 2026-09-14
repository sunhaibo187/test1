import java.util.*;
import java.io.*;

/**
 * Excel接口组装工具类
 * 从Excel文档读取接口名称、接口编码、接口参数并进行组装
 */
public class ExcelInterfaceAssembler {

    /**
     * 接口信息模型
     */
    public static class InterfaceInfo {
        private String interfaceName;  // 接口名称
        private String interfaceCode;  // 接口编码
        private String interfacePath;  // 接口路径
        private String method;         // 请求方法
        private List<ParamInfo> params; // 参数列表

        public InterfaceInfo() {
            this.params = new ArrayList<>();
            this.method = "POST";
        }

        // Getters and Setters
        public String getInterfaceName() { return interfaceName; }
        public void setInterfaceName(String interfaceName) { this.interfaceName = interfaceName; }
        public String getInterfaceCode() { return interfaceCode; }
        public void setInterfaceCode(String interfaceCode) { this.interfaceCode = interfaceCode; }
        public String getInterfacePath() { return interfacePath; }
        public void setInterfacePath(String interfacePath) { this.interfacePath = interfacePath; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public List<ParamInfo> getParams() { return params; }
        public void addParam(ParamInfo param) { this.params.add(param); }

        @Override
        public String toString() {
            return "接口名称: " + interfaceName + ", 接口编码: " + interfaceCode + 
                   ", 路径: " + interfacePath + ", 参数数量: " + params.size();
        }
    }

    /**
     * 参数信息模型
     */
    public static class ParamInfo {
        private String paramName;    // 参数名称
        private String paramType;    // 参数类型
        private String paramDesc;    // 参数描述
        private boolean required;    // 是否必填
        private String defaultValue; // 默认值

        public ParamInfo(String paramName, String paramType) {
            this.paramName = paramName;
            this.paramType = paramType;
            this.required = true;
        }

        // Getters and Setters
        public String getParamName() { return paramName; }
        public void setParamName(String paramName) { this.paramName = paramName; }
        public String getParamType() { return paramType; }
        public void setParamType(String paramType) { this.paramType = paramType; }
        public String getParamDesc() { return paramDesc; }
        public void setParamDesc(String paramDesc) { this.paramDesc = paramDesc; }
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
    }

    /**
     * 从CSV文件读取接口信息（简化版，不依赖第三方库）
     * CSV格式：接口名称,接口编码,接口路径,请求方法,参数名1|类型|描述|是否必填|默认值,参数名2|类型|描述|是否必填|默认值,...
     */
    public static List<InterfaceInfo> readFromCsv(String csvFilePath) throws IOException {
        List<InterfaceInfo> interfaces = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            // 跳过标题行
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split(",");
                if (parts.length < 3) continue;
                
                InterfaceInfo info = new InterfaceInfo();
                info.setInterfaceName(parts[0].trim());
                info.setInterfaceCode(parts[1].trim());
                info.setInterfacePath(parts[2].trim());
                if (parts.length >= 4) info.setMethod(parts[3].trim());
                
                // 解析参数
                for (int i = 4; i < parts.length; i++) {
                    String[] paramParts = parts[i].split("\\|");
                    if (paramParts.length >= 2) {
                        ParamInfo param = new ParamInfo(paramParts[0].trim(), paramParts[1].trim());
                        if (paramParts.length >= 3) param.setParamDesc(paramParts[2].trim());
                        if (paramParts.length >= 4) param.setRequired("是".equalsIgnoreCase(paramParts[3].trim()));
                        if (paramParts.length >= 5) param.setDefaultValue(paramParts[4].trim());
                        info.addParam(param);
                    }
                }
                interfaces.add(info);
            }
        }
        return interfaces;
    }

    /**
     * 生成接口文档（Markdown格式）
     */
    public static String generateMarkdownDoc(List<InterfaceInfo> interfaces) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 接口文档\n\n");
        sb.append("---\n\n");
        
        for (int i = 0; i < interfaces.size(); i++) {
            InterfaceInfo iface = interfaces.get(i);
            sb.append("## ").append(i + 1).append(". ").append(iface.getInterfaceName()).append("\n\n");
            sb.append("- **接口编码**: ").append(iface.getInterfaceCode()).append("\n");
            sb.append("- **请求方法**: ").append(iface.getMethod()).append("\n");
            sb.append("- **接口路径**: ").append(iface.getInterfacePath()).append("\n\n");
            
            if (!iface.getParams().isEmpty()) {
                sb.append("### 请求参数\n\n");
                sb.append("| 参数名 | 类型 | 必填 | 描述 | 默认值 |\n");
                sb.append("|--------|------|------|------|--------|\n");
                
                for (ParamInfo param : iface.getParams()) {
                    sb.append("| ").append(param.getParamName()).append(" | ");
                    sb.append(param.getParamType()).append(" | ");
                    sb.append(param.isRequired() ? "是" : "否").append(" | ");
                    sb.append(param.getParamDesc() != null ? param.getParamDesc() : "-").append(" | ");
                    sb.append(param.getDefaultValue() != null ? param.getDefaultValue() : "-").append(" |\n");
                }
                sb.append("\n");
            }
            
            // 生成请求示例
            sb.append("### 请求示例\n\n");
            sb.append("```json\n");
            sb.append(generateJsonExample(iface)).append("\n");
            sb.append("```\n\n");
            
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    /**
     * 生成JSON请求示例
     */
    public static String generateJsonExample(InterfaceInfo iface) {
        if (iface.getParams().isEmpty()) {
            return "{}";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (int i = 0; i < iface.getParams().size(); i++) {
            ParamInfo param = iface.getParams().get(i);
            sb.append("  \"").append(param.getParamName()).append("\": ");
            
            // 根据类型设置默认值
            if (param.getDefaultValue() != null) {
                sb.append("\"").append(param.getDefaultValue()).append("\"");
            } else {
                String type = param.getParamType().toLowerCase();
                if (type.contains("int") || type.contains("long")) {
                    sb.append("0");
                } else if (type.contains("boolean")) {
                    sb.append("true");
                } else if (type.contains("array") || type.contains("list")) {
                    sb.append("[]");
                } else {
                    sb.append("\"\"");
                }
            }
            
            if (i < iface.getParams().size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 生成curl命令集合
     */
    public static String generateCurlCommands(List<InterfaceInfo> interfaces) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 接口curl命令集合\n\n");
        
        for (InterfaceInfo iface : interfaces) {
            sb.append("## ").append(iface.getInterfaceName()).append("\n");
            sb.append("# ").append(iface.getInterfaceCode()).append("\n");
            sb.append("curl -X ").append(iface.getMethod()).append(" \"");
            sb.append(iface.getInterfacePath()).append("\" \\\n");
            sb.append("  -H \"Content-Type: application/json\" \\\n");
            if (!iface.getParams().isEmpty()) {
                sb.append("  -d '").append(generateJsonExample(iface)).append("'");
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 保存内容到文件
     */
    public static void saveToFile(String content, String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(content);
        }
    }

    public static void main(String[] args) {
        try {
            // 示例数据（实际使用时请替换为真实的CSV文件路径）
            String csvContent = "接口名称,接口编码,接口路径,请求方法,参数1|类型|描述|是否必填|默认值,参数2|类型|描述|是否必填|默认值\n" +
                    "全球通客户等级查询接口,QUERY_CUST_LEVEL,/order/IGoToneCustLevelOpenService/qryOnecustlevel,POST,SERIAL_NUMBER|String|手机号码|是|13574156868\n" +
                    "用户基本信息查询,QUERY_USER_INFO,/order/IQueryInfosOpenService/getUserInfo,POST,X_GETMODE|String|查询类型|是|0,SERIAL_NUMBER|String|手机号码|是|15874202674\n" +
                    "查询客户年龄,QUERY_CUST_AGE,/order/IReteMirabileOpenService/queryAgeBySerialNumber,POST,serialNumber|String|手机号码|是|15874202674";
            
            // 写入临时CSV文件
            String tempCsv = "temp_interfaces.csv";
            saveToFile(csvContent, tempCsv);
            System.out.println("✅ 临时CSV文件已创建: " + tempCsv);
            
            // 读取接口信息
            List<InterfaceInfo> interfaces = readFromCsv(tempCsv);
            System.out.println("✅ 已读取 " + interfaces.size() + " 个接口信息\n");
            
            // 生成Markdown文档
            String markdownDoc = generateMarkdownDoc(interfaces);
            saveToFile(markdownDoc, "interfaces_doc.md");
            System.out.println("✅ Markdown接口文档已生成: interfaces_doc.md");
            
            // 生成curl命令
            String curlCommands = generateCurlCommands(interfaces);
            saveToFile(curlCommands, "interfaces_curl.sh");
            System.out.println("✅ curl命令集合已生成: interfaces_curl.sh");
            
            // 打印接口信息
            System.out.println("\n========== 读取的接口信息 ==========");
            for (InterfaceInfo iface : interfaces) {
                System.out.println("✅ " + iface);
            }
            
            System.out.println("\n✅ 接口组装完成！");
            
        } catch (Exception e) {
            System.out.println("❌ 接口组装失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}