<?php
function handleSync(string $method, array $body): void
{
    if ($method !== 'POST') { jsonResponse(['error' => 'Method not allowed'], 405); }
    $db    = Database::getConnection();
    $since = $body['since'] ?? 0;

    foreach ($body['accounts'] ?? [] as $acc) {
        $ex = $db->prepare('SELECT id FROM accounts WHERE id = ?'); $ex->execute([$acc['id'] ?? 0]);
        $now = time() * 1000;
        if ($ex->fetch()) {
            $db->prepare('UPDATE accounts SET name=?,type=?,balance=?,currency=?,updated_at=? WHERE id=?')
               ->execute([$acc['name'],$acc['type'],$acc['balance'],$acc['currency'],$now,$acc['id']]);
        } else {
            $db->prepare('INSERT INTO accounts (name,type,balance,currency,color,icon,parent_account_id,is_virtual,bank_code,iban,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)')
               ->execute([$acc['name'],$acc['type'],$acc['balance'],$acc['currency'],$acc['color']??0,$acc['icon']??'ic_account',$acc['parent_account_id']??null,$acc['is_virtual']??0,$acc['bank_code']??'',$acc['iban']??'',$now,$now]);
        }
    }
    foreach ($body['transactions'] ?? [] as $tx) {
        if (!empty($tx['remote_id'])) {
            $ex = $db->prepare('SELECT id FROM transactions WHERE remote_id = ?'); $ex->execute([$tx['remote_id']]);
            if ($ex->fetch()) continue;
        }
        $now = time() * 1000;
        $db->prepare('INSERT IGNORE INTO transactions (account_id,virtual_account_id,amount,description,date,type,category_id,note,is_recurring,recurring_interval_days,remote_id,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)')
           ->execute([$tx['account_id'],$tx['virtual_account_id']??null,$tx['amount'],$tx['description']??'',$tx['date'],$tx['type'],$tx['category_id']??null,$tx['note']??'',$tx['is_recurring']??0,$tx['recurring_interval_days']??0,$tx['remote_id']??null,$now]);
    }

    $accs = $db->prepare('SELECT * FROM accounts WHERE updated_at > ?'); $accs->execute([$since]);
    $txs  = $db->prepare('SELECT * FROM transactions WHERE created_at > ?'); $txs->execute([$since]);
    $cats = $db->query('SELECT * FROM categories ORDER BY level, name');

    jsonResponse(['server_time' => time()*1000, 'accounts' => $accs->fetchAll(), 'transactions' => $txs->fetchAll(), 'categories' => $cats->fetchAll()]);
}
