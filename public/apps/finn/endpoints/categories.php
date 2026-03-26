<?php
function handleCategories(string $method, ?int $id, array $body): void
{
    $db = Database::getConnection();
    switch ($method) {
        case 'GET':
            if ($id) {
                $s = $db->prepare('SELECT * FROM categories WHERE id = ?'); $s->execute([$id]);
                ($r = $s->fetch()) ? jsonResponse($r) : jsonResponse(['error' => 'Not found'], 404);
            } else {
                jsonResponse($db->query('SELECT * FROM categories ORDER BY level, name')->fetchAll());
            }
            break;
        case 'POST':
            $db->prepare('INSERT INTO categories (name,parent_category_id,color,icon,pattern,level,is_default) VALUES (?,?,?,?,?,?,?)')
               ->execute([$body['name']??'',$body['parent_category_id']??null,$body['color']??0,$body['icon']??'ic_category',$body['pattern']??'',$body['level']??1,$body['is_default']??0]);
            jsonResponse(['id' => $db->lastInsertId()], 201);
            break;
        case 'PUT':
            if (!$id) { jsonResponse(['error' => 'ID required'], 400); }
            $db->prepare('UPDATE categories SET name=?,parent_category_id=?,color=?,icon=?,pattern=?,level=?,is_default=? WHERE id=?')
               ->execute([$body['name']??'',$body['parent_category_id']??null,$body['color']??0,$body['icon']??'ic_category',$body['pattern']??'',$body['level']??1,$body['is_default']??0,$id]);
            jsonResponse(['updated' => true]);
            break;
        case 'DELETE':
            if (!$id) { jsonResponse(['error' => 'ID required'], 400); }
            $db->prepare('DELETE FROM categories WHERE id = ?')->execute([$id]);
            jsonResponse(['deleted' => true]);
            break;
        default: jsonResponse(['error' => 'Method not allowed'], 405);
    }
}
