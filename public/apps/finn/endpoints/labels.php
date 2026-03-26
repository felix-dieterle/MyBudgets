<?php
function handleLabels(string $method, ?int $id, array $body): void
{
    $db = Database::getConnection();
    switch ($method) {
        case 'GET':
            if ($id) {
                $s = $db->prepare('SELECT * FROM labels WHERE id = ?'); $s->execute([$id]);
                ($r = $s->fetch()) ? jsonResponse($r) : jsonResponse(['error' => 'Not found'], 404);
            } else {
                jsonResponse($db->query('SELECT * FROM labels ORDER BY name')->fetchAll());
            }
            break;
        case 'POST':
            $db->prepare('INSERT INTO labels (name, color) VALUES (?, ?)')->execute([$body['name']??'',$body['color']??0]);
            jsonResponse(['id' => $db->lastInsertId()], 201);
            break;
        case 'PUT':
            if (!$id) { jsonResponse(['error' => 'ID required'], 400); }
            $db->prepare('UPDATE labels SET name=?, color=? WHERE id=?')->execute([$body['name']??'',$body['color']??0,$id]);
            jsonResponse(['updated' => true]);
            break;
        case 'DELETE':
            if (!$id) { jsonResponse(['error' => 'ID required'], 400); }
            $db->prepare('DELETE FROM labels WHERE id = ?')->execute([$id]);
            jsonResponse(['deleted' => true]);
            break;
        default: jsonResponse(['error' => 'Method not allowed'], 405);
    }
}
