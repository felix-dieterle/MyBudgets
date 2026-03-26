<?php
/**
 * MyBudgets API Configuration
 * Copy to config.local.php and adjust for your environment.
 */
define('DB_HOST', getenv('DB_HOST') ?: 'localhost');
define('DB_PORT', getenv('DB_PORT') ?: '3306');
define('DB_NAME', getenv('DB_NAME') ?: 'mybudgets');
define('DB_USER', getenv('DB_USER') ?: 'mybudgets_user');
define('DB_PASS', getenv('DB_PASS') ?: '');
define('API_SECRET', getenv('MYBUDGETS_API_SECRET') ?: 'change_me_in_production');
define('CORS_ALLOWED_ORIGIN', getenv('CORS_ORIGIN') ?: '*');
define('DEBUG_MODE', getenv('DEBUG_MODE') === 'true');
define('APP_VERSION', '1.0.0');
