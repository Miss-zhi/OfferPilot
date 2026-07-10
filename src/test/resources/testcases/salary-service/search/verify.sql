SELECT '薪资记录总数' AS check_point, CAST(COUNT(*) AS CHAR) AS actual, '3' AS expected FROM op_salary_record WHERE create_by = 'test';

SELECT '字节跳动记录存在' AS check_point, company_name AS actual, '字节跳动' AS expected FROM op_salary_record WHERE company_name = '字节跳动' AND create_by = 'test' LIMIT 1;
