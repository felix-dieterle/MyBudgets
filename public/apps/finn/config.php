<?php
/**
 * MyBudgets API Configuration
 * Copy to config.local.php and adjust for your environment.
 * config.local.php (optional, gitignored) overrides the defaults below.
 */
if (is_file(__DIR__ . '/config.local.php')) {
    require_once __DIR__ . '/config.local.php';
}
if (!defined('DB_HOST')) define('DB_HOST', getenv('DB_HOST') ?: 'localhost');
if (!defined('DB_PORT')) define('DB_PORT', getenv('DB_PORT') ?: '3306');
if (!defined('DB_NAME')) define('DB_NAME', getenv('DB_NAME') ?: 'mybudgets');
if (!defined('DB_USER')) define('DB_USER', getenv('DB_USER') ?: 'mybudgets_user');
if (!defined('DB_PASS')) define('DB_PASS', getenv('DB_PASS') ?: '');
if (!defined('API_SECRET')) define('API_SECRET', getenv('MYBUDGETS_API_SECRET') ?: 'change_me_in_production');
if (!defined('CORS_ALLOWED_ORIGIN')) define('CORS_ALLOWED_ORIGIN', getenv('CORS_ORIGIN') ?: '*');
if (!defined('DEBUG_MODE')) define('DEBUG_MODE', getenv('DEBUG_MODE') === 'true');
if (!defined('APP_VERSION')) define('APP_VERSION', '1.0.0');
