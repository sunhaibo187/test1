import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口组装工具类测试类
 */
public class InterfaceAssemblerTest {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        System.out.println("========== 开始测试接口组装工具类 ==========\n");

        boolean allTestsPassed = true;

        // 测试1：验证单个接口信息的创建
        allTestsPassed &= testInterfaceInfoCreation();

        // 测试2：验证curl命令生成
        allTestsPassed &= testCurlCommandGeneration();

        // 测试3：验证接口模板生成
        allTestsPassed &= testTemplateGeneration();

        // 测试4：验证所有接口的组装
        allTestsPassed &= testAllInterfacesAssembly();

        System.out.println("\n========== 测试结果 ==========");
        if (allTestsPassed) {
            System.out.println("✅ 所有测试通过！");
        } else {
            System.out.println("❌ 部分测试失败，请检查！");
        }
    }

    /**
     * 测试1：验证单个接口信息的创建
     */
    private static boolean testInterfaceInfoCreation() {
        System.out.println("【测试1】验证单个接口信息的创建");
        
        try {
            InterfaceAssembler.InterfaceInfo iface = new InterfaceAssembler.InterfaceInfo(
                "测试接口",
                "/test/path"
            );
            
            iface.addRequestBody("{\"key\":\"value\"}");
            iface.addRequestBody("{\"key2\":\"value2\"}");
            
            boolean result = "测试接口".equals(iface.getName())
                && "/test/path".equals(iface.getPath())
                && iface.getRequestBodies().size() == 2;
            
            System.out.println(result ? "✅ 接口信息创建正常" : "❌ 接口信息创建失败");
            return result;
        } catch (Exception e) {
            System.out.println("❌ 接口信息创建异常：" + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 测试2：验证curl命令生成
     */
    private static boolean testCurlCommandGeneration() {
        System.out.println("\n【测试2】验证curl命令生成");
        
        try {
            InterfaceAssembler.InterfaceInfo iface = new InterfaceAssembler.InterfaceInfo(
                "全球通客户等级查询接口",
                "/order/IGoToneCustLevelOpenService/qryOnecustlevel"
            );
            iface.addRequestBody("{\"SERIAL_NUMBER\":\"13574156868\"}");
            
            String curlCommand = InterfaceAssembler.generateCurlCommand(iface, 0);
            
            boolean result = curlCommand != null
                && curlCommand.contains("全球通客户等级查询接口")
                && curlCommand.contains("curl")
                && curlCommand.contains("SERIAL_NUMBER");
            
            System.out.println(result ? "✅ curl命令生成正常" : "❌ curl命令生成失败");
            if (!result && curlCommand != null) {
                System.out.println("生成的curl命令：" + curlCommand);
            }
            return result;
        } catch (Exception e) {
            System.out.println("❌ curl命令生成异常：" + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 测试3：验证接口模板生成
     */
    private static boolean testTemplateGeneration() {
        System.out.println("\n【测试3】验证接口模板生成");
        
        try {
            InterfaceAssembler.InterfaceInfo iface = new InterfaceAssembler.InterfaceInfo(
                "用户基本信息查询",
                "/order/IQueryInfosOpenService/getUserInfo"
            );
            iface.addRequestBody("{\"X_GETMODE\":\"0\",\"SERIAL_NUMBER\":\"15874202674\"}");
            
            String template = InterfaceAssembler.generateInterfaceTemplate(iface);
            
            boolean result = template != null
                && template.contains("用户基本信息查询")
                && template.contains("X_GETMODE")
                && template.contains("SERIAL_NUMBER");
            
            System.out.println(result ? "✅ 接口模板生成正常" : "❌ 接口模板生成失败");
            if (!result && template != null) {
                System.out.println("生成的模板：" + template);
            }
            return result;
        } catch (Exception e) {
            System.out.println("❌ 接口模板生成异常：" + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 测试4：验证所有接口的组装
     */
    private static boolean testAllInterfacesAssembly() {
        System.out.println("\n【测试4】验证所有接口的组装");
        
        try {
            List<InterfaceAssembler.InterfaceInfo> interfaces = new ArrayList<>();
            
            // 添加几个关键接口
            InterfaceAssembler.InterfaceInfo if1 = new InterfaceAssembler.InterfaceInfo(
                "全球通客户等级查询接口",
                "/order/IGoToneCustLevelOpenService/qryOnecustlevel"
            );
            if1.addRequestBody("{\"SERIAL_NUMBER\":\"13574156868\"}");
            interfaces.add(if1);
            
            InterfaceAssembler.InterfaceInfo if2 = new InterfaceAssembler.InterfaceInfo(
                "用户基本信息查询",
                "/order/IQueryInfosOpenService/getUserInfo"
            );
            if2.addRequestBody("{\"X_GETMODE\":\"0\",\"SERIAL_NUMBER\":\"15874202674\"}");
            if2.addRequestBody("{\"X_GETMODE\":\"1\",\"USER_ID\":\"3111012426918715\"}");
            interfaces.add(if2);
            
            String allCurlCommands = InterfaceAssembler.generateAllCurlCommands(interfaces);
            String allTemplates = InterfaceAssembler.generateAllTemplates(interfaces);
            
            boolean result = allCurlCommands != null
                && allTemplates != null
                && interfaces.size() == 2
                && allCurlCommands.contains("全球通客户等级查询接口")
                && allCurlCommands.contains("用户基本信息查询")
                && allTemplates.contains("全球通客户等级查询接口")
                && allTemplates.contains("用户基本信息查询");
            
            System.out.println(result ? "✅ 所有接口组装正常" : "❌ 所有接口组装失败");
            System.out.println("   接口数量：" + interfaces.size());
            return result;
        } catch (Exception e) {
            System.out.println("❌ 所有接口组装异常：" + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}