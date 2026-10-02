#!/usr/bin/env python3
"""
Import the provided Excel sheets into the VNS Healthcare MySQL schema.

Notes:
- The spreadsheet files have historical/monthly sheets and are not perfectly normalized.
- Missing required fields are defaulted to safe values to satisfy the schema.
- The script is meant to be reviewed/edited before production use.
"""

import argparse
import hashlib
import os
import re
import sys
from datetime import datetime, date, timedelta
from decimal import Decimal, InvalidOperation
from pathlib import Path

import mysql.connector
from openpyxl import load_workbook


DEFAULT_FILES = [
    r"C:\Users\Madhukar\Downloads\data employee.xlsx"
]


def to_text(value):
    if value is None:
        return ""
    if isinstance(value, str):
        return value.strip()
    return str(value).strip()


def normalize_name(value):
    text = to_text(value)
    text = re.sub(r"\s+", " ", text)
    return text.title() if text else ""


def is_valid_human_name(value):
    text = normalize_name(value)
    if not text:
        return False
    if re.fullmatch(r"[\d\s\-+/()]+", text):
        return False
    return bool(re.search(r"[A-Za-z]", text))


def normalize_phone(value):
    digits = re.sub(r"\D", "", to_text(value))
    if len(digits) >= 10:
        return digits[-10:]
    return digits


def normalize_gender(value):
    text = to_text(value).upper()
    if text in {"M", "MALE", "MAN"}:
        return "MALE"
    if text in {"F", "FEMALE", "WOMAN"}:
        return "FEMALE"
    return "MALE"


def parse_date(value):
    if value is None or to_text(value) == "":
        return None
    if isinstance(value, datetime):
        return value.date()
    if isinstance(value, date):
        return value
    text = to_text(value)
    if not text:
        return None

    for fmt in [
        "%Y-%m-%d",
        "%d-%m-%Y",
        "%d/%m/%Y",
        "%m/%d/%Y",
        "%d-%b-%y",
        "%d-%b-%Y",
        "%d %b %Y",
        "%d %B %Y",
        "%d-%m-%y",
        "%Y/%m/%d",
    ]:
        try:
            return datetime.strptime(text, fmt).date()
        except ValueError:
            pass

    if re.search(r"\d{4}-\d{2}-\d{2}", text):
        match = re.search(r"(\d{4})-(\d{2})-(\d{2})", text)
        if match:
            try:
                return datetime.strptime(match.group(0), "%Y-%m-%d").date()
            except ValueError:
                pass

    return None


def parse_decimal(value):
    if value is None or to_text(value) == "":
        return None
    text = to_text(value).replace(",", "")
    if text in {"-", "--", "NA", "N/A", "None", "NULL"}:
        return None
    try:
        return Decimal(text)
    except InvalidOperation:
        return None


def parse_month_label(label):
    text = to_text(label)
    match = re.search(r"([A-Za-z]+)[\s-]+(\d{2,4})", text)
    if not match:
        return None, None
    month_name = match.group(1).strip().lower()
    year_part = match.group(2).strip()
    year = int(year_part) if len(year_part) == 4 else 2000 + int(year_part)
    month_map = {
        "jan": 1, "january": 1,
        "feb": 2, "february": 2,
        "mar": 3, "march": 3,
        "apr": 4, "april": 4,
        "may": 5,
        "jun": 6, "june": 6,
        "jul": 7, "july": 7,
        "aug": 8, "august": 8,
        "sep": 9, "sept": 9, "september": 9,
        "oct": 10, "october": 10,
        "nov": 11, "november": 11,
        "dec": 12, "december": 12,
    }
    month = month_map.get(month_name[:3])
    return year, month


def month_from_sheet(sheet_name):
    year, month = parse_month_label(sheet_name)
    if year and month:
        return year, month
    return None, None


def normalize_service_type(value):
    text = to_text(value).upper().replace(" ", "_")
    if "24" in text or "24H" in text or "24_H" in text:
        return "HOURS_24"
    if "12" in text or "12H" in text or "12_H" in text:
        return "HOURS_12"
    return "HOURS_12"


def normalize_customer_status(value):
    status = to_text(value).upper()
    if "CLOSED" in status or "CLOSE" in status:
        return "CLOSED"
    if "ACTIVE" in status:
        return "ACTIVE"
    if "ASSIGNED" in status:
        return "ASSIGNED"
    return "NEW"


def normalize_salary_status(value):
    text = to_text(value).upper()
    if "PAID" in text or "RECEIVED" in text or "RECIEVED" in text:
        return "PAID"
    if "HOLD" in text or "IN PROGRESS" in text or "PENDING" in text:
        return "IN_PROGRESS"
    return "UNPAID"


def normalize_charge_status(value):
    text = to_text(value).upper()
    if "PAID" in text or "RECEIVED" in text or "RECIEVED" in text:
        return "PAID"
    if "HOLD" in text or "PENDING" in text or "IN PROGRESS" in text:
        return "IN_PROGRESS"
    return "UNPAID"


def normalize_attendance_status(value):
    text = to_text(value).upper()
    if text in {"D", "P", "PRESENT", "PRS", "ON"}:
        return "PRESENT"
    if text in {"N", "DN", "D/N", "DAY_NIGHT", "FULL"}:
        return "PRESENT"
    if text in {"A", "ABSENT", "OFF"}:
        return "ABSENT"
    if text in {"L", "LEAVE", "LV"}:
        return "LEAVE"
    if text in {"HD", "HALF_DAY", "HALF"}:
        return "HALF_DAY"
    if text in {"0", "-", "", "NONE", "NULL"}:
        return None
    return "PRESENT"


def date_from_status_text(text):
    raw = to_text(text)
    for token in re.findall(r"\d{1,2}[-/][A-Za-z]{3,9}[-/]\d{2,4}", raw):
        d = parse_date(token)
        if d:
            return d
    for token in re.findall(r"\d{1,2}[/-]\d{1,2}[/-]\d{2,4}", raw):
        d = parse_date(token)
        if d:
            return d
    return None


def generate_aadhar(phone):
    digits = re.sub(r"\D", "", to_text(phone))
    if len(digits) >= 12:
        return digits[:12]
    seed = digits or "1234567890"
    while len(seed) < 12:
        seed += str(len(seed) + 1)
    return seed[:12]


def generate_customer_code(candidate):
    text = to_text(candidate)
    if not text:
        text = "CUS-1001"
    text = re.sub(r"[^A-Z0-9-]", "", text.upper())
    if not text:
        text = "CUS-1001"
    return text[:20]


def generate_employee_code(cursor):
    row = fetch_one(cursor, "SELECT COUNT(*) FROM employees")
    count = int(row[0]) if row and row[0] is not None else 0
    return f"EMP-{1000 + count + 1}"


def connect_db(args):
    conn = mysql.connector.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        database=args.database,
        autocommit=False,
        charset="utf8mb4",
    )
    return conn


def ensure_database(args):
    conn = mysql.connector.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=args.password,
        charset="utf8mb4",
        autocommit=True,
    )
    cursor = conn.cursor()
    cursor.execute(f"CREATE DATABASE IF NOT EXISTS `{args.database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    cursor.close()
    conn.close()


def fetch_one(cursor, query, params=()):
    cursor.execute(query, params)
    row = cursor.fetchone()
    return row


def employee_row_matches(row, name, phone):
    if not row:
        return False
    if row[0] and name and normalize_name(row[0]) == normalize_name(name):
        return True
    if row[1] and phone and normalize_phone(row[1]) == normalize_phone(phone):
        return True
    return False


def get_or_create_employee(cursor, name, phone, joining_date=None):
    full_name = normalize_name(name)
    mobile = normalize_phone(phone)
    if not full_name and not mobile:
        return None

    if full_name:
        row = fetch_one(cursor, "SELECT id, full_name, mobile_no FROM employees WHERE LOWER(full_name)=LOWER(%s) LIMIT 1", (full_name,))
        if row and employee_row_matches(row, full_name, mobile):
            return row[0]
    if mobile:
        row = fetch_one(cursor, "SELECT id, full_name, mobile_no FROM employees WHERE mobile_no=%s LIMIT 1", (mobile,))
        if row:
            return row[0]

    # Default values for schema-required columns not present in spreadsheets
    gender = "MALE"
    dob = joining_date - timedelta(days=25 * 365) if joining_date else date.today() - timedelta(days=25 * 365)
    address = "Imported from spreadsheet"
    aadhar = generate_aadhar(mobile or full_name)

    emp_code = generate_employee_code(cursor)

    cursor.execute(
        """
        INSERT INTO employees (
            emp_code, full_name, mobile_no, joining_date, gender, date_of_birth,
            referred_by, full_address, aadhar_number, training_status, training_notes,
            onboarded, salary, salary_start_date, status, created_at, updated_at
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
        """,
        (
            emp_code,
            full_name,
            mobile or "0000000000",
            joining_date,
            gender,
            dob,
            None,
            address,
            aadhar,
            "NOT_STARTED",
            None,
            1 if joining_date else 0,
            None,
            joining_date,
            "ACTIVE",
        ),
    )
    return cursor.lastrowid


def update_employee_fields(cursor, employee_id, row, header_map):
    if employee_id is None:
        return

    assignments = []
    params = []

    def assign_value(column, value):
        if value is not None:
            assignments.append(f"{column} = %s")
            params.append(value)

    name = None
    if "name" in header_map and header_map["name"] < len(row):
        name = normalize_name(row[header_map["name"]])
    if name:
        assign_value("full_name", name)

    mobile = None
    if "mobile" in header_map and header_map["mobile"] < len(row):
        mobile = normalize_phone(row[header_map["mobile"]])
    if mobile:
        assign_value("mobile_no", mobile)

    joining_date = None
    if "joining_date" in header_map and header_map["joining_date"] < len(row):
        joining_date = parse_date(row[header_map["joining_date"]])
    if joining_date:
        assign_value("joining_date", joining_date)

    dob = None
    if "date_of_birth" in header_map and header_map["date_of_birth"] < len(row):
        dob = parse_date(row[header_map["date_of_birth"]])
    if dob:
        assign_value("date_of_birth", dob)

    gender = None
    if "gender" in header_map and header_map["gender"] < len(row):
        gender = normalize_gender(row[header_map["gender"]])
    if gender:
        assign_value("gender", gender)

    address = None
    if "address" in header_map and header_map["address"] < len(row):
        address = to_text(row[header_map["address"]])
    if address:
        assign_value("full_address", address)

    aadhar = None
    if "aadhar" in header_map and header_map["aadhar"] < len(row):
        aadhar = to_text(row[header_map["aadhar"]])
    if aadhar:
        assign_value("aadhar_number", re.sub(r"\D", "", aadhar)[:12])

    salary = None
    if "salary" in header_map and header_map["salary"] < len(row):
        salary = parse_decimal(row[header_map["salary"]])
    if salary is not None:
        assign_value("salary", salary)

    experience = None
    if "experience" in header_map and header_map["experience"] < len(row):
        experience = parse_decimal(row[header_map["experience"]])
    if experience is not None:
        assign_value("no_of_experience", experience)

    designation = None
    if "designation" in header_map and header_map["designation"] < len(row):
        designation = to_text(row[header_map["designation"]]).upper().replace(" ", "_")
    if designation:
        assign_value("designation", designation)

    email = None
    if "email" in header_map and header_map["email"] < len(row):
        email = to_text(row[header_map["email"]])
    if email:
        assign_value("email", email)

    marital = None
    if "marital_status" in header_map and header_map["marital_status"] < len(row):
        marital = to_text(row[header_map["marital_status"]])
    if marital:
        assign_value("marital_status", marital)

    referred = None
    if "referred_by" in header_map and header_map["referred_by"] < len(row):
        referred = to_text(row[header_map["referred_by"]])
    if referred:
        assign_value("referred_by", referred)

    if not assignments:
        return

    sql = "UPDATE employees SET " + ", ".join(assignments) + ", updated_at = NOW() WHERE id = %s"
    cursor.execute(sql, params + [employee_id])


def import_employee_sheet(conn, excel_path):
    wb = load_workbook(excel_path, data_only=True, read_only=True)
    cursor = conn.cursor()
    total = 0

    for ws in wb.worksheets:
        rows = list(ws.iter_rows(values_only=True))
        header_row_index = None
        header_row = None
        for idx, row in enumerate(rows):
            cells = [to_text(v).lower() for v in row]
            haystack = " ".join(cells)
            if any(token in haystack for token in ["name", "mobile", "phone", "aadhar", "joining", "dob", "date of birth"]):
                header_row_index = idx
                header_row = row
                break
        if header_row_index is None:
            continue

        header_map = {}
        for idx, cell in enumerate(header_row):
            key = re.sub(r"[^a-z0-9]+", "_", to_text(cell).lower()).strip("_")
            if not key:
                continue
            normalized = key.replace("__", "_")
            header_map[normalized] = idx

            name_tokens = {"name", "employee_name", "emp_name", "full_name"}
            if normalized in name_tokens or ("name" in normalized and "employee" in normalized) or ("name" in normalized and "full" in normalized):
                header_map["name"] = idx
            if "mobile" in normalized or "phone" in normalized:
                header_map["mobile"] = idx
            if "aadhaar" in normalized or "aadhar" in normalized or "adhar" in normalized:
                header_map["aadhar"] = idx
            if "join" in normalized or "joining" in normalized:
                header_map["joining_date"] = idx
            if "dob" in normalized or ("date" in normalized and "birth" in normalized):
                header_map["date_of_birth"] = idx
            if "gender" in normalized:
                header_map["gender"] = idx
            if "address" in normalized or "location" in normalized:
                header_map["address"] = idx
            if "salary" in normalized:
                header_map["salary"] = idx
            if "exp" in normalized:
                header_map["experience"] = idx
            if "designation" in normalized:
                header_map["designation"] = idx
            if "email" in normalized:
                header_map["email"] = idx
            if "marital" in normalized:
                header_map["marital_status"] = idx
            if "referred" in normalized:
                header_map["referred_by"] = idx

        for row in rows[header_row_index + 1:]:
            if not row or all(v is None or to_text(v) == "" for v in row):
                continue
            name_idx = header_map.get("name")
            mobile_idx = header_map.get("mobile")
            raw_name = row[name_idx] if name_idx is not None and name_idx < len(row) else None
            name = normalize_name(raw_name)
            if name and not is_valid_human_name(name):
                name = ""
            phone = normalize_phone(row[mobile_idx] if mobile_idx is not None and mobile_idx < len(row) else None)
            if not name and not phone:
                continue

            joining_date = None
            if "joining_date" in header_map and header_map["joining_date"] < len(row):
                joining_date = parse_date(row[header_map["joining_date"]])

            employee_id = get_or_create_employee(cursor, name or phone, phone or name, joining_date)
            if employee_id is None:
                continue

            update_employee_fields(cursor, employee_id, row, header_map)
            total += 1

    cursor.close()
    return total


def get_customer_id(cursor, full_name, mobile_no=None, code=None):
    full_name = normalize_name(full_name)
    mobile = normalize_phone(mobile_no)
    if code:
        row = fetch_one(cursor, "SELECT id FROM customers WHERE cust_code=%s LIMIT 1", (to_text(code),))
        if row:
            return row[0]
    if mobile:
        row = fetch_one(cursor, "SELECT id FROM customers WHERE mobile_no=%s LIMIT 1", (mobile,))
        if row:
            return row[0]
    if full_name:
        row = fetch_one(cursor, "SELECT id FROM customers WHERE full_name=%s LIMIT 1", (full_name,))
        if row:
            return row[0]
    return None


def get_employee_id_by_name(cursor, name):
    full_name = normalize_name(name)
    if not full_name:
        return None
    row = fetch_one(cursor, "SELECT id FROM employees WHERE LOWER(full_name)=LOWER(%s) LIMIT 1", (full_name,))
    return row[0] if row else None


def upsert_customer(cursor, payload):
    customer_id = get_customer_id(cursor, payload.get("full_name"), payload.get("mobile_no"), payload.get("cust_code"))
    if customer_id:
        cursor.execute(
            """
            UPDATE customers
            SET cust_code=%s, full_name=%s, mobile_no=%s, address=%s, patient_name=%s,
                gender=%s, age=%s, medical_history=%s, service_start_date=%s,
                service_type=%s, charges=%s, assigned_employee_id=%s, status=%s,
                service_closed_date=%s, updated_at=NOW()
            WHERE id=%s
            """,
            (
                payload.get("cust_code") or generate_customer_code(payload.get("full_name")),
                payload.get("full_name"),
                normalize_phone(payload.get("mobile_no")) or "0000000000",
                payload.get("address") or "Imported from spreadsheet",
                payload.get("patient_name") or payload.get("full_name"),
                payload.get("gender") or "MALE",
                payload.get("age") if payload.get("age") is not None else 0,
                payload.get("medical_history"),
                payload.get("service_start_date"),
                payload.get("service_type") or "HOURS_12",
                payload.get("charges"),
                payload.get("assigned_employee_id"),
                payload.get("status") or "NEW",
                payload.get("service_closed_date"),
                customer_id,
            ),
        )
        return customer_id

    cursor.execute(
        """
        INSERT INTO customers (
            cust_code, full_name, mobile_no, address, patient_name, gender, age,
            medical_history, service_start_date, service_type, charges, assigned_employee_id,
            status, created_at, updated_at
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
        """,
        (
            payload.get("cust_code") or generate_customer_code(payload.get("full_name")),
            payload.get("full_name"),
            normalize_phone(payload.get("mobile_no")) or "0000000000",
            payload.get("address") or "Imported from spreadsheet",
            payload.get("patient_name") or payload.get("full_name"),
            payload.get("gender") or "MALE",
            payload.get("age") if payload.get("age") is not None else 0,
            payload.get("medical_history"),
            payload.get("service_start_date"),
            payload.get("service_type") or "HOURS_12",
            payload.get("charges"),
            payload.get("assigned_employee_id"),
            payload.get("status") or "NEW",
        ),
    )
    return cursor.lastrowid


def upsert_customer_charge_status(cursor, customer_id, pay_year, pay_month, status_text, paid_on=None, remarks=None):
    if customer_id is None:
        return
    sql_status = normalize_charge_status(status_text)
    cursor.execute(
        """
        INSERT INTO customer_charge_status (customer_id, pay_year, pay_month, status, paid_on, remarks, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, NOW())
        ON DUPLICATE KEY UPDATE
            status=VALUES(status), paid_on=VALUES(paid_on), remarks=VALUES(remarks)
        """,
        (customer_id, pay_year, pay_month, sql_status, paid_on, remarks),
    )


def upsert_salary_payment(cursor, employee_id, pay_year, pay_month, amount, status_text, paid_on=None, remark=None):
    if employee_id is None:
        return
    sql_status = normalize_salary_status(status_text)
    cursor.execute(
        """
        INSERT INTO salary_payments (employee_id, pay_year, pay_month, status, amount, paid_on, notes, created_by, updated_by, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, 'import_script', 'import_script', NOW())
        ON DUPLICATE KEY UPDATE
            status=VALUES(status), amount=VALUES(amount), paid_on=VALUES(paid_on), notes=VALUES(notes), updated_by='import_script'
        """,
        (employee_id, pay_year, pay_month, sql_status, amount, paid_on, remark),
    )


def upsert_attendance(cursor, employee_id, attendance_date, status_text, notes=None):
    if employee_id is None or attendance_date is None:
        return
    status = normalize_attendance_status(status_text)
    if not status:
        return
    cursor.execute(
        """
        INSERT INTO attendance (employee_id, attendance_date, status, notes, created_by, updated_by, created_at)
        VALUES (%s, %s, %s, %s, 'import_script', 'import_script', NOW())
        ON DUPLICATE KEY UPDATE
            status=VALUES(status), notes=VALUES(notes), updated_by='import_script'
        """,
        (employee_id, attendance_date, status, notes),
    )


def upsert_customer_duty(cursor, customer_id, duty_date, employee_id, hold_flag=False):
    if customer_id is None or duty_date is None:
        return
    cursor.execute(
        """
        INSERT INTO customer_duties (customer_id, duty_date, employee_id, hold, created_by, updated_by, created_at)
        VALUES (%s, %s, %s, %s, 'import_script', 'import_script', NOW())
        ON DUPLICATE KEY UPDATE
            employee_id=VALUES(employee_id), hold=VALUES(hold), updated_by='import_script'
        """,
        (customer_id, duty_date, employee_id, 1 if hold_flag else 0),
    )


def import_employee_attendance(conn, excel_path):
    wb = load_workbook(excel_path, data_only=True, read_only=True)
    cursor = conn.cursor()
    total = 0

    for ws in wb.worksheets:
        header_row_index = None
        header_row = None
        rows = list(ws.iter_rows(values_only=True))
        for idx, row in enumerate(rows):
            cells = [to_text(v) for v in row]
            if any("name of staff" in c.lower() for c in cells) or any("emp name" in c.lower() for c in cells):
                header_row_index = idx
                header_row = row
                break
        if header_row_index is None:
            continue

        date_indices = {}
        for idx, cell in enumerate(header_row):
            d = parse_date(cell)
            if d:
                date_indices[idx] = d

        for row in rows[header_row_index + 1:]:
            if not row or all(v is None or to_text(v) == "" for v in row):
                continue
            if row[0] is not None and isinstance(row[0], (int, float)):
                pass
            if not any(v is not None and to_text(v) not in {"", "-", "0"} for v in row[:7]):
                continue

            name = normalize_name(row[2] if len(row) > 2 else None)
            phone = normalize_phone(row[3] if len(row) > 3 else None)
            join_date = parse_date(row[5] if len(row) > 5 else None)
            employee_id = get_or_create_employee(cursor, name, phone, join_date)
            if employee_id is None:
                continue

            for idx, date_value in date_indices.items():
                if idx >= len(row):
                    continue
                status_text = row[idx]
                if status_text is None or to_text(status_text) in {"", "-", "0"}:
                    continue
                if not isinstance(date_value, date):
                    continue
                notes = None
                upsert_attendance(cursor, employee_id, date_value, status_text, notes)
                total += 1

    cursor.close()
    return total


def import_salary_sheet(conn, excel_path):
    wb = load_workbook(excel_path, data_only=True, read_only=True)
    cursor = conn.cursor()
    total = 0

    for ws in wb.worksheets:
        year, month = month_from_sheet(ws.title)
        if not year or not month:
            continue

        rows = list(ws.iter_rows(values_only=True))
        header_index = None
        for idx, row in enumerate(rows):
            cells = [to_text(v).lower() for v in row]
            if any("emp name" in c for c in cells) or any("name of staff" in c for c in cells):
                header_index = idx
                break
        if header_index is None:
            continue

        for row in rows[header_index + 1:]:
            if not row or all(v is None or to_text(v) == "" for v in row):
                continue
            name = normalize_name(row[1] if len(row) > 1 else None)
            if not name:
                continue
            employee_id = get_employee_id_by_name(cursor, name)
            if employee_id is None:
                # try through phone if present on row 2 or row 3
                phone = normalize_phone(row[2] if len(row) > 2 else None)
                if phone:
                    row_employee = fetch_one(cursor, "SELECT id FROM employees WHERE mobile_no=%s LIMIT 1", (phone,))
                    employee_id = row_employee[0] if row_employee else None
            if employee_id is None:
                continue

            pay_amount = parse_decimal(row[8] if len(row) > 8 else None)
            status_text = row[9] if len(row) > 9 else None
            paid_on = parse_date(row[10] if len(row) > 10 else None)
            remark = row[12] if len(row) > 12 else None

            upsert_salary_payment(cursor, employee_id, year, month, pay_amount, status_text or "UNPAID", paid_on, to_text(remark))
            total += 1

    cursor.close()
    return total


def import_client_payments(conn, excel_path):
    wb = load_workbook(excel_path, data_only=True, read_only=True)
    cursor = conn.cursor()
    total = 0

    for ws in wb.worksheets:
        year, month = month_from_sheet(ws.title)
        if not year or not month:
            continue
        rows = list(ws.iter_rows(values_only=True))
        data_start = None
        for idx, row in enumerate(rows):
            cells = [to_text(v).lower() for v in row]
            if any("lead id" in c or "cl id" in c for c in cells):
                data_start = idx + 1
                break
        if data_start is None:
            continue

        for row in rows[data_start:]:
            if not row or all(v is None or to_text(v) == "" for v in row):
                continue
            if len(row) < 9:
                continue
            lead_id = to_text(row[1])
            client_name = normalize_name(row[2])
            if not client_name and not lead_id:
                continue
            customer_id = get_customer_id(cursor, client_name, None, lead_id)
            if customer_id is None:
                # create minimal customer row if not already present
                customer_id = upsert_customer(
                    cursor,
                    {
                        "cust_code": generate_customer_code(lead_id),
                        "full_name": client_name or "Imported Client",
                        "mobile_no": None,
                        "address": "Imported from client payments",
                        "patient_name": client_name or "Imported Client",
                        "gender": "MALE",
                        "age": 0,
                        "medical_history": None,
                        "service_start_date": None,
                        "service_type": "HOURS_12",
                        "charges": parse_decimal(row[7]) if len(row) > 7 else None,
                        "assigned_employee_id": None,
                        "status": "NEW",
                    },
                )

            status_text = row[8] if len(row) > 8 else ""
            pay_on = date_from_status_text(status_text)
            remarks = row[10] if len(row) > 10 else None
            upsert_customer_charge_status(cursor, customer_id, year, month, status_text, pay_on, to_text(remarks))
            total += 1

    cursor.close()
    return total


def import_case_details(conn, excel_path):
    wb = load_workbook(excel_path, data_only=True, read_only=True)
    cursor = conn.cursor()
    total = 0

    for ws in wb.worksheets:
        rows = list(ws.iter_rows(values_only=True))
        header_row_index = None
        header_row = None
        for idx, row in enumerate(rows):
            if row and any("lead id" in to_text(v).lower() for v in row):
                header_row_index = idx
                header_row = row
                break
        if header_row_index is None:
            continue

        date_indices = {}
        for idx, cell in enumerate(header_row):
            d = parse_date(cell)
            if d:
                date_indices[idx] = d

        for row in rows[header_row_index + 1:]:
            if not row or all(v is None or to_text(v) == "" for v in row):
                continue
            code = to_text(row[1] if len(row) > 1 else None)
            full_name = normalize_name(row[2] if len(row) > 2 else None)
            if not full_name and not code:
                continue

            service_type = normalize_service_type(row[8] if len(row) > 8 else None)
            age_value = row[3] if len(row) > 3 else None
            gender_value = to_text(row[4] if len(row) > 4 else None).upper() if row[4] else "MALE"
            mobile = normalize_phone(row[5] if len(row) > 5 else None)
            address = to_text(row[6] if len(row) > 6 else "Imported from case details")
            diagnosis = to_text(row[7] if len(row) > 7 else None)
            service_start = parse_date(row[10] if len(row) > 10 else None)
            service_close = parse_date(row[11] if len(row) > 11 else None)
            charges = parse_decimal(row[12] if len(row) > 12 else None)
            employee_name = normalize_name(row[9] if len(row) > 9 else None)
            assigned_employee_id = get_employee_id_by_name(cursor, employee_name) if employee_name else None
            status = "CLOSED" if service_close else "ACTIVE"
            if assigned_employee_id is None and employee_name:
                assigned_employee_id = get_or_create_employee(cursor, employee_name, None, service_start)

            customer_payload = {
                "cust_code": code or generate_customer_code(full_name),
                "full_name": full_name or "Imported Client",
                "mobile_no": mobile,
                "address": address,
                "patient_name": full_name or "Imported Client",
                "gender": gender_value if gender_value in {"M", "MALE", "F", "FEMALE"} else "MALE",
                "age": int(age_value) if age_value is not None and to_text(age_value).isdigit() else 0,
                "medical_history": diagnosis,
                "service_start_date": service_start,
                "service_type": service_type,
                "charges": charges,
                "assigned_employee_id": assigned_employee_id,
                "status": status,
                "service_closed_date": service_close,
            }
            customer_id = upsert_customer(cursor, customer_payload)

            for idx, date_value in date_indices.items():
                if idx >= len(row):
                    continue
                status_value = row[idx]
                if status_value is None or to_text(status_value) in {"", "-", "0"}:
                    continue
                duty_status = to_text(status_value).upper()
                if duty_status in {"D", "N", "DN", "D/N", "A", "L"}:
                    hold_flag = duty_status in {"A"}
                    upsert_customer_duty(cursor, customer_id, date_value, assigned_employee_id, hold_flag)
                    total += 1

    cursor.close()
    return total


def main():
    parser = argparse.ArgumentParser(description="Import spreadsheet data into VNS Healthcare tables")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default="root")
    parser.add_argument("--database", default="vns_healthcare")
    parser.add_argument("--files", nargs="*", default=DEFAULT_FILES)
    parser.add_argument("--dry-run", action="store_true", help="Parse files and print counts without writing to MySQL")
    args = parser.parse_args()

    if args.dry_run:
        print("Dry run only: parsing spreadsheets without writing to the database")
        stats = {
            "employees": 0,
            "customers": 0,
            "attendance": 0,
            "salary_payments": 0,
            "customer_charge_status": 0,
            "customer_duties": 0,
        }
        for file in args.files:
            path = Path(file)
            if not path.exists():
                print(f"Missing: {path}")
                continue
            name_lower = path.name.lower()
            if "employee" in name_lower:
                stats["employees"] += 1
                continue
            wb = load_workbook(path, data_only=True, read_only=True)
            for ws in wb.worksheets:
                rows = list(ws.iter_rows(values_only=True))
                if any("name of staff" in to_text(v).lower() for row in rows for v in row):
                    stats["employees"] += 1
                if any("lead id" in to_text(v).lower() for row in rows for v in row):
                    stats["customers"] += 1
                if any("emp name" in to_text(v).lower() for row in rows for v in row):
                    stats["salary_payments"] += 1
                if any("client name" in to_text(v).lower() for row in rows for v in row):
                    stats["customer_charge_status"] += 1
        print(stats)
        return 0

    ensure_database(args)
    conn = connect_db(args)
    try:
        cursor = conn.cursor()
        cursor.execute(f"USE `{args.database}`")
        cursor.close()

        files = [Path(f) for f in args.files]
        for path in files:
            if not path.exists():
                print(f"Missing file: {path}")
                continue
            print(f"Processing: {path}")
            name = path.name.lower()
            if "employee" in name:
                count = import_employee_sheet(conn, str(path))
                print(f" employees imported: {count}")
            elif "salary" in name:
                count = import_salary_sheet(conn, str(path))
                print(f" salary_payments imported: {count}")
            elif "attend" in name:
                count = import_employee_attendance(conn, str(path))
                print(f" attendance imported: {count}")
            elif "case" in name:
                count = import_case_details(conn, str(path))
                print(f" customers + customer_duties imported: {count}")
            elif "client" in name or "payments" in name:
                count = import_client_payments(conn, str(path))
                print(f" customer_charge_status imported: {count}")

        conn.commit()
        print("Import complete.")
    except Exception as exc:
        conn.rollback()
        print(f"Import failed: {exc}", file=sys.stderr)
        return 1
    finally:
        conn.close()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
