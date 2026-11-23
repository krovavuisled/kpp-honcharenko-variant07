-- Variant 7. Demonstration places and fleet identifiers are fictional.
-- Run this file as an account authorized to create the schema and tables.
CREATE DATABASE IF NOT EXISTS waste_collection
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE waste_collection;

CREATE TABLE IF NOT EXISTS container_site (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(24) NOT NULL,
  name VARCHAR(100) NOT NULL,
  address VARCHAR(180) NOT NULL,
  capacity_containers INT NOT NULL,
  CONSTRAINT uq_site_code UNIQUE (code),
  CONSTRAINT ck_site_capacity CHECK (capacity_containers BETWEEN 1 AND 500)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS vehicle (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plate VARCHAR(24) NOT NULL,
  model VARCHAR(100) NOT NULL,
  capacity_kg INT NOT NULL,
  active BOOLEAN NOT NULL,
  CONSTRAINT uq_vehicle_plate UNIQUE (plate),
  CONSTRAINT ck_vehicle_capacity CHECK (capacity_kg BETWEEN 1 AND 50000),
  CONSTRAINT ck_vehicle_active CHECK (active IN (0, 1))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS pickup_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  site_id BIGINT NOT NULL,
  title VARCHAR(140) NOT NULL,
  fill_level DECIMAL(5,2) NOT NULL,
  sanitary_risk DECIMAL(5,2) NOT NULL,
  overdue_hours INT NOT NULL,
  priority DECIMAL(5,2) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'CREATED',
  CONSTRAINT fk_request_site FOREIGN KEY (site_id) REFERENCES container_site (id) ON DELETE RESTRICT,
  CONSTRAINT ck_request_fill CHECK (fill_level BETWEEN 0 AND 100),
  CONSTRAINT ck_request_risk CHECK (sanitary_risk BETWEEN 0 AND 100),
  CONSTRAINT ck_request_overdue CHECK (overdue_hours BETWEEN 0 AND 8760),
  CONSTRAINT ck_request_priority CHECK (priority BETWEEN 0 AND 100),
  CONSTRAINT ck_request_status CHECK (status IN ('CREATED','PLANNED','IN_PROGRESS','COMPLETED')),
  INDEX ix_request_status_priority (status, priority)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS collection_trip (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT NOT NULL,
  vehicle_id BIGINT NOT NULL,
  scheduled_date DATE NOT NULL,
  notes VARCHAR(240) NOT NULL DEFAULT '',
  CONSTRAINT uq_trip_request UNIQUE (request_id),
  CONSTRAINT fk_trip_request FOREIGN KEY (request_id) REFERENCES pickup_request (id) ON DELETE RESTRICT,
  CONSTRAINT fk_trip_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id) ON DELETE RESTRICT
) ENGINE=InnoDB;

INSERT IGNORE INTO container_site (id, code, name, address, capacity_containers) VALUES
  (1, 'SITE-01', 'Північний майданчик', 'вул. Умовна, 8', 6),
  (2, 'SITE-02', 'Майданчик біля ринку', 'пров. Навчальний, 4', 10),
  (3, 'SITE-03', 'Парковий майданчик', 'вул. Прикладна, 12', 4);

INSERT IGNORE INTO vehicle (id, plate, model, capacity_kg, active) VALUES
  (1, 'DEMO-01', 'Маневровий сміттєвоз', 5200, TRUE),
  (2, 'DEMO-02', 'Магістральний сміттєвоз', 9000, TRUE),
  (3, 'DEMO-03', 'Резервний сміттєвоз', 4500, FALSE);

INSERT IGNORE INTO pickup_request (id, site_id, title, fill_level, sanitary_risk, overdue_hours, priority, status) VALUES
  (1, 1, 'Позапланове вивезення заповнених контейнерів', 80, 60, 36, 65.50, 'CREATED'),
  (2, 2, 'Вивезення після ринкового дня', 95, 75, 54, 83.00, 'PLANNED'),
  (3, 3, 'Вивезення змішаних відходів', 70, 50, 18, 51.75, 'IN_PROGRESS'),
  (4, 1, 'Регулярне вивезення', 50, 30, 0, 30.50, 'COMPLETED');

INSERT IGNORE INTO collection_trip (id, request_id, vehicle_id, scheduled_date, notes) VALUES
  (1, 2, 2, '2026-11-24', 'Під’їзд із боку службових воріт'),
  (2, 3, 1, '2026-11-23', 'Забрати всі змішані відходи'),
  (3, 4, 1, '2026-11-20', 'Планове вивезення виконано');
