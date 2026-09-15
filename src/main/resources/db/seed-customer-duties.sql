USE vns_healthcare;

-- Sample duty ranges for CUS-1003 (Sep 2026): Anita 1-3, Lakshmi 4-8, Hold 9, Ramesh 10-12

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-01', e.id, 0, NOW() FROM customers c, employees e WHERE c.cust_code = 'CUS-1003' AND e.emp_code = 'EMP-1001'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), hold = VALUES(hold);

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-02', e.id, 0, NOW() FROM customers c, employees e WHERE c.cust_code = 'CUS-1003' AND e.emp_code = 'EMP-1001'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), hold = VALUES(hold);

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-03', e.id, 0, NOW() FROM customers c, employees e WHERE c.cust_code = 'CUS-1003' AND e.emp_code = 'EMP-1001'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), hold = VALUES(hold);

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-04', e.id, 0, NOW() FROM customers c, employees e WHERE c.cust_code = 'CUS-1003' AND e.emp_code = 'EMP-1003'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), hold = VALUES(hold);

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-05', e.id, 0, NOW() FROM customers c, employees e WHERE c.cust_code = 'CUS-1003' AND e.emp_code = 'EMP-1003'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), hold = VALUES(hold);

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-06', e.id, 0, NOW() FROM customers c, employees e WHERE c.cust_code = 'CUS-1003' AND e.emp_code = 'EMP-1003'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), hold = VALUES(hold);

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-07', e.id, 0, NOW() FROM customers c, employees e WHERE c.cust_code = 'CUS-1003' AND e.emp_code = 'EMP-1003'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), hold = VALUES(hold);

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-08', e.id, 0, NOW() FROM customers c, employees e WHERE c.cust_code = 'CUS-1003' AND e.emp_code = 'EMP-1003'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), hold = VALUES(hold);

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-09', NULL, 1, NOW() FROM customers c WHERE c.cust_code = 'CUS-1003'
ON DUPLICATE KEY UPDATE employee_id = NULL, hold = 1;

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-10', e.id, 0, NOW() FROM customers c, employees e WHERE c.cust_code = 'CUS-1003' AND e.emp_code = 'EMP-1002'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), hold = VALUES(hold);

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-11', e.id, 0, NOW() FROM customers c, employees e WHERE c.cust_code = 'CUS-1003' AND e.emp_code = 'EMP-1002'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), hold = VALUES(hold);

INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_at)
SELECT c.id, '2026-09-12', e.id, 0, NOW() FROM customers c, employees e WHERE c.cust_code = 'CUS-1003' AND e.emp_code = 'EMP-1002'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), hold = VALUES(hold);
