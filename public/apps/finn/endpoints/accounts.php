<?php
function handleAccounts(string $method, ?int $id, array $body): void
{
    $db = Database::getConnection();
    switch ($method) {
        case 'GET':
            if ($id) {
                $s = $db->prepare('SELECT * FROM accounts WHERE id = ?'); $s->execute([$id]);
                ($r = $s->fetch()) ? jsonResponse($r) : jsonResponse(['error' => 'Not found'], 404);
            } else {
                jsonResponse($db->query('SELECT * FROM accounts ORDER BY name')->fetchAll());
            }
            break;
        case 'POST':
            $now = time() * 1000;
            $s = $db->prepare('INSERT INTO accounts (name,type,balance,currency,color,icon,parent_account_id,is_virtual,bank_code,iban,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)');
            $s->execute([$body['name']??'',$body['type']??'CHECKING',$body['balance']??0,$body['currency']??'EUR',$body['color']??0,$body['icon']??'ic_account',$body['parent_account_id']??null,$body['is_virtual']??0,$body['bank_code']??'',$body['iban']??'',$now,$now]);
            jsonResponse(['id' => $db->lastInsertId()], 201);
            break;
        case 'PUT':
            if (!$id) { jsonResponse(['error' => 'ID required'], 400); }
            $now = time() * 1000;
            $db->prepare('UPDATE accounts SET name=?,type=?,balance=?,currency=?,color=?,icon=?,parent_account_id=?,is_virtual=?,bank_code=?,iban=?,updated_at=? WHERE id=?')
               ->execute([$body['name']??'',$body['type']??'CHECKING',$body['balance']??0,$body['currency']??'EUR',$body['color']??0,$body['icon']??'ic_account',$body['parent_account_id']??null,$body['is_virtual']??0,$body['bank_code']??'',$body['iban']??'',$now,$id]);
            jsonResponse(['updated' => true]);
            break;
        case 'DELETE':
            if (!$id) { jsonResponse(['error' => 'ID required'], 400); }
            $db->prepare('DELETE FROM accounts WHERE id = ?')->execute([$id]);
            jsonResponse(['deleted' => true]);
            break;
        default: jsonResponse(['error' => 'Method not allowed'], 405);
    }
}
