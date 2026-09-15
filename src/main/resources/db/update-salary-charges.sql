-- Fill salary / charges on existing rows (safe to re-run)
USE vns_healthcare;

UPDATE employees SET salary = 22000.00 WHERE emp_code = 'EMP-1001';
UPDATE employees SET salary = 18000.00 WHERE emp_code = 'EMP-1002';
UPDATE employees SET salary = 24000.00 WHERE emp_code = 'EMP-1003';
UPDATE employees SET salary = 16000.00 WHERE emp_code = 'EMP-1004';
UPDATE employees SET salary = 15000.00 WHERE emp_code = 'EMP-1005';
UPDATE employees SET salary = 18000.00 WHERE salary IS NULL;

UPDATE customers SET charges = 35000.00 WHERE cust_code = 'CUS-1001';
UPDATE customers SET charges = 18000.00 WHERE cust_code = 'CUS-1002';
UPDATE customers SET charges = 38000.00 WHERE cust_code = 'CUS-1003';
UPDATE customers SET charges = 16000.00 WHERE cust_code = 'CUS-1004';
UPDATE customers SET charges = 36000.00 WHERE cust_code = 'CUS-1005';
UPDATE customers SET charges = 25000.00 WHERE charges IS NULL;
