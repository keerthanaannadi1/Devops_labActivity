CREATE TABLE IF NOT EXISTS departments (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_departments_name (name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS employees (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(120) NOT NULL,
    email            VARCHAR(180) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    department_id    BIGINT       NULL,
    manager_id       BIGINT       NULL,
    designation      VARCHAR(100) NULL,
    date_of_joining  DATE         NULL,
    phone            VARCHAR(20)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_employees_email (email),
    KEY idx_employees_department (department_id),
    KEY idx_employees_manager (manager_id),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_employees_manager FOREIGN KEY (manager_id) REFERENCES employees (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS attendance (
    id              BIGINT    NOT NULL AUTO_INCREMENT,
    employee_id     BIGINT    NOT NULL,
    date            DATE      NOT NULL,
    check_in_time   TIME      NULL,
    check_out_time  TIME      NULL,
    status          VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_employee_date (employee_id, date),
    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS leave_requests (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id   BIGINT       NOT NULL,
    leave_type    VARCHAR(20)  NOT NULL,
    from_date     DATE         NOT NULL,
    to_date       DATE         NOT NULL,
    reason        VARCHAR(500) NULL,
    status        VARCHAR(20)  NOT NULL,
    reviewed_by   BIGINT       NULL,
    reviewed_at   DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_leave_employee (employee_id),
    KEY idx_leave_status (status),
    CONSTRAINT fk_leave_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_leave_reviewer FOREIGN KEY (reviewed_by) REFERENCES employees (id)
) ENGINE = InnoDB;
