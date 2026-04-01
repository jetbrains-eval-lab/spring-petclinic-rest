-- Insert Users
INSERT INTO users (username, password, enabled) VALUES
('admin', '$2a$10$ymaklWBnpBKlgdMgkjWVF.GMGyvH8aDuTK.glFOaKw712LHtRRymS', TRUE),
('owner1', '$2a$10$ymaklWBnpBKlgdMgkjWVF.GMGyvH8aDuTK.glFOaKw712LHtRRymS', TRUE),
('vet1', '$2a$10$ymaklWBnpBKlgdMgkjWVF.GMGyvH8aDuTK.glFOaKw712LHtRRymS', TRUE);

-- Assign Roles
INSERT INTO roles (username, role) VALUES
('admin', 'ROLE_OWNER_ADMIN'),
('admin', 'ROLE_VET_ADMIN'),
('admin', 'ROLE_ADMIN'),
('owner1', 'ROLE_OWNER'),
('vet1', 'ROLE_VET');

-- Insert Vets
INSERT INTO vets (first_name, last_name, username) VALUES
('James', 'Carter', 'vet1'),
('Helen', 'Leary', NULL),
('Linda', 'Douglas', NULL),
('Rafael', 'Ortega', NULL),
('Henry', 'Stevens', NULL),
('Sharon', 'Jenkins', NULL);

-- Insert Specialties
INSERT INTO specialties (name) VALUES
('radiology'),
('surgery'),
('dentistry');

-- Link Vets to Specialties
INSERT INTO vet_specialties (vet_id, specialty_id) VALUES
(2, 1),
(3, 2),
(3, 3),
(4, 2),
(5, 1);

-- Insert Pet Types
INSERT INTO types (name) VALUES
('cat'),
('dog'),
('lizard'),
('snake'),
('bird'),
('hamster');

-- Insert Owners
INSERT INTO owners (first_name, last_name, address, city, telephone, username) VALUES
('George', 'Franklin', '110 W. Liberty St.', 'Madison', '6085551023', 'owner1'),
('Betty', 'Davis', '638 Cardinal Ave.', 'Sun Prairie', '6085551749', NULL),
('Eduardo', 'Rodriquez', '2693 Commerce St.', 'McFarland', '6085558763', NULL),
('Harold', 'Davis', '563 Friendly St.', 'Windsor', '6085553198', NULL),
('Peter', 'McTavish', '2387 S. Fair Way', 'Madison', '6085552765', NULL),
('Jean', 'Coleman', '105 N. Lake St.', 'Monona', '6085552654', NULL),
('Jeff', 'Black', '1450 Oak Blvd.', 'Monona', '6085555387', NULL),
('Maria', 'Escobito', '345 Maple St.', 'Madison', '6085557683', NULL),
('David', 'Schroeder', '2749 Blackhawk Trail', 'Madison', '6085559435', NULL),
('Carlos', 'Estaban', '2335 Independence La.', 'Waunakee', '6085555487', NULL);

-- Insert Pets
INSERT INTO pets (name, birth_date, type_id, owner_id) VALUES
('Leo', '2010-09-07', 1, 1),
('Basil', '2012-08-06', 6, 2),
('Rosy', '2011-04-17', 2, 3),
('Jewel', '2010-03-07', 2, 3),
('Iggy', '2010-11-30', 3, 4),
('George', '2010-01-20', 4, 5),
('Samantha', '2012-09-04', 1, 6),
('Max', '2012-09-04', 1, 6),
('Lucky', '2011-08-06', 5, 7),
('Mulligan', '2007-02-24', 2, 8),
('Freddy', '2010-03-09', 5, 9),
('Lucky', '2010-06-24', 2, 10),
('Sly', '2012-06-08', 1, 10);

-- Insert Visits
INSERT INTO visits (pet_id, visit_date, description) VALUES
(7, '2013-01-01', 'rabies shot'),
(8, '2013-01-02', 'rabies shot'),
(8, '2013-01-03', 'neutered'),
(7, '2013-01-04', 'spayed');
