-- 示例Oracle存储过程：根据手机号查询客户等级（BOSS场景）
-- 使用方法：将真实存储过程文件放置到本目录，ProcTranslator即可解析翻译
CREATE OR REPLACE PROCEDURE P_QUERY_CUST_LEVEL(
    p_serial_number IN VARCHAR2,     -- 输入：手机号码
    p_level OUT VARCHAR2             -- 输出：客户等级
) AS
    v_count NUMBER;                  -- 客户历史消费次数
BEGIN
    -- 查询客户历史消费次数
    SELECT COUNT(*) INTO v_count
    FROM CUST_INFO
    WHERE SERIAL_NUMBER = p_serial_number;

    -- 根据消费次数判定客户等级
    IF v_count = 0 THEN
        p_level := 'NONE';           -- 无消费记录
    ELSIF v_count < 10 THEN
        p_level := 'GOLD';           -- 普通客户
    ELSIF v_count < 50 THEN
        p_level := 'PLATINUM';       -- 中高端客户
    ELSE
        p_level := 'DIAMOND';        -- 高端客户
    END IF;

EXCEPTION
    WHEN NO_DATA_FOUND THEN
        p_level := 'NONE';
    WHEN OTHERS THEN
        p_level := 'ERROR';
END P_QUERY_CUST_LEVEL;
