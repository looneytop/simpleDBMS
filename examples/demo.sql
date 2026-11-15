CREATE TABLE brands (
    name STRING PRIMARY KEY,
    country STRING,
    registered_on DATE
);
CREATE TABLE cars (
    id INT PRIMARY KEY,
    brand STRING REFERENCES brands(name),
    price DOUBLE,
    available BOOLEAN
);
INSERT INTO brands VALUES
('Toyota', 'Japan', '2026-10-01'),
('BMW', 'Germany', '2026-10-02'),
('Ford', 'USA', '2026-10-03');
INSERT INTO cars VALUES (1, 'Toyota', 25000.0, TRUE);
INSERT INTO cars (id, brand) VALUES (2, 'BMW');
INSERT INTO cars VALUES
(3, 'Ford', 18000.0, FALSE),
(4, 'Toyota', 22000.0, TRUE);
SELECT * FROM cars;
SELECT brand, price FROM cars WHERE price > 20000;
UPDATE cars SET price = 30000, available = TRUE WHERE id = 2;
CREATE INDEX idx_cars_brand ON cars (brand);
CREATE INDEX idx_cars_price ON cars (price);
SELECT * FROM cars WHERE brand = 'Toyota';
DELETE FROM cars WHERE brand = 'Toyota';
SELECT * FROM cars;
CREATE TABLE notes (id INT PRIMARY KEY, text STRING);
INSERT INTO notes VALUES (1, 'O''Brien: пробел, запятая; точка с запятой');
SELECT * FROM notes;
DROP TABLE notes;
