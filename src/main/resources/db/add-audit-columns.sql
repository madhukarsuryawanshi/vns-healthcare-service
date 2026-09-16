-- Run this on an existing MySQL database to change audit columns to username values.
-- The app stores the logged-in username instead of the numeric user id.

ALTER TABLE app_sequence
    MODIFY COLUMN created_by VARCHAR(100) NULL,
    MODIFY COLUMN updated_by VARCHAR(100) NULL;

ALTER TABLE employees
    MODIFY COLUMN created_by VARCHAR(100) NULL,
    MODIFY COLUMN updated_by VARCHAR(100) NULL;

ALTER TABLE employee_documents
    MODIFY COLUMN created_by VARCHAR(100) NULL,
    MODIFY COLUMN updated_by VARCHAR(100) NULL;

ALTER TABLE customers
    MODIFY COLUMN created_by VARCHAR(100) NULL,
    MODIFY COLUMN updated_by VARCHAR(100) NULL;

ALTER TABLE customer_documents
    MODIFY COLUMN created_by VARCHAR(100) NULL,
    MODIFY COLUMN updated_by VARCHAR(100) NULL;

ALTER TABLE customer_duties
    MODIFY COLUMN created_by VARCHAR(100) NULL,
    MODIFY COLUMN updated_by VARCHAR(100) NULL;

ALTER TABLE attendance
    MODIFY COLUMN created_by VARCHAR(100) NULL,
    MODIFY COLUMN updated_by VARCHAR(100) NULL;

ALTER TABLE salary_payments
    MODIFY COLUMN created_by VARCHAR(100) NULL,
    MODIFY COLUMN updated_by VARCHAR(100) NULL;

ALTER TABLE app_users
    MODIFY COLUMN created_by VARCHAR(100) NULL,
    MODIFY COLUMN updated_by VARCHAR(100) NULL;

ALTER TABLE roles
    MODIFY COLUMN created_by VARCHAR(100) NULL,
    MODIFY COLUMN updated_by VARCHAR(100) NULL;
