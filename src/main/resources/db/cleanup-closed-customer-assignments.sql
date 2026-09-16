-- Clear stale assigned employee references for already-closed customer records.
-- Run this once after deploying the fix to clean old data.

UPDATE customers
SET assigned_employee_id = NULL
WHERE status = 'CLOSED'
  AND assigned_employee_id IS NOT NULL;

-- Optional: verify the cleanup result.
SELECT id, cust_code, patient_name, status, assigned_employee_id
FROM customers
WHERE status = 'CLOSED'
ORDER BY id;
