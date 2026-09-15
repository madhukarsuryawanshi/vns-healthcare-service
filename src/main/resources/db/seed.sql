-- Sample data for VNS Healthcare (dummy names, mobiles, and Aadhar numbers)
-- Run after schema.sql, preferably on empty tables:
--   mysql -u root -p vns_healthcare < src/main/resources/db/seed.sql

USE vns_healthcare;

INSERT INTO app_sequence (seq_name, next_value) VALUES
    ('EMP', 1006),
    ('CUS', 1006)
ON DUPLICATE KEY UPDATE next_value = GREATEST(next_value, VALUES(next_value));

INSERT INTO employees (
    emp_code, full_name, mobile_no, joining_date, gender, date_of_birth,
    referred_by, full_address, aadhar_number, training_status, training_notes,
    onboarded, salary, salary_start_date, status, created_at, updated_at
) VALUES
    ('EMP-1001', 'Anita Sharma', '9876501001', '2025-04-12', 'FEMALE', '1994-08-21',
     'Sunita Devi', '12, Sector 14, Dwarka, New Delhi 110078', '234512345001',
     'COMPLETED', 'Bedside care, BP/sugar charting, elderly handling completed.',
     1, 22000.00, '2025-05-01', 'ACTIVE', '2025-04-12 10:15:00', '2025-05-01 09:00:00'),
    ('EMP-1002', 'Ramesh Kumar', '9876501002', '2025-06-03', 'MALE', '1989-02-14',
     NULL, '44, Ashok Nagar, Jaipur, Rajasthan 302001', '234512345002',
     'IN_PROGRESS', 'First-aid done. ICU assist module pending.',
     1, 18000.00, '2025-07-01', 'ACTIVE', '2025-06-03 11:20:00', '2025-07-01 09:00:00'),
    ('EMP-1003', 'Lakshmi Nair', '9876501003', '2025-08-18', 'FEMALE', '1996-11-05',
     'Dr. Mehta clinic', '8/21, Kaloor, Kochi, Kerala 682017', '234512345003',
     'COMPLETED', 'Trained for 24-hr dementia care and night duty.',
     1, 24000.00, '2025-09-01', 'ACTIVE', '2025-08-18 09:40:00', '2025-09-01 09:00:00'),
    ('EMP-1004', 'Mohammed Irfan', '9876501004', '2026-01-10', 'MALE', '1992-06-30',
     'Anita Sharma', 'B-102, Yelahanka, Bengaluru 560064', '234512345004',
     'IN_PROGRESS', 'Orientation week 2. Mobility support practice ongoing.',
     0, 16000.00, NULL, 'ACTIVE', '2026-01-10 14:05:00', '2026-01-20 16:00:00'),
    ('EMP-1005', 'Pooja Yadav', '9876501005', '2026-02-02', 'FEMALE', '1998-03-19',
     NULL, '31, Gomti Nagar, Lucknow, Uttar Pradesh 226010', '234512345005',
     'NOT_STARTED', NULL,
     0, 15000.00, NULL, 'ACTIVE', '2026-02-02 10:00:00', '2026-02-02 10:00:00');

INSERT INTO employee_documents (
    employee_id, original_filename, stored_filename, content_type, file_size, uploaded_at
) VALUES
    (1, 'anita_aadhar.pdf', 'anita_aadhar_sample.pdf', 'application/pdf', 184320, '2025-04-12 10:30:00'),
    (1, 'anita_nursing_cert.pdf', 'anita_cert_sample.pdf', 'application/pdf', 256000, '2025-04-20 12:10:00'),
    (2, 'ramesh_aadhar.pdf', 'ramesh_aadhar_sample.pdf', 'application/pdf', 172000, '2025-06-03 11:40:00'),
    (3, 'lakshmi_aadhar.pdf', 'lakshmi_aadhar_sample.pdf', 'application/pdf', 191000, '2025-08-18 10:05:00');

INSERT INTO customers (
    cust_code, full_name, mobile_no, address, patient_name, gender, age,
    medical_history, service_start_date, service_type, charges, assigned_employee_id,
    status, created_at, updated_at
) VALUES
    ('CUS-1001', 'Vikram Malhotra', '9811102001',
     'House 7, Vasant Vihar, New Delhi 110057', 'Sarla Malhotra', 'FEMALE', 78,
     'Post-stroke weakness, hypertension, needs help with walking and medicines.',
     '2025-05-05', 'HOURS_24', 35000.00, 1, 'ACTIVE', '2025-04-28 16:20:00', '2025-05-05 08:00:00'),
    ('CUS-1002', 'Neha Joshi', '9811102002',
     '22, C Scheme, Jaipur 302001', 'Harish Joshi', 'MALE', 82,
     'Type 2 diabetes, mild dementia. Day-time attendant required.',
     '2025-07-08', 'HOURS_12', 18000.00, 2, 'ASSIGNED', '2025-07-01 11:00:00', '2025-07-08 08:00:00'),
    ('CUS-1003', 'Arun Menon', '9811102003',
     'Villa 4, Panampilly Nagar, Kochi 682036', 'Kamala Menon', 'FEMALE', 74,
     'Parkinson’s, fall risk, night restlessness.',
     '2025-09-10', 'HOURS_24', 38000.00, 3, 'ACTIVE', '2025-09-02 09:15:00', '2025-09-10 08:00:00'),
    ('CUS-1004', 'Sanjay Reddy', '9811102004',
     '14, Indiranagar, Bengaluru 560038', 'Ramesh Reddy', 'MALE', 69,
     'Post hip-replacement physiotherapy support at home.',
     NULL, 'HOURS_12', 16000.00, NULL, 'NEW', '2026-02-15 13:45:00', '2026-02-15 13:45:00'),
    ('CUS-1005', 'Kavita Singh', '9811102005',
     'A-55, Gomti Nagar Ext., Lucknow 226010', 'Om Prakash Singh', 'MALE', 86,
     'COPD, oxygen at night. Family wants 24-hr care from next month.',
     '2026-04-01', 'HOURS_24', 36000.00, NULL, 'NEW', '2026-03-01 10:30:00', '2026-03-01 10:30:00');

INSERT INTO attendance (
    employee_id, attendance_date, status, check_in_time, check_out_time, notes, created_at
) VALUES
    (1, '2026-09-08', 'PRESENT', '08:00:00', '20:00:00', '24-hr shift start at Vasant Vihar', '2026-09-08 08:05:00'),
    (2, '2026-09-08', 'PRESENT', '08:30:00', '20:30:00', 'Day duty at C Scheme', '2026-09-08 08:35:00'),
    (3, '2026-09-08', 'LEAVE', NULL, NULL, 'Family function', '2026-09-08 07:50:00'),
    (1, '2026-09-09', 'PRESENT', '08:00:00', NULL, 'On site', '2026-09-09 08:02:00'),
    (2, '2026-09-09', 'HALF_DAY', '08:30:00', '14:00:00', 'Relieved at 2 pm', '2026-09-09 08:40:00'),
    (3, '2026-09-09', 'PRESENT', '07:45:00', NULL, 'Night handover done', '2026-09-09 07:50:00'),
    (4, '2026-09-09', 'ABSENT', NULL, NULL, 'Not yet onboarded — marked for training day', '2026-09-09 09:00:00');
