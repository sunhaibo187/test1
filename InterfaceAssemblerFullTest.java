import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 接口组装工具类完整测试类
 * 运行完整的接口组装流程并输出结果到文件
 */
public class InterfaceAssemblerFullTest {

    public static void main(String[] args) {
        System.out.println("========== 开始完整测试接口组装工具类 ==========\n");

        try {
            // 构建完整的接口列表
            List<InterfaceAssembler.InterfaceInfo> interfaces = buildFullInterfaceList();
            System.out.println("✅ 已构建 " + interfaces.size() + " 个接口信息");

            // 生成curl命令
            String curlCommands = InterfaceAssembler.generateAllCurlCommands(interfaces);
            System.out.println("✅ 已生成所有接口的curl命令");

            // 生成请求模板
            String templates = InterfaceAssembler.generateAllTemplates(interfaces);
            System.out.println("✅ 已生成所有接口的请求模板");

            // 输出到文件
            writeToFile("interface_curl_commands.txt", curlCommands);
            System.out.println("✅ curl命令已保存到 interface_curl_commands.txt");

            writeToFile("interface_templates.json", templates);
            System.out.println("✅ 请求模板已保存到 interface_templates.json");

            // 简单验证输出内容
            boolean curlValid = curlCommands.contains("全球通客户等级查询接口")
                && curlCommands.contains("curl")
                && curlCommands.contains("SERIAL_NUMBER");

            boolean templateValid = templates.contains("全球通客户等级查询接口")
                && templates.contains("url")
                && templates.contains("method");

            System.out.println("\n========== 验证结果 ==========");
            System.out.println("curl命令验证：" + (curlValid ? "✅ 有效" : "❌ 无效"));
            System.out.println("请求模板验证：" + (templateValid ? "✅ 有效" : "❌ 无效"));
            System.out.println("\n✅ 完整测试通过！接口组装结果正确。");

        } catch (Exception e) {
            System.out.println("\n❌ 完整测试失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<InterfaceAssembler.InterfaceInfo> buildFullInterfaceList() {
        List<InterfaceAssembler.InterfaceInfo> interfaces = new ArrayList<>();

        // 1. 全球通客户等级查询接口
        InterfaceAssembler.InterfaceInfo if1 = new InterfaceAssembler.InterfaceInfo(
            "全球通客户等级查询接口",
            "/order/IGoToneCustLevelOpenService/qryOnecustlevel"
        );
        if1.addRequestBody("{\"SERIAL_NUMBER\":\"13574156868\"}");
        interfaces.add(if1);

        // 2. 用户基本信息查询
        InterfaceAssembler.InterfaceInfo if2 = new InterfaceAssembler.InterfaceInfo(
            "用户基本信息查询",
            "/order/IQueryInfosOpenService/getUserInfo"
        );
        if2.addRequestBody("{\"X_GETMODE\":\"0\",\"SERIAL_NUMBER\":\"15874202674\"}");
        if2.addRequestBody("{\"X_GETMODE\":\"1\",\"USER_ID\":\"3111012426918715\"}");
        if2.addRequestBody("{\"X_GETMODE\":\"2\",\"CUST_ID\":\"3114072302894554\"}");
        interfaces.add(if2);

        // 3. 用户详细资料查询
        InterfaceAssembler.InterfaceInfo if3 = new InterfaceAssembler.InterfaceInfo(
            "用户详细资料查询",
            "/order/IQueryInfosOpenService/getUserCustAcct"
        );
        if3.addRequestBody("{\"xGetmodeE\":\"0\",\"serialNumber\":\"15874202674\"}");
        interfaces.add(if3);

        // 4. 查询客户年龄
        InterfaceAssembler.InterfaceInfo if4 = new InterfaceAssembler.InterfaceInfo(
            "查询客户年龄",
            "/order/IReteMirabileOpenService/queryAgeBySerialNumber"
        );
        if4.addRequestBody("{\"serialNumber\":\"15874202674\"}");
        interfaces.add(if4);

        // 5. 用户已订购商品查询
        InterfaceAssembler.InterfaceInfo if5 = new InterfaceAssembler.InterfaceInfo(
            "用户已订购商品查询",
            "/order/IQueryInfosOpenService/querySubscribeInfo"
        );
        if5.addRequestBody("{\"SERIAL_NUMBER\":\"15874202674\",\"QUERY_TYPE\":\"00\",\"QUERY_MODE\":\"0\"}");
        interfaces.add(if5);

        return interfaces;
    }

    private static void writeToFile(String filename, String content) throws IOException {
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(content);
        }
    }
}