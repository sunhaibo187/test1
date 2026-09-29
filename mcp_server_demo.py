# -*- coding: utf-8 -*-
"""
MCP Server 最小可运行示例

核心知识：
- MCP = Model Context Protocol，AI 与外部工具/数据之间的统一协议
- 本文件是一个 "MCP Server"(提供能力的工具端)，通过标准 stdio 传输与 AI 客户端通信
- AI 客户端(Claude Desktop / Trae / 自研)启动本程序后，能自动"发现"本文件中
  用 @tool / @resource 注册的能力并直接调用

运行方式：
  1. 安装依赖（Python 3.10+）: pip install mcp
  2. 命令行调试（最直观）:
       mcp run mcp_server_demo.py
     或套接字方式便于外部调用:
       mcp run mcp_server_demo.py --transport sse
  3. 在支持 MCP 的客户端里配置（如 Trae IDE）:
       mcpServers:
         demo:
           command: python
           args: ["mcp_server_demo.py"]
"""

from mcp.server.fastmcp import FastMCP

# ============================================================
# 1. 创建 MCP 服务器实例（名称会展示给 AI 客户端）
# ============================================================
mcp = FastMCP("DemoToolServer", instructions="提供计算、时间、BOSS客户等级查询等演示能力")


# ============================================================
# 2. 用 @mcp.tool() 注册"工具" —— 这是最核心的部分
#    函数签名 = 工具入参，docstring = 参数说明(给 AI 看的)
#    AI 客户端会根据 docstring 自动决定传什么参数
# ============================================================

@mcp.tool()
def calculator(expression: str) -> str:
    """计算数学表达式。
    参数 expression: 要计算的数学表达式，例如 "3 + 5 * 2" 或 "2 ** 10"。
    返回计算结果。
    """
    try:
        # 安全起见仅允许数学运算字符
        allowed = set("0123456789+-*/.()% ")
        if not all(c in allowed for c in expression):
            return "错误：表达式包含非法字符"
        return f"{expression} = {eval(expression, {'__builtins__': {}}, {})}"
    except Exception as e:
        return f"计算失败: {e}"


@mcp.tool()
def get_current_time() -> str:
    """获取服务器当前时间。无参数。返回形如 2026-09-11 14:30:05 的时间字符串。"""
    from datetime import datetime
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


@mcp.tool()
def query_cust_level(serial_number: str) -> str:
    """模拟BOSS系统全球通客户等级查询接口。
    参数 serial_number: 11位手机号。
    返回客户等级信息。
    """
    if not (serial_number.isdigit() and len(serial_number) == 11):
        return "错误：手机号必须是11位数字"
    # 模拟返回(真实场景这里调用 /order/IGoToneCustLevelOpenService/qryOnecustlevel)
    return {
        "SERIAL_NUMBER": serial_number,
        "CUST_LEVEL": "全球通金卡客户",
        "LEVEL_CODE": "01",
        "QUERY_TIME": get_current_time(),
    }


# ============================================================
# 3. 用 @mcp.resource() 注册"资源" —— 向 AI 暴露可读取的数据
# ============================================================

@mcp.resource("demo://config/boss")
def get_boss_config():
    """BOSS系统演示配置信息(模拟)。"""
    return {
        "service_addr": "/order",
        "auth_type": "token",
        "timeout_sec": 30,
    }


# ============================================================
# 4. 用 @mcp.prompt() 注册"提示词模板" —— 给 AI 提供常用指令模板
# ============================================================

@mcp.prompt()
def boss_interface_qa() -> str:
    """BOSS接口测试助手提示词模板。"""
    return (
        "你是BOSS系统接口测试助手。用户会提供接口名称和参数，"
        "你应当：1) 组装请求报文 2) 调用本服务器上的查询工具 3) 解释返回结果。"
    )


# ============================================================
# 5. 启动服务器
# ============================================================
if __name__ == "__main__":
    # stdio 传输(默认)：客户端以子进程方式启动本脚本并通信
    mcp.run()