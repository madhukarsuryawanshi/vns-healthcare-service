-- VNS Healthcare – MySQL schema (JPA also creates/updates these tables)
-- Database: vns_healthcare

CREATE DATABASE IF NOT EXISTS vns_healthcare
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE vns_healthcare;

-- Auto-generated business codes: EMP-1001, CUS-1001
CREATE TABLE IF NOT EXISTS app_sequence (
    seq_name    VARCHAR(40) NOT NULL PRIMARY KEY,
    next_value  BIGINT      NOT NULL,
    created_by  VARCHAR(100) NULL,
    updated_by  VARCHAR(100) NULL
);

-- Care staff (nurses / attendants)
CREATE TABLE IF NOT EXISTS employees (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    emp_code           VARCHAR(20)  NOT NULL UNIQUE,
    full_name          VARCHAR(120) NOT NULL,
    mobile_no          VARCHAR(15)   NOT NULL,
    joining_date       DATE         NOT NULL,
    gender             VARCHAR(10)  NOT NULL,
    date_of_birth      DATE         NOT NULL,
    referred_by        VARCHAR(120) NULL,
    full_address       VARCHAR(500) NOT NULL,
    aadhar_number      VARCHAR(12)   NOT NULL UNIQUE,
    training_status    VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED',
    training_notes     VARCHAR(1000) NULL,
    onboarded          BIT(1)       NOT NULL DEFAULT 0,
    salary             DECIMAL(12,2) NULL,
    salary_start_date  DATE         NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by         VARCHAR(100) NULL,
    updated_by         VARCHAR(100) NULL,
    created_at         DATETIME     NOT NULL,
    updated_at         DATETIME     NOT NULL,
    INDEX idx_emp_name (full_name),
    INDEX idx_emp_mobile (mobile_no)
);

-- KYC / ID / certificates uploaded for an employee
CREATE TABLE IF NOT EXISTS employee_documents (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id        BIGINT       NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    stored_filename    VARCHAR(255) NOT NULL,
    content_type       VARCHAR(120) NULL,
    file_size          BIGINT       NOT NULL,
    created_by         VARCHAR(100) NULL,
    updated_by         VARCHAR(100) NULL,
    uploaded_at        DATETIME     NOT NULL,
    CONSTRAINT fk_doc_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
);

-- Patients / household contacts (leads)
CREATE TABLE IF NOT EXISTS customers (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cust_code            VARCHAR(20)  NOT NULL UNIQUE,
    full_name            VARCHAR(120) NOT NULL,
    mobile_no            VARCHAR(15)  NOT NULL,
    address              VARCHAR(500) NOT NULL,
    patient_name         VARCHAR(120) NOT NULL,
    gender               VARCHAR(10)  NULL,
    age                  INT          NOT NULL,
    medical_history      VARCHAR(2000) NULL,
    service_start_date   DATE         NULL,
    service_type         VARCHAR(20)  NOT NULL,
    charges              DECIMAL(12,2) NULL,
    assigned_employee_id BIGINT       NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    billed_amount        DECIMAL(12,2) NULL,
    service_closed_date  DATE         NULL,
    created_by           VARCHAR(100) NULL,
    updated_by           VARCHAR(100) NULL,
    created_at           DATETIME     NOT NULL,
    updated_at           DATETIME     NOT NULL,
    CONSTRAINT fk_cust_employee FOREIGN KEY (assigned_employee_id) REFERENCES employees (id) ON DELETE SET NULL,
    INDEX idx_cust_name (full_name),
    INDEX idx_cust_mobile (mobile_no)
);

-- KYC / ID / certificates uploaded for a customer
CREATE TABLE IF NOT EXISTS customer_documents (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id        BIGINT       NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    stored_filename    VARCHAR(255) NOT NULL,
    content_type       VARCHAR(120) NULL,
    file_size          BIGINT       NOT NULL,
    created_by         VARCHAR(100) NULL,
    updated_by         VARCHAR(100) NULL,
    uploaded_at        DATETIME     NOT NULL,
    CONSTRAINT fk_customer_doc_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE
);

-- Daily caregiver / Hold on a customer location
CREATE TABLE IF NOT EXISTS customer_duties (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id  BIGINT       NOT NULL,
    duty_date    DATE         NOT NULL,
    employee_id  BIGINT       NULL,
    hold         BIT(1)       NOT NULL DEFAULT 0,
    created_by   VARCHAR(100) NULL,
    updated_by   VARCHAR(100) NULL,
    created_at   DATETIME     NOT NULL,
    UNIQUE KEY uk_cust_day (customer_id, duty_date),
    CONSTRAINT fk_duty_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT fk_duty_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE SET NULL
);

-- Daily attendance for onboarded / active staff
CREATE TABLE IF NOT EXISTS attendance (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id      BIGINT       NOT NULL,
    attendance_date  DATE         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    check_in_time    TIME         NULL,
    check_out_time   TIME         NULL,
    notes            VARCHAR(500) NULL,
    created_by       VARCHAR(100) NULL,
    updated_by       VARCHAR(100) NULL,
    created_at       DATETIME     NOT NULL,
    UNIQUE KEY uk_emp_day (employee_id, attendance_date),
    CONSTRAINT fk_att_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
);

-- Monthly salary paid / in progress / unpaid
CREATE TABLE IF NOT EXISTS salary_payments (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id  BIGINT        NOT NULL,
    pay_year     INT           NOT NULL,
    pay_month    INT           NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    amount       DECIMAL(12,2) NULL,
    paid_on      DATE          NULL,
    notes        VARCHAR(500)  NULL,
    created_by   VARCHAR(100)  NULL,
    updated_by   VARCHAR(100)  NULL,
    created_at   DATETIME      NOT NULL,
    UNIQUE KEY uk_emp_salary_month (employee_id, pay_year, pay_month),
    CONSTRAINT fk_sal_employee FOREIGN KEY (employee_id) REFERENCES employees (id) ON DELETE CASCADE
);

-- Existing databases (Hibernate ddl-auto=update also adds these):
-- ALTER TABLE employees ADD COLUMN salary DECIMAL(12,2) NULL;
-- ALTER TABLE customers ADD COLUMN charges DECIMAL(12,2) NULL;
