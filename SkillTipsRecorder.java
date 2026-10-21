// 测试
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skill 使用技巧记录工具
 *
 * 功能：
 *   1. 管理 Skill 使用技巧条目（分类 / 名称 / 触发时机 / 技巧要点 / 示例）
 *   2. 支持按分类查看、关键字搜索
 *   3. 支持从本地文件加载、导出为 Markdown 文档
 *   4. 内置常用 Skill 技巧示例数据，开箱即用
 *
 * 使用方式：
 *   java SkillTipsRecorder                 打印全部技巧
 *   java SkillTipsRecorder 分类名          按分类查看
 *   java SkillTipsRecorder -s 关键字       按关键字搜索
 *   java SkillTipsRecorder -e out.md       导出为 Markdown
 */
public class SkillTipsRecorder {

    /** 技巧条目 */
    public static class SkillTip {
        private final String category;      // 分类
        private final String name;          // Skill 名称
        private final String trigger;       // 触发时机（什么时候用）
        private final String tips;          // 技巧要点
        private final String example;       // 使用示例

        public SkillTip(String category, String name, String trigger, String tips, String example) {
            this.category = category;
            this.name = name;
            this.trigger = trigger;
            this.tips = tips;
            this.example = example;
        }

        public String getCategory() { return category; }
        public String getName() { return name; }
        public String getTrigger() { return trigger; }
        public String getTips() { return tips; }
        public String getExample() { return example; }

        /** 判断是否匹配关键字（分类/名称/要点/示例） */
        public boolean matches(String keyword) {
            if (keyword == null || keyword.isEmpty()) {
                return true;
            }
            String kw = keyword.toLowerCase();
            return category.toLowerCase().contains(kw)
                    || name.toLowerCase().contains(kw)
                    || trigger.toLowerCase().contains(kw)
                    || tips.toLowerCase().contains(kw)
                    || example.toLowerCase().contains(kw);
        }
    }

    /** 技巧仓库：分类 -> 条目列表（保持插入顺序） */
    private final Map<String, List<SkillTip>> repository = new LinkedHashMap<>();

    /** 添加一条技巧 */
    public void addTip(SkillTip tip) {
        repository.computeIfAbsent(tip.getCategory(), k -> new ArrayList<>()).add(tip);
    }

    /** 打印全部技巧 */
    public void printAll() {
        if (repository.isEmpty()) {
            System.out.println("暂无技巧记录。");
            return;
        }
        int index = 1;
        for (Map.Entry<String, List<SkillTip>> entry : repository.entrySet()) {
            System.out.println("========== " + entry.getKey() + " ==========");
            for (SkillTip tip : entry.getValue()) {
                System.out.println(formatTip(index++, tip));
            }
            System.out.println();
        }
    }

    /** 按分类打印 */
    public void printByCategory(String category) {
        List<SkillTip> tips = repository.get(category);
        if (tips == null || tips.isEmpty()) {
            System.out.println("未找到分类：" + category);
            System.out.println("可用分类：" + String.join("、", repository.keySet()));
            return;
        }
        System.out.println("========== " + category + " ==========");
        int index = 1;
        for (SkillTip tip : tips) {
            System.out.println(formatTip(index++, tip));
        }
    }

    /** 按关键字搜索 */
    public void search(String keyword) {
        List<SkillTip> result = new ArrayList<>();
        for (List<SkillTip> tips : repository.values()) {
            for (SkillTip tip : tips) {
                if (tip.matches(keyword)) {
                    result.add(tip);
                }
            }
        }
        if (result.isEmpty()) {
            System.out.println("未找到包含关键字 \"" + keyword + "\" 的技巧。");
            return;
        }
        System.out.println("搜索关键字：\"" + keyword + "\" ，共命中 " + result.size() + " 条：");
        int index = 1;
        for (SkillTip tip : result) {
            System.out.println(formatTip(index++, tip));
        }
    }

    /** 单条技巧格式化输出 */
    private String formatTip(int index, SkillTip tip) {
        StringBuilder sb = new StringBuilder();
        sb.append(index).append(". 【").append(tip.getName()).append("】\n");
        sb.append("   触发时机：").append(tip.getTrigger()).append("\n");
        sb.append("   技巧要点：").append(tip.getTips()).append("\n");
        if (tip.getExample() != null && !tip.getExample().isEmpty()) {
            sb.append("   使用示例：").append(tip.getExample()).append("\n");
        }
        return sb.toString();
    }

    /** 导出为 Markdown 文档 */
    public void exportMarkdown(String filePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Skill 使用技巧记录\n\n");
        sb.append("> 由 SkillTipsRecorder 自动生成\n\n");
        for (Map.Entry<String, List<SkillTip>> entry : repository.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n\n");
            sb.append("| 序号 | Skill 名称 | 触发时机 | 技巧要点 | 使用示例 |\n");
            sb.append("|------|-----------|----------|----------|----------|\n");
            int index = 1;
            for (SkillTip tip : entry.getValue()) {
                sb.append("| ").append(index++).append(" | ")
                  .append(tip.getName()).append(" | ")
                  .append(tip.getTrigger()).append(" | ")
                  .append(tip.getTips()).append(" | ")
                  .append(tip.getExample() == null ? "" : tip.getExample())
                  .append(" |\n");
            }
            sb.append("\n");
        }
        Files.write(Paths.get(filePath), sb.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("已导出 Markdown 文档：" + filePath);
    }

    /** 从本地文本文件加载技巧（格式：分类|名称|触发时机|技巧要点|使用示例） */
    public void loadFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("提示：未找到文件 " + filePath + "，跳过加载。");
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|");
                if (parts.length < 4) {
                    System.out.println("警告：第 " + lineNo + " 行格式不正确，已跳过：" + line);
                    continue;
                }
                String example = parts.length >= 5 ? parts[4] : "";
                addTip(new SkillTip(parts[0].trim(), parts[1].trim(),
                        parts[2].trim(), parts[3].trim(), example.trim()));
            }
        }
        System.out.println("已从 " + filePath + " 加载技巧记录。");
    }

    /** 构建内置示例数据 */
    public static SkillTipsRecorder buildDefault() {
        SkillTipsRecorder recorder = new SkillTipsRecorder();

        recorder.addTip(new SkillTip("代码审查", "TRAE-code-review",
                "明确要求对 MR/PR、commit、分支或代码差异做审查时",
                "只用于通用代码审查；安全审计、普通编码、功能修复不要用此技能",
                "帮我审查这个分支的代码质量"));

        recorder.addTip(new SkillTip("安全审查", "TRAE-security-review",
                "要求安全扫描、漏洞扫描或安全审计时",
                "只报告本次变更引入且可证实可利用的风险，不用于通用代码审查",
                "对这次提交做安全审查"));

        recorder.addTip(new SkillTip("运行时调试", "TRAE-debugger",
                "静态分析无法定位、需要收集运行时证据的复杂 Bug",
                "先提假设再插桩复现，遵循 假设→插桩→复现→分析→修复→验证 流程",
                "这个 Bug 静态看不出来，帮我做运行时调试"));

        recorder.addTip(new SkillTip("浏览器自动化", "TRAE-browseruse",
                "需要访问网址、抓取网页、测试前端 UI、执行多步浏览器交互时",
                "先加载技能再调用浏览器工具；抓取/测试前先明确目标元素",
                "打开登录页，填写表单并验证跳转"));

        recorder.addTip(new SkillTip("小程序开发", "TRAE-generate-mini-app",
                "明确要求创建/生成/修改小程序（微信/支付宝/抖音/Taro）时",
                "仅用于小程序；普通 Web、UI、移动端请求不适用",
                "帮我生成一个 Taro 小程序页面"));

        recorder.addTip(new SkillTip("技能创建", "skill-creator",
                "用户想创建/新增一个 Skill 时",
                "必须立即调用，不要只口头描述；先明确技能名称、触发场景、执行流程",
                "把我们这套压测流程做成一个技能"));

        return recorder;
    }

    /** 帮助信息 */
    private static void printUsage() {
        System.out.println("Skill 使用技巧记录工具");
        System.out.println("用法：");
        System.out.println("  java SkillTipsRecorder                打印全部技巧");
        System.out.println("  java SkillTipsRecorder <分类名>        按分类查看");
        System.out.println("  java SkillTipsRecorder -s <关键字>     按关键字搜索");
        System.out.println("  java SkillTipsRecorder -e <文件路径>   导出为 Markdown");
        System.out.println("  java SkillTipsRecorder -l <文件路径>   从文件加载后打印");
    }

    public static void main(String[] args) {
        // 内置示例数据
        SkillTipsRecorder recorder = buildDefault();

        if (args.length == 0) {
            recorder.printAll();
            printUsage();
            return;
        }

        String first = args[0];
        if ("-s".equals(first)) {
            if (args.length < 2) {
                System.out.println("请指定搜索关键字，例如：-s 调试");
                return;
            }
            recorder.search(args[1]);
        } else if ("-e".equals(first)) {
            String outPath = args.length >= 2 ? args[1] : "skill_tips.md";
            try {
                recorder.exportMarkdown(outPath);
            } catch (IOException e) {
                System.err.println("导出失败：" + e.getMessage());
            }
        } else if ("-l".equals(first)) {
            if (args.length < 2) {
                System.out.println("请指定文件路径，例如：-l tips.txt");
                return;
            }
            try {
                recorder.loadFromFile(args[1]);
                recorder.printAll();
            } catch (IOException e) {
                System.err.println("加载失败：" + e.getMessage());
            }
        } else {
            recorder.printByCategory(first);
        }
    }
}
