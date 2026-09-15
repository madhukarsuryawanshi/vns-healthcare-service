-- Sample: 2026 Jun–Aug paid for onboarded staff; September stays In progress by default
USE vns_healthcare;

INSERT INTO salary_payments (employee_id, pay_year, pay_month, status, amount, paid_on, notes, created_at)
SELECT e.id, 2026, 6, 'PAID', e.salary, '2026-07-02', 'June salary', NOW()
FROM employees e WHERE e.emp_code IN ('EMP-1001', 'EMP-1002', 'EMP-1003')
ON DUPLICATE KEY UPDATE status = 'PAID', amount = VALUES(amount), paid_on = VALUES(paid_on);

INSERT INTO salary_payments (employee_id, pay_year, pay_month, status, amount, paid_on, notes, created_at)
SELECT e.id, 2026, 7, 'PAID', e.salary, '2026-08-02', 'July salary', NOW()
FROM employees e WHERE e.emp_code IN ('EMP-1001', 'EMP-1002', 'EMP-1003')
ON DUPLICATE KEY UPDATE status = 'PAID', amount = VALUES(amount), paid_on = VALUES(paid_on);

INSERT INTO salary_payments (employee_id, pay_year, pay_month, status, amount, paid_on, notes, created_at)
SELECT e.id, 2026, 8, 'PAID', e.salary, '2026-09-02', 'August salary', NOW()
FROM employees e WHERE e.emp_code IN ('EMP-1001', 'EMP-1002', 'EMP-1003')
ON DUPLICATE KEY UPDATE status = 'PAID', amount = VALUES(amount), paid_on = VALUES(paid_on);

INSERT INTO salary_payments (employee_id, pay_year, pay_month, status, amount, paid_on, notes, created_at)
SELECT e.id, 2026, 9, 'IN_PROGRESS', e.salary, NULL, 'September in progress', NOW()
FROM employees e WHERE e.emp_code IN ('EMP-1001', 'EMP-1002', 'EMP-1003')
ON DUPLICATE KEY UPDATE status = 'IN_PROGRESS';
