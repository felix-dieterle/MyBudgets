<?php
function handleTransactions(string $method, ?int $id, array $body): void
{
    $db = Database::getConnection();
    switch ($method) {
        case 'GET':
            if ($id) {
                $s = $db->prepare('SELECT * FROM transactions WHERE id = ?'); $s->execute([$id]);
                ($r = $s->fetch()) ? jsonResponse($r) : jsonResponse(['error' => 'Not found'], 404);
            } else {
                $where = []; $params = [];
                if ($a = $_GET['account_id']  ?? null) { $where[] = 'account_id = ?';  $params[] = $a; }
                if ($c = $_GET['category_id'] ?? null) { $where[] = 'category_id = ?'; $params[] = $c; }
                if ($f = $_GET['from']        ?? null) { $where[] = 'date >= ?';        $params[] = $f; }
                if ($t = $_GET['to']          ?? null) { $where[] = 'date <= ?';        $params[] = $t; }
                $limit = min((int)($_GET['limit'] ?? 50), 500); $offset = (int)($_GET['offset'] ?? 0);
                $sql = 'SELECT * FROM transactions' . ($where ? ' WHERE '.implode(' AND ',$where) : '') . ' ORDER BY date DESC LIMIT ? OFFSET ?';
                $params[] = $limit; $params[] = $offset;
                $s = $db->prepare($sql); $s->execute($params);
                jsonResponse($s->fetchAll());
            }
            break;
        case 'POST':
            $now = time() * 1000;
            $db->prepare('INSERT INTO transactions (account_id,virtual_account_id,amount,description,date,type,category_id,note,is_recurring,recurring_interval_days,remote_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)')
               ->execute([$body['account_id']??0,$body['virtual_account_id']??null,$body['amount']??0,$body['description']??'',$body['date']??$now,$body['type']??'EXPENSE',$body['category_id']??null,$body['note']??'',$body['is_recurring']??0,$body['recurring_interval_days']??0,$body['remote_id']??null,$now]);
            jsonResponse(['id' => $db->lastInsertId()], 201);
            break;
        case 'PUT':
            if (!$id) { jsonResponse(['error' => 'ID required'], 400); }
            $db->prepare('UPDATE transactions SET account_id=?,virtual_account_id=?,amount=?,description=?,date=?,type=?,category_id=?,note=?,is_recurring=?,recurring_interval_days=?,remote_id=? WHERE id=?')
               ->execute([$body['account_id']??0,$body['virtual_account_id']??null,$body['amount']??0,$body['description']??'',$body['date']??0,$body['type']??'EXPENSE',$body['category_id']??null,$body['note']??'',$body['is_recurring']??0,$body['recurring_interval_days']??0,$body['remote_id']??null,$id]);
            jsonResponse(['updated' => true]);
            break;
        case 'DELETE':
            if (!$id) { jsonResponse(['error' => 'ID required'], 400); }
            $db->prepare('DELETE FROM transactions WHERE id = ?')->execute([$id]);
            jsonResponse(['deleted' => true]);
            break;
        default: jsonResponse(['error' => 'Method not allowed'], 405);
    }
}
