<?php
/**
 * MyBudgets REST API
 * URL: server/apps/finn/api.php
 */
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/db.php';
require_once __DIR__ . '/endpoints/accounts.php';
require_once __DIR__ . '/endpoints/transactions.php';
require_once __DIR__ . '/endpoints/categories.php';
require_once __DIR__ . '/endpoints/labels.php';
require_once __DIR__ . '/endpoints/sync.php';

header('Access-Control-Allow-Origin: ' . CORS_ALLOWED_ORIGIN);
header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, X-API-Key');
header('Content-Type: application/json; charset=utf-8');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') { http_response_code(204); exit; }

$apiKey = $_SERVER['HTTP_X_API_KEY'] ?? '';
if ($apiKey !== API_SECRET) { http_response_code(401); echo json_encode(['error' => 'Unauthorized']); exit; }

$method   = $_SERVER['REQUEST_METHOD'];
$relative = ltrim(substr(parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH), strlen(dirname($_SERVER['SCRIPT_NAME']))), '/');
$parts    = array_values(array_filter(explode('/', $relative)));
$resource = $parts[0] ?? '';
$id       = isset($parts[1]) ? (int)$parts[1] : null;
$body     = json_decode(file_get_contents('php://input'), true) ?? [];

switch ($resource) {
    case 'accounts':     handleAccounts($method, $id, $body);    break;
    case 'transactions': handleTransactions($method, $id, $body); break;
    case 'categories':   handleCategories($method, $id, $body);  break;
    case 'labels':       handleLabels($method, $id, $body);      break;
    case 'sync':         handleSync($method, $body);             break;
    case 'version':      echo json_encode(['version' => APP_VERSION]); break;
    default: http_response_code(404); echo json_encode(['error' => 'Not found']);
}

function jsonResponse(mixed $data, int $code = 200): void
{
    http_response_code($code);
    echo json_encode($data);
    exit;
}
