# VNS Healthcare

Home-care desk for staff, patient leads, assignments, and attendance. Built with **Spring Boot 2.7** (Java 8), **Thymeleaf**, and **MySQL**.

## What you can do

- Add employees (name, mobile, joining date, gender, DOB, referral, address, Aadhar, **salary**, documents)
- Add **leads/customers** (Cust ID, family contact, patient, service 12/24 hrs, **charges**, medical history)
- Search staff by **name / mobile / Aadhar / Emp ID**
- Record **training** notes by hand
- Print a **brochure / profile** to share with the customer (KYC is not printed)
- **Onboard** staff when salary starts
- **Assign** an employee to the customer location
- Mark **attendance** for the active staff list
- Download an **Excel attendance report** (date range + in-hand salary)
- Download a **customer duty report** (employee per day, Hold unpaid)
- **Close Service** on a lead to bill duty days and lock the record
- Track **salary paid status** by month (Paid / In progress / Unpaid)

## Database tables

See `src/main/resources/db/schema.sql`. Summary:

| Table | Purpose |
| --- | --- |
| `app_sequence` | Generates `EMP-1001`, `CUS-1001` |
| `employees` | Care staff master |
| `employee_documents` | Uploaded KYC / certificates |
| `customers` | Patient leads |
| `customer_duties` | Daily caregiver or Hold on a case |
| `attendance` | One row per employee per day |
| `salary_payments` | Monthly salary Paid / In progress / Unpaid |

## Run locally

1. Start MySQL (or `docker compose up -d`).
2. Set username/password in `src/main/resources/application.properties` (defaults: `root` / `root`).
3. From the project folder:

```
mvn spring-boot:run
```

4. Open [http://localhost:8080](http://localhost:8080)

Hibernate creates/updates tables on startup (`ddl-auto=update`). Uploaded files go to the `uploads/` folder.
