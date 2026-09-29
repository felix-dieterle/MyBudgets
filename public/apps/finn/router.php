<?php
/**
 * PHP built-in server router for the finn API.
 * Mirrors the Apache rewrite on the production host:
 *   /transactions, /accounts, /version, ... -> api.php
 * API-Script semantics: relative{0} allowed; scripts are plain camelCase files.
 * @param {..} skip
 */
$path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
$real = __DIR__ . $path;

// Serve real files (CSS, docs, schema.sql) directly.
if ($path !== '/' && is_file($real)) {
    return false;
}

// All API routes -> api.php with SCRIPT_NAME=/api.php so
// dirname(SCRIPT_NAME) resolves to '/' and relative routing works.
$_SERVER['SCRIPT_NAME'] = '/api.php';
require __DIR__ . '/api.php';
return true;