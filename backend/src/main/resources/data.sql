-- Seed data. Password for every account below is: password123
-- BCrypt hash of "password123" (strength 10)

MERGE INTO departments (name) KEY(name) VALUES ('Engineering');
MERGE INTO departments (name) KEY(name) VALUES ('Human Resources');
MERGE INTO departments (name) KEY(name) VALUES ('Finance');

-- HR Admin (no manager)
MERGE INTO employees (name, email, password_hash, role, department_id, manager_id, designation, date_of_joining, phone)
KEY(email)
VALUES (
    'Asha Menon', 'asha@elms.local',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'HR_ADMIN',
    (SELECT id FROM departments WHERE name = 'Human Resources'),
    NULL, 'HR Head', '2019-03-11', '9840011001'
);

-- Manager
MERGE INTO employees (name, email, password_hash, role, department_id, manager_id, designation, date_of_joining, phone)
KEY(email)
VALUES (
    'Ravi Kumar', 'ravi@elms.local',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'MANAGER',
    (SELECT id FROM departments WHERE name = 'Engineering'),
    NULL, 'Engineering Manager', '2018-07-02', '9840011002'
);

-- Employees reporting to Ravi
MERGE INTO employees (name, email, password_hash, role, department_id, manager_id, designation, date_of_joining, phone)
KEY(email)
VALUES (
    'Priya Sharma', 'priya@elms.local',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'EMPLOYEE',
    (SELECT id FROM departments WHERE name = 'Engineering'),
    (SELECT id FROM employees WHERE email = 'ravi@elms.local'),
    'Senior Software Engineer', '2021-01-18', '9840011003'
);

MERGE INTO employees (name, email, password_hash, role, department_id, manager_id, designation, date_of_joining, phone)
KEY(email)
VALUES (
    'Daniel Fernandes', 'daniel@elms.local',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'EMPLOYEE',
    (SELECT id FROM departments WHERE name = 'Engineering'),
    (SELECT id FROM employees WHERE email = 'ravi@elms.local'),
    'Software Engineer', '2022-06-13', '9840011004'
);
