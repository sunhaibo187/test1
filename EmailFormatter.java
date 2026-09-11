import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 邮件格式设置工具类
 * 用于生成标准化的邮件格式
 */
public class EmailFormatter {

    public enum IssuePriority {
        LOW("低"),
        MEDIUM("中"),
        HIGH("高"),
        CRITICAL("紧急");

        private final String desc;

        IssuePriority(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }

    public static class EmailInfo {
        private String subject;
        private String content;
        private String issueReason;
        private String progress;
        private String solution;
        private IssuePriority priority;
        private String responsiblePerson;
        private String contact;
        private List<String> attachments;

        public EmailInfo() {
            this.priority = IssuePriority.MEDIUM;
            this.attachments = new ArrayList<>();
        }

        public EmailInfo subject(String subject) {
            this.subject = subject;
            return this;
        }

        public EmailInfo content(String content) {
            this.content = content;
            return this;
        }

        public EmailInfo issueReason(String issueReason) {
            this.issueReason = issueReason;
            return this;
        }

        public EmailInfo progress(String progress) {
            this.progress = progress;
            return this;
        }

        public EmailInfo solution(String solution) {
            this.solution = solution;
            return this;
        }

        public EmailInfo priority(IssuePriority priority) {
            this.priority = priority;
            return this;
        }

        public EmailInfo responsiblePerson(String responsiblePerson) {
            this.responsiblePerson = responsiblePerson;
            return this;
        }

        public EmailInfo contact(String contact) {
            this.contact = contact;
            return this;
        }

        public EmailInfo addAttachment(String attachment) {
            this.attachments.add(attachment);
            return this;
        }

        // Getters
        public String getSubject() { return subject; }
        public String getContent() { return content; }
        public String getIssueReason() { return issueReason; }
        public String getProgress() { return progress; }
        public String getSolution() { return solution; }
        public IssuePriority getPriority() { return priority; }
        public String getResponsiblePerson() { return responsiblePerson; }
        public String getContact() { return contact; }
        public List<String> getAttachments() { return attachments; }
    }

    private static final String SEPARATOR = "====================================";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 生成HTML格式的邮件
     */
    public static String generateHtmlEmail(EmailInfo emailInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <title>").append(emailInfo.getSubject()).append("</title>\n");
        sb.append("  <style>\n");
        sb.append("    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }\n");
        sb.append("    .container { max-width: 800px; margin: 0 auto; padding: 20px; }\n");
        sb.append("    .header { border-bottom: 2px solid #0066cc; padding-bottom: 10px; margin-bottom: 20px; }\n");
        sb.append("    .section { margin-bottom: 20px; padding: 15px; background: #f9f9f9; border-radius: 5px; }\n");
        sb.append("    .section h3 { color: #0066cc; margin-top: 0; }\n");
        sb.append("    .priority-LOW { background: #e6f7ff; border-left: 4px solid #1890ff; }\n");
        sb.append("    .priority-MEDIUM { background: #fff7e6; border-left: 4px solid #fa8c16; }\n");
        sb.append("    .priority-HIGH { background: #fff1f0; border-left: 4px solid #ff4d4f; }\n");
        sb.append("    .priority-CRITICAL { background: #fff1f0; border-left: 4px solid #cf1322; }\n");
        sb.append("    .footer { border-top: 1px solid #ddd; padding-top: 10px; margin-top: 20px; color: #666; font-size: 12px; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("  <div class=\"container\">\n");
        sb.append("    <div class=\"header\">\n");
        sb.append("      <h2>").append(emailInfo.getSubject()).append("</h2>\n");
        sb.append("      <p><strong>优先级：</strong>").append(emailInfo.getPriority().getDesc()).append(" | ");
        sb.append("<strong>时间：</strong>").append(DATE_FORMAT.format(new Date())).append("</p>\n");
        sb.append("    </div>\n");

        // 内容部分
        if (emailInfo.getContent() != null && !emailInfo.getContent().isEmpty()) {
            sb.append("    <div class=\"section\">\n");
            sb.append("      <h3>📝 内容</h3>\n");
            sb.append("      <p>").append(escapeHtml(emailInfo.getContent())).append("</p>\n");
            sb.append("    </div>\n");
        }

        // 问题原因
        if (emailInfo.getIssueReason() != null && !emailInfo.getIssueReason().isEmpty()) {
            sb.append("    <div class=\"section\">\n");
            sb.append("      <h3>🔍 问题原因</h3>\n");
            sb.append("      <p>").append(escapeHtml(emailInfo.getIssueReason())).append("</p>\n");
            sb.append("    </div>\n");
        }

        // 进度
        if (emailInfo.getProgress() != null && !emailInfo.getProgress().isEmpty()) {
            sb.append("    <div class=\"section\">\n");
            sb.append("      <h3>⏳ 进度</h3>\n");
            sb.append("      <p>").append(escapeHtml(emailInfo.getProgress())).append("</p>\n");
            sb.append("    </div>\n");
        }

        // 后续处理方案
        if (emailInfo.getSolution() != null && !emailInfo.getSolution().isEmpty()) {
            sb.append("    <div class=\"section\">\n");
            sb.append("      <h3>✅ 后续处理方案</h3>\n");
            sb.append("      <p>").append(escapeHtml(emailInfo.getSolution())).append("</p>\n");
            sb.append("    </div>\n");
        }

        // 负责人
        if (emailInfo.getResponsiblePerson() != null && !emailInfo.getResponsiblePerson().isEmpty()) {
            sb.append("    <div class=\"section\">\n");
            sb.append("      <h3>👤 负责人</h3>\n");
            sb.append("      <p>").append(escapeHtml(emailInfo.getResponsiblePerson()));
            if (emailInfo.getContact() != null && !emailInfo.getContact().isEmpty()) {
                sb.append("（联系：").append(escapeHtml(emailInfo.getContact())).append("）");
            }
            sb.append("</p>\n");
            sb.append("    </div>\n");
        }

        // 附件
        if (!emailInfo.getAttachments().isEmpty()) {
            sb.append("    <div class=\"section\">\n");
            sb.append("      <h3>📎 附件</h3>\n");
            sb.append("      <ul>\n");
            for (String attachment : emailInfo.getAttachments()) {
                sb.append("        <li>").append(escapeHtml(attachment)).append("</li>\n");
            }
            sb.append("      </ul>\n");
            sb.append("    </div>\n");
        }

        sb.append("    <div class=\"footer\">\n");
        sb.append("      <p>此邮件由系统自动生成，如有疑问请联系相关负责人。</p>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        return sb.toString();
    }

    /**
     * 生成纯文本格式的邮件
     */
    public static String generateTextEmail(EmailInfo emailInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(emailInfo.getSubject()).append("】\n");
        sb.append(SEPARATOR).append("\n");
        sb.append("优先级：").append(emailInfo.getPriority().getDesc()).append("\n");
        sb.append("时间：").append(DATE_FORMAT.format(new Date())).append("\n\n");

        if (emailInfo.getContent() != null && !emailInfo.getContent().isEmpty()) {
            sb.append("📝 内容\n");
            sb.append(emailInfo.getContent()).append("\n\n");
        }

        if (emailInfo.getIssueReason() != null && !emailInfo.getIssueReason().isEmpty()) {
            sb.append("🔍 问题原因\n");
            sb.append(emailInfo.getIssueReason()).append("\n\n");
        }

        if (emailInfo.getProgress() != null && !emailInfo.getProgress().isEmpty()) {
            sb.append("⏳ 进度\n");
            sb.append(emailInfo.getProgress()).append("\n\n");
        }

        if (emailInfo.getSolution() != null && !emailInfo.getSolution().isEmpty()) {
            sb.append("✅ 后续处理方案\n");
            sb.append(emailInfo.getSolution()).append("\n\n");
        }

        if (emailInfo.getResponsiblePerson() != null && !emailInfo.getResponsiblePerson().isEmpty()) {
            sb.append("👤 负责人\n");
            sb.append(emailInfo.getResponsiblePerson());
            if (emailInfo.getContact() != null && !emailInfo.getContact().isEmpty()) {
                sb.append("（联系：").append(emailInfo.getContact()).append("）");
            }
            sb.append("\n\n");
        }

        if (!emailInfo.getAttachments().isEmpty()) {
            sb.append("📎 附件\n");
            for (String attachment : emailInfo.getAttachments()) {
                sb.append("- ").append(attachment).append("\n");
            }
            sb.append("\n");
        }

        sb.append(SEPARATOR).append("\n");
        sb.append("此邮件由系统自动生成，如有疑问请联系相关负责人。\n");

        return sb.toString();
    }

    private static String escapeHtml(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#x27;");
    }

    public static void main(String[] args) {
        // 示例1：问题反馈邮件
        EmailInfo email1 = new EmailInfo()
            .subject("【问题反馈】登录系统异常")
            .content("用户在登录系统时遇到异常，无法正常登录。")
            .issueReason("初步判断是数据库连接超时导致，具体原因正在排查中。")
            .progress("已收集到错误日志，正在分析问题根源，预计2小时内有结果。")
            .solution("1. 修复数据库连接问题；2. 优化超时设置；3. 添加降级处理方案。")
            .priority(IssuePriority.HIGH)
            .responsiblePerson("张三")
            .contact("zhangsan@example.com")
            .addAttachment("错误日志.log")
            .addAttachment("数据库连接测试报告.pdf");

        System.out.println("========== HTML格式邮件 ==========");
        System.out.println(generateHtmlEmail(email1));
        
        System.out.println("\n\n========== 纯文本格式邮件 ==========");
        System.out.println(generateTextEmail(email1));

        // 示例2：进度汇报邮件
        EmailInfo email2 = new EmailInfo()
            .subject("【进度汇报】项目A开发进度")
            .content("本周项目A按计划进行，已完成核心功能开发。")
            .progress("完成度：80% | 前端：90% | 后端：75% | 测试：70%")
            .solution("下周计划：完成剩余功能、联调测试、性能优化。")
            .priority(IssuePriority.MEDIUM)
            .responsiblePerson("李四")
            .contact("lisi@example.com")
            .addAttachment("项目进度报告.docx")
            .addAttachment("测试报告.xlsx");

        System.out.println("\n\n========== 进度汇报邮件（纯文本） ==========");
        System.out.println(generateTextEmail(email2));
    }
}