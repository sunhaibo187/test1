import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 接口组装工具类
 * 将接口信息整理成可调用的格式（curl命令、请求模板等）
 */
public class InterfaceAssembler {

    /**
     * 接口信息模型
     */
    public static class InterfaceInfo {
        private String name;
        private String path;
        private List<String> requestBodies;

        public InterfaceInfo(String name, String path) {
            this.name = name;
            this.path = path;
            this.requestBodies = new ArrayList<>();
        }

        public void addRequestBody(String body) {
            this.requestBodies.add(body);
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public List<String> getRequestBodies() {
            return requestBodies;
        }
    }

    private static final String BASE_URL = "http://your-server:port";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 生成单个接口的curl命令
     */
    public static String generateCurlCommand(InterfaceInfo interfaceInfo, int bodyIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 接口名称: ").append(interfaceInfo.getName()).append("\n");
        sb.append("# 服务地址: ").append(BASE_URL).append(interfaceInfo.getPath()).append("\n");
        
        if (bodyIndex >= 0 && bodyIndex < interfaceInfo.getRequestBodies().size()) {
            String body = interfaceInfo.getRequestBodies().get(bodyIndex);
            sb.append("curl -X POST '").append(BASE_URL).append(interfaceInfo.getPath()).append("' \\\n");
            sb.append("  -H 'Content-Type: application/json' \\\n");
            sb.append("  -d '").append(escapeSingleQuote(body)).append("'");
        } else {
            sb.append("curl -X POST '").append(BASE_URL).append(interfaceInfo.getPath()).append("' \\\n");
            sb.append("  -H 'Content-Type: application/json'");
        }
        
        return sb.toString();
    }

    /**
     * 生成所有接口的curl命令集合
     */
    public static String generateAllCurlCommands(List<InterfaceInfo> interfaces) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ========== 接口调用命令集合 ==========\n\n");
        
        for (InterfaceInfo iface : interfaces) {
            sb.append("## ").append(iface.getName()).append("\n");
            sb.append("URL: ").append(BASE_URL).append(iface.getPath()).append("\n\n");
            
            if (iface.getRequestBodies().isEmpty()) {
                sb.append(generateCurlCommand(iface, -1)).append("\n\n");
            } else {
                for (int i = 0; i < iface.getRequestBodies().size(); i++) {
                    sb.append("### 请求示例 ").append(i + 1).append("\n");
                    sb.append(generateCurlCommand(iface, i)).append("\n\n");
                }
            }
            sb.append("-----------------------------------\n\n");
        }
        
        return sb.toString();
    }

    /**
     * 生成接口请求模板（JSON格式）
     */
    public static String generateInterfaceTemplate(InterfaceInfo interfaceInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"interfaceName\": \"").append(interfaceInfo.getName()).append("\",\n");
        sb.append("  \"url\": \"").append(BASE_URL).append(interfaceInfo.getPath()).append("\",\n");
        sb.append("  \"method\": \"POST\",\n");
        sb.append("  \"contentType\": \"application/json\",\n");
        
        if (!interfaceInfo.getRequestBodies().isEmpty()) {
            sb.append("  \"examples\": [\n");
            for (int i = 0; i < interfaceInfo.getRequestBodies().size(); i++) {
                sb.append("    ").append(interfaceInfo.getRequestBodies().get(i));
                if (i < interfaceInfo.getRequestBodies().size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("  ]\n");
        } else {
            sb.append("  \"examples\": []\n");
        }
        
        sb.append("}");
        return sb.toString();
    }

    /**
     * 生成所有接口的请求模板集合
     */
    public static String generateAllTemplates(List<InterfaceInfo> interfaces) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < interfaces.size(); i++) {
            sb.append(generateInterfaceTemplate(interfaces.get(i)));
            if (i < interfaces.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]");
        return GSON.toJson(GSON.fromJson(sb.toString(), Object.class));
    }

    private static String escapeSingleQuote(String str) {
        return str.replace("'", "'\\''");
    }

    public static void main(String[] args) {
        List<InterfaceInfo> interfaces = new ArrayList<>();

        // 1. 全球通客户等级查询接口
        InterfaceInfo if1 = new InterfaceInfo("全球通客户等级查询接口", "/order/IGoToneCustLevelOpenService/qryOnecustlevel");
        if1.addRequestBody("{\"SERIAL_NUMBER\":\"13574156868\"}");
        interfaces.add(if1);

        // 2. 用户基本信息查询
        InterfaceInfo if2 = new InterfaceInfo("用户基本信息查询", "/order/IQueryInfosOpenService/getUserInfo");
        if2.addRequestBody("{\"X_GETMODE\":\"0\",\"SERIAL_NUMBER\":\"15874202674\"}");
        if2.addRequestBody("{\"X_GETMODE\":\"1\",\"USER_ID\":\"3111012426918715\"}");
        if2.addRequestBody("{\"X_GETMODE\":\"2\",\"CUST_ID\":\"3114072302894554\"}");
        interfaces.add(if2);

        // 3. 用户详细资料查询
        InterfaceInfo if3 = new InterfaceInfo("用户详细资料查询", "/order/IQueryInfosOpenService/getUserCustAcct");
        if3.addRequestBody("{\"xGetmodeE\":\"0\",\"serialNumber\":\"15874202674\"}");
        interfaces.add(if3);

        // 4. 查询客户年龄
        InterfaceInfo if4 = new InterfaceInfo("查询客户年龄", "/order/IReteMirabileOpenService/queryAgeBySerialNumber");
        if4.addRequestBody("{\"serialNumber\":\"15874202674\"}");
        interfaces.add(if4);

        // 5. 用户已订购商品查询
        InterfaceInfo if5 = new InterfaceInfo("用户已订购商品查询", "/order/IQueryInfosOpenService/querySubscribeInfo");
        if5.addRequestBody("{\"SERIAL_NUMBER\":\"15874202674\",\"QUERY_TYPE\":\"00\",\"QUERY_MODE\":\"0\"}");
        interfaces.add(if5);

        // 6. 套餐计划查询
        InterfaceInfo if6 = new InterfaceInfo("套餐计划查询", "/order/IQueryInfosOpenService/getUserAllDiscnt");
        if6.addRequestBody("{\"SERIAL_NUMBER\":\"15874202674\",\"X_GETMODE\":\"1\",\"REMOVE_TAG\":\"0\",\"ROUTE_EPARCHY_CODE\":\"0731\"}");
        interfaces.add(if6);

        // 7. 用户优惠查询
        InterfaceInfo if7 = new InterfaceInfo("用户优惠查询", "/order/IGetUser360ViewOpenService/queryUserDiscnt");
        if7.addRequestBody("{\"USER_ID\":\"3103011205210251\",\"SelectTag\":\"1\"}");
        interfaces.add(if7);

        // 8. 实名制查询
        InterfaceInfo if8 = new InterfaceInfo("实名制查询", "/order/IRealNameCheckOpenService/checkCustInfoIntf");
        if8.addRequestBody("{\"serialNumber\":\"15874202674\"}");
        interfaces.add(if8);

        // 9. 用户主体服务状态查询
        InterfaceInfo if9 = new InterfaceInfo("用户主体服务状态查询", "/order/IQueryInfosOpenService/getUserStates");
        interfaces.add(if9);

        // 10. 全球通标签查询
        InterfaceInfo if10 = new InterfaceInfo("全球通标签查询", "/order/IAbilityPlatOpenService/qryGsmTag");
        if10.addRequestBody("{\"SERVICE_TYPE\":\"01\",\"SERIAL_NUMBER\":\"15873155945\"}");
        interfaces.add(if10);

        // 11. 宽带多账号手机查询
        InterfaceInfo if11 = new InterfaceInfo("宽带多账号手机查询", "/order/IBroadBandQueryOpenService/qryMultiBD");
        if11.addRequestBody("{\"SERIAL_NUMBER\":\"13808427239\"}");
        interfaces.add(if11);

        // 12. 芒果访问boss系统检查用户权益领取情况
        InterfaceInfo if12 = new InterfaceInfo("芒果访问boss系统检查用户权益领取情况", "/order/IQueryUserDiscntOpenService/checkDiscntAndCallAbility");
        if12.addRequestBody("{\"SERIAL_NUMBER\":\"13808427239\"}");
        interfaces.add(if12);

        // 13. 宽带信息的查询接口
        InterfaceInfo if13 = new InterfaceInfo("宽带信息的查询接口", "/order/IBroadBandIntfAppOpenService/broadbandDetailInfoQry");
        if13.addRequestBody("{\"ACCESS_ACCT\":\"731X13672315\",\"SERIAL_NUMBER\":\"13808427239\"}");
        interfaces.add(if13);

        // 14. 业务历史查询
        InterfaceInfo if14 = new InterfaceInfo("业务历史查询", "/order/ITradeInfoOpenService/queryTradeInfo");
        if14.addRequestBody("{\"carryTag\":\"0\",\"startTime\":\"20250905000000\",\"endTime\":\"20250930000000\",\"userMobile\":\"13017192268\"}");
        interfaces.add(if14);

        // 15. 头中低客户标识查询接口
        InterfaceInfo if15 = new InterfaceInfo("头中低客户标识查询接口", "/order/IHeadMiddleDownCustInfoOpenService/gotoneHeadMiddleDownTag");
        if15.addRequestBody("{\"serialNumber\": \"13808427239\"}");
        interfaces.add(if15);

        // 16. 判断用户是否有权限受理接口
        InterfaceInfo if16 = new InterfaceInfo("判断用户是否有权限受理接口", "/order/IBroadbandIntfOpenService/hasPriv4BdGivePhoneTicketA");
        if16.addRequestBody("{\"EPARCHY_CODE\":\"0731\",\"SERIAL_NUMBER\":\"13707310007\"}");
        interfaces.add(if16);

        // 17. 特定产品订购关系查询
        InterfaceInfo if17 = new InterfaceInfo("特定产品订购关系查询", "/order/IAbilityPlatOpenService/querySpecGoodsOrder");
        if17.addRequestBody("{\"GOODS_TYPE\":\"2\",\"SERIAL_NUMBER\":\"13707310007\",\"GOODS_ID_LIST\":\"2021999900054730\"}");
        interfaces.add(if17);

        // 18. 集团关键人查询接口
        InterfaceInfo if18 = new InterfaceInfo("集团关键人查询接口", "/order/IQueryCustAndComOpenService/queryCustGroupBySn");
        if18.addRequestBody("{\"SERIAL_NUMBER\":\"15874179050\"}");
        interfaces.add(if18);

        // 19. 完美家庭客户查询接口
        InterfaceInfo if19 = new InterfaceInfo("完美家庭客户查询接口", "/order/IQueryCustAndComOpenService/queryCustFamBySn");
        if19.addRequestBody("{\"SERIAL_NUMBER\":\"15874179051\"}");
        interfaces.add(if19);

        // 20. 业务办理资格校验
        InterfaceInfo if20 = new InterfaceInfo("业务办理资格校验", "/order/IAbilityPlatInfoSyncOpenService/ctrmCrmToAction");
        if20.addRequestBody("{\"ACTIVITYCODE\":\"T3000505\",\"BIPCODE\":\"BIP3B505\",\"BIZ_NAME\":\"业务办理资格校验\",\"CHECKTYPE\":\"1\",\"CUSTOMER_NAME\":\"张模糊化\",\"GOODSID\":\"2025999500029903\",\"ID_CARD_NUM\":\"433116100218517935\",\"ID_CARD_TYPE\":\"0\",\"MOBILENO\":\"15116176268\",\"NUMTYPE\":\"1\",\"PRODUCT_LIST\":[{\"CHECKTYPE\":\"1\",\"PRODUCT_TYPE\":\"03002\"}],\"SERIAL_NUMBER\":\"15116176268\"}");
        interfaces.add(if20);

        // 21. 商品与组关系查询接口
        InterfaceInfo if21 = new InterfaceInfo("商品与组关系查询接口", "/upc/IQueryGroupOpenService/queryOfferGroupByOfferId");
        if21.addRequestBody("{\"offerId\": \"110099922265\",\"mgmtDistrict\":\"ZZZZ\"}");
        interfaces.add(if21);

        // 22. 根据组编码查询商品
        InterfaceInfo if22 = new InterfaceInfo("根据组编码查询商品", "/upc/IQueryGroupComRelOpenService/queryGroupComRelOfferByGroupId");
        if22.addRequestBody("{\"groupId\":99001806,\"mgmtDistrict\":\"0731\"}");
        interfaces.add(if22);

        // 23. 根据商品ID查询商品
        InterfaceInfo if23 = new InterfaceInfo("根据商品ID查询商品", "/upc/IQueryOfferOpenService/getSimpleOfferByOfferId");
        if23.addRequestBody("{\"offerId\": 110085011412}");
        interfaces.add(if23);

        // 24. 查询结构属性信息
        InterfaceInfo if24 = new InterfaceInfo("查询结构属性信息", "/upc/IQueryOfferComChaOpenService/queryOfferComChaByOfferId");
        if24.addRequestBody("{\"offerId\":\"130035006006\"}");
        interfaces.add(if24);

        // 25. 查询成员产品
        InterfaceInfo if25 = new InterfaceInfo("查询成员产品", "/upc/IQueryEnableOpenServic/queryOfferComRelEnableByOfferIdAndRelOfferIdNeglectDate");
        if25.addRequestBody("{\"offerId\":\"150063207671\",\"relOfferId\":\"120000000150\"}");
        interfaces.add(if25);

        // 26. 查询销售属性值信息
        InterfaceInfo if26 = new InterfaceInfo("查询销售属性值信息", "/upc/IQueryOfferChaOpenService/queryOfferChaAndVal");
        if26.addRequestBody("{\"offerId\":\"130099999196\",\"mgmtDistrict\":\"ZZZZ\"}");
        interfaces.add(if26);

        // 27. 查询商品关联关系
        InterfaceInfo if27 = new InterfaceInfo("查询商品关联关系", "/upc/IQueryJoinRelOpenService/queryOfferJoinRelOfferByOfferIdRelType");
        if27.addRequestBody("{\"offerId\":\"110099925652\"}");
        interfaces.add(if27);

        // 28. 查询组合商品构成
        InterfaceInfo if28 = new InterfaceInfo("查询组合商品构成", "/upc/IQueryComRelOpenService/queryOfferComRelOfferByOfferId");
        if28.addRequestBody("{\"offerId\":\"150099575783\",\"mgmtDistrict\":\"ZZZZ\"}");
        interfaces.add(if28);

        // 29. OA平台商品上报审核接口
        InterfaceInfo if29 = new InterfaceInfo("OA平台商品上报审核接口", "/upc/IOrderUpGroupMgrOpenService/offerUpGroupOaAudit");
        if29.addRequestBody("{\"PRODUCT_ID\":\"9922042103\",\"ORDER_TYPE\":\"UP_GROUP_OPTIONAL_PKG_00\",\"STATUS\":\"2\"}");
        interfaces.add(if29);

        // 30. 获取缓存列表
        InterfaceInfo if30 = new InterfaceInfo("获取缓存列表", "/upc/IQueryCatalogOpenService/queryCatalogByUpCatalogIdDistrict");
        if30.addRequestBody("{\"upCatalogId\":\"HF\",\"mgmtDistrict\":\"ZZZZ\"}");
        interfaces.add(if30);

        // 31. OA平台商品上报接口
        InterfaceInfo if31 = new InterfaceInfo("OA平台商品上报接口", "/upc/IOrderUpGroupMgrOpenService/offerUpGroup");
        if31.addRequestBody("{\"PRODUCT_ID\":99001806,\"ORDER_TYPE\":\"UP_GROUP_BASE_PKG_00\",\"STAFF_NAME\":\"压测用户\",\"STAFF_CODE\":\"YCTEST01\"}");
        interfaces.add(if31);

        // 32. 根据商品编码查询商品信息
        InterfaceInfo if32 = new InterfaceInfo("根据商品编码查询商品信息", "/upc/IQueryOfferOpenService/getSimpleOfferByOfferId");
        if32.addRequestBody("{\"offerId\": 110085011412}");
        interfaces.add(if32);

        // 33. 查询需求管理平台发起的上报工单状态信息
        InterfaceInfo if33 = new InterfaceInfo("查询需求管理平台发起的上报工单状态信息", "/upc/IOrderUpGroupMgrOpenService/queryOaOnLineOrderByCond");
        if33.addRequestBody("{\"PRODUCT_ID\":\"45515859\",\"ORDER_TYPE\":\"UP_GROUP_BASE_PKG_00\"}");
        interfaces.add(if33);

        // 34. 根据分类编码与地市编码查询目录信息
        InterfaceInfo if34 = new InterfaceInfo("根据分类编码与地市编码查询目录信息", "/upc/IQueryGroupUpOfferOpenService/queryGroupUpOfferInfoByOfferId");
        if34.addRequestBody("{\"UP_OFFER_TYPE\":\"1\",\"OFFER_CODE\":\"99575784\"}");
        interfaces.add(if34);

        // 35. 三类二次确认信息局数据同步
        InterfaceInfo if35 = new InterfaceInfo("三类二次确认信息局数据同步", "/upc/IQueryBureDataOpenService/queryReconfirmInfo");
        if35.addRequestBody("{\"channelSource\":\"10011\",\"day\":3000}");
        interfaces.add(if35);

        // 36. 根据需求编码查询资费信息
        InterfaceInfo if36 = new InterfaceInfo("根据需求编码查询资费信息", "/upc/IQueryLocalOfferOpenService/queryOfferInfoByRequireCode");
        if36.addRequestBody("{\"REQUIRE_CODE\":\"0000S_SCJT00638\"}");
        interfaces.add(if36);

        // 37. 根据商品目录查询商品
        InterfaceInfo if37 = new InterfaceInfo("根据商品目录查询商品", "/upc/IQueryOfferOpenService/queryOfferByCatalogId");
        if37.addRequestBody("{\"catalogId\":\"BZBG\",\"rootId\":\"GROUP\",\"mgmtDistrict\":\"0731\"}");
        interfaces.add(if37);

        // 38. 大视频平台内容信息同步接口
        InterfaceInfo if38 = new InterfaceInfo("大视频平台内容信息同步接口", "/upc/ISyncOperateOfferOpenService/syncBigVideoInfo");
        if38.addRequestBody("{\"SP_CODE\":\"799210\",\"BIZ_CODE\":\"mg210999\",\"PRODUCT_ID\":\"8801190026\",\"PRODUCT_NAME\":\"芒果全站包单月55元\",\"PRODUCT_STATUS\":\"0\",\"VALID_DATE\":\"2025-7-23 17:49:58\",\"EXPIRE_DATE\":\"2025-12-31 17:49:58\",\"DONE_CODE\":\"9999\",\"CREATE_DATE\":\"2025-9-23 17:49:58\",\"DONE_DATE\":\"2025-9-23 17:49:58\",\"OP_ID\":\"YCTEST01\",\"ORG_ID\":\"0000\",\"REMARK\":\"集约化测试\"}");
        interfaces.add(if38);

        // 39. 子商品结构信息查询
        InterfaceInfo if39 = new InterfaceInfo("子商品结构信息查询", "/order/ICustServiceRaleInfoOpenService/queryOfferStrutureInfo");
        if39.addRequestBody("{\"SERIAL_NUMBER\": \"15874953758\", \"OFFER_ID\": \"130099883675\"}");
        interfaces.add(if39);

        // 40. 手机主套餐可选融合宽带产品查询接口
        InterfaceInfo if40 = new InterfaceInfo("手机主套餐可选融合宽带产品查询接口", "/order/IFusionBroadbandOpenService/queryBBProdsByMainDiscnt");
        if40.addRequestBody("{\"MAIN_DISCNT_CODE\":\"99706440\"}");
        interfaces.add(if40);

        // 41. 宽带办理优惠信息办理和展示
        InterfaceInfo if41 = new InterfaceInfo("宽带办理优惠信息办理和展示", "/order/IFusionBroadbandOpenService/getFusionBBDisInfo");
        if41.addRequestBody("{\"EPARCHY_CODE\":\"0731\",\"QUERY_TYPE\":\"SINGLE_BROADBAND_PACKAGE\",\"OFFER_CODE\":\"99799889\"}");
        interfaces.add(if41);

        // 42. 营销活动查询
        InterfaceInfo if42 = new InterfaceInfo("营销活动查询", "/order/IQuerySaleActiveHisOpenService/querySaleActives");
        if42.addRequestBody("{\"SERIAL_NUMBER\":\"13507480178\",\"paging\":\"1\",\"pageNum\":\"5\",\"rowsPerPage\":\"5\"}");
        interfaces.add(if42);

        // 43. 电渠ecop产品信息查询接口
        InterfaceInfo if43 = new InterfaceInfo("电渠ecop产品信息查询接口", "/order/IElemQryOpenService/queryProductByOfferCode");
        if43.addRequestBody("{\"OFFER_TYPE\":\"P\",\"OFFER_CODE\":\"16100021\"}");
        interfaces.add(if43);

        // 生成输出
        System.out.println("========== 接口curl命令集合 ==========\n");
        System.out.println(generateAllCurlCommands(interfaces));
        
        System.out.println("\n========== 接口请求模板集合 ==========\n");
        System.out.println(generateAllTemplates(interfaces));
    }
}