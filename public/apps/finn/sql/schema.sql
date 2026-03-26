-- MyBudgets Database Schema
-- MySQL 5.7+ / MariaDB 10.3+
-- Database: mybudgets

CREATE DATABASE IF NOT EXISTS `mybudgets` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `mybudgets`;

CREATE TABLE IF NOT EXISTS `accounts` (
    `id`               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `name`             VARCHAR(100)    NOT NULL,
    `type`             ENUM('CHECKING','SAVINGS','CASH','VIRTUAL') NOT NULL DEFAULT 'CHECKING',
    `balance`          DECIMAL(15,2)   NOT NULL DEFAULT 0.00,
    `currency`         CHAR(3)         NOT NULL DEFAULT 'EUR',
    `color`            INT             NOT NULL DEFAULT 0,
    `icon`             VARCHAR(50)     NOT NULL DEFAULT 'ic_account',
    `parent_account_id` BIGINT UNSIGNED NULL,
    `is_virtual`       TINYINT(1)      NOT NULL DEFAULT 0,
    `bank_code`        VARCHAR(20)     NOT NULL DEFAULT '',
    `iban`             VARCHAR(34)     NOT NULL DEFAULT '',
    `created_at`       BIGINT          NOT NULL,
    `updated_at`       BIGINT          NOT NULL,
    FOREIGN KEY (`parent_account_id`) REFERENCES `accounts`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `categories` (
    `id`                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `name`               VARCHAR(100)    NOT NULL,
    `parent_category_id` BIGINT UNSIGNED NULL,
    `color`              INT             NOT NULL DEFAULT 0,
    `icon`               VARCHAR(50)     NOT NULL DEFAULT 'ic_category',
    `pattern`            VARCHAR(500)    NOT NULL DEFAULT '',
    `level`              TINYINT         NOT NULL DEFAULT 1,
    `is_default`         TINYINT(1)      NOT NULL DEFAULT 0,
    FOREIGN KEY (`parent_category_id`) REFERENCES `categories`(`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `labels` (
    `id`    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `name`  VARCHAR(50)  NOT NULL,
    `color` INT          NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `transactions` (
    `id`                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `account_id`              BIGINT UNSIGNED NOT NULL,
    `virtual_account_id`      BIGINT UNSIGNED NULL,
    `amount`                  DECIMAL(15,2)   NOT NULL,
    `description`             VARCHAR(255)    NOT NULL DEFAULT '',
    `date`                    BIGINT          NOT NULL,
    `type`                    ENUM('INCOME','EXPENSE','TRANSFER') NOT NULL,
    `category_id`             BIGINT UNSIGNED NULL,
    `note`                    TEXT            NOT NULL DEFAULT '',
    `is_recurring`            TINYINT(1)      NOT NULL DEFAULT 0,
    `recurring_interval_days` INT             NOT NULL DEFAULT 0,
    `remote_id`               VARCHAR(100)    NULL,
    `created_at`              BIGINT          NOT NULL,
    FOREIGN KEY (`account_id`)         REFERENCES `accounts`(`id`)   ON DELETE CASCADE,
    FOREIGN KEY (`virtual_account_id`) REFERENCES `accounts`(`id`)   ON DELETE SET NULL,
    FOREIGN KEY (`category_id`)        REFERENCES `categories`(`id`) ON DELETE SET NULL,
    INDEX `idx_account_date` (`account_id`, `date`),
    UNIQUE KEY `uniq_remote` (`remote_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `transaction_labels` (
    `transaction_id` BIGINT UNSIGNED NOT NULL,
    `label_id`       BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`transaction_id`, `label_id`),
    FOREIGN KEY (`transaction_id`) REFERENCES `transactions`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`label_id`)       REFERENCES `labels`(`id`)       ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `gamification_badges` (
    `id`          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(100) NOT NULL,
    `description` VARCHAR(255) NOT NULL,
    `icon_res`    VARCHAR(100) NOT NULL DEFAULT 'ic_badge',
    `earned_at`   BIGINT       NULL,
    `type`        VARCHAR(50)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed: Level-1 categories
INSERT IGNORE INTO `categories` (`name`,`color`,`icon`,`pattern`,`level`,`is_default`) VALUES
('Wohnen',       -9737364,  'ic_home',      'miete|strom|wasser|gas|internet|versicherung',          1, 1),
('Lebensmittel', -9996072,  'ic_food',      'rewe|edeka|aldi|lidl|kaufland|netto|supermarkt|bäcker', 1, 1),
('Transport',    -12627531, 'ic_transport', 'tankstelle|bahn|db|ubahn|bus|öpnv',                     1, 1),
('Freizeit',     -5588480,  'ic_leisure',   'kino|restaurant|café|sport|fitness|verein',             1, 1),
('Gesundheit',   -13529344, 'ic_health',    'apotheke|arzt|krankenhaus|medikament',                  1, 1),
('Einkommen',    -11751600, 'ic_income',    'gehalt|lohn|rente|zinsen|dividende',                    1, 1),
('Sparen',       -7618826,  'ic_savings',   'sparkasse|sparbuch|depot|etf|fonds',                    1, 1),
('Bekleidung',   -6187360,  'ic_clothing',  'zara|h&m|primark|c&a',                                  1, 1);

-- Seed: Level-2 categories (Wohnen)
SET @wohnen = (SELECT id FROM categories WHERE name='Wohnen' LIMIT 1);
INSERT IGNORE INTO `categories` (`name`,`parent_category_id`,`color`,`icon`,`pattern`,`level`,`is_default`) VALUES
('Miete',           @wohnen, -9737364, 'ic_home',   'miete|kaltmiete|warmmiete',            2, 1),
('Strom & Gas',     @wohnen, -9737364, 'ic_energy', 'strom|gas|stadtwerke|energie',         2, 1),
('Internet',        @wohnen, -9737364, 'ic_wifi',   'internet|telekom|vodafone|o2|dsl',     2, 1),
('Versicherungen',  @wohnen, -9737364, 'ic_shield', 'versicherung|haftpflicht|hausrat',     2, 1);

-- Seed: Level-2 categories (Lebensmittel)
SET @food = (SELECT id FROM categories WHERE name='Lebensmittel' LIMIT 1);
INSERT IGNORE INTO `categories` (`name`,`parent_category_id`,`color`,`icon`,`pattern`,`level`,`is_default`) VALUES
('Supermarkt',   @food, -9996072, 'ic_cart',       'rewe|edeka|aldi|lidl|kaufland|netto',  2, 1),
('Restaurant',   @food, -9996072, 'ic_restaurant', 'restaurant|imbiss|döner|pizza|sushi',  2, 1),
('Lieferdienst', @food, -9996072, 'ic_delivery',   'lieferando|deliveroo|uber eats',       2, 1);

-- Seed: Level-2 categories (Transport)
SET @transport = (SELECT id FROM categories WHERE name='Transport' LIMIT 1);
INSERT IGNORE INTO `categories` (`name`,`parent_category_id`,`color`,`icon`,`pattern`,`level`,`is_default`) VALUES
('ÖPNV',       @transport, -12627531, 'ic_tram', 'bahn|mvg|hvv|rnv|kvb|öpnv|ticket',       2, 1),
('Tankstelle', @transport, -12627531, 'ic_fuel', 'tankstelle|aral|shell|esso|bp|total',      2, 1),
('Parken',     @transport, -12627531, 'ic_park', 'parkhaus|parkplatz|parking',               2, 1);

-- Seed: Level-2 categories (Gesundheit)
SET @health = (SELECT id FROM categories WHERE name='Gesundheit' LIMIT 1);
INSERT IGNORE INTO `categories` (`name`,`parent_category_id`,`color`,`icon`,`pattern`,`level`,`is_default`) VALUES
('Apotheke', @health, -13529344, 'ic_pharmacy', 'apotheke|medikament', 2, 1),
('Arzt',     @health, -13529344, 'ic_doctor',   'arzt|zahnarzt|praxis|klinik', 2, 1);

-- Seed: Level-2 categories (Einkommen)
SET @income = (SELECT id FROM categories WHERE name='Einkommen' LIMIT 1);
INSERT IGNORE INTO `categories` (`name`,`parent_category_id`,`color`,`icon`,`pattern`,`level`,`is_default`) VALUES
('Gehalt', @income, -11751600, 'ic_work',     'gehalt|lohn|arbeitgeber', 2, 1),
('Zinsen', @income, -11751600, 'ic_trending', 'zinsen|dividende|ertrag', 2, 1);
