-- Insert salary records for searchSalary tests
INSERT INTO op_salary_record (user_id, company_name, position, base_salary, months, bonus_info, stock_info, source, created_at, updated_at, create_by, update_by)
VALUES ('salary-test', '字节跳动', 'Java开发工程师', 35000.00, 15, '3个月年终', '期权', '招聘网站', NOW(), NOW(), 'test', 'test');

INSERT INTO op_salary_record (user_id, company_name, position, base_salary, months, bonus_info, stock_info, source, created_at, updated_at, create_by, update_by)
VALUES ('salary-test', '阿里巴巴', 'Java高级工程师', 40000.00, 16, '4个月年终', 'RSU', '朋友告知', NOW(), NOW(), 'test', 'test');

INSERT INTO op_salary_record (user_id, company_name, position, base_salary, months, stock_info, source, created_at, updated_at, create_by, update_by)
VALUES ('salary-test', '腾讯', 'C++开发工程师', 38000.00, 14, 'RSU+期权', '猎头', NOW(), NOW(), 'test', 'test');
