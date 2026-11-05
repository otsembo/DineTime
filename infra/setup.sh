#!/usr/bin/env bash
# Bootstraps Couchbase Server + Sync Gateway for DineTime. Usage: ./setup.sh (after `docker compose up -d`)
set -euo pipefail
CB=http://localhost:8091; CBAUTH=Administrator:password
SG=http://localhost:4985; SGAUTH=Administrator:password
BUCKET=dinetime; SCOPE=dinetime

echo "Initialising cluster..."
curl -sf -m 120 -X POST $CB/pools/default -d memoryQuota=512 -d indexMemoryQuota=256 >/dev/null || true
curl -sf -m 120 -X POST $CB/node/controller/setupServices -d services=kv%2Cn1ql%2Cindex >/dev/null || true
curl -sf -m 120 -X POST $CB/settings/indexes -d storageMode=forestdb >/dev/null || true
curl -sf -m 120 -X POST $CB/settings/web -d port=8091 -d username=Administrator -d password=password >/dev/null || true

echo "Creating bucket, scope, collections..."
curl -sf -m 120 -u $CBAUTH -X POST $CB/pools/default/buckets -d name=$BUCKET -d ramQuota=256 >/dev/null || true
sleep 5
curl -sf -m 120 -u $CBAUTH -X POST $CB/pools/default/buckets/$BUCKET/scopes -d name=$SCOPE >/dev/null || true
for c in meals meal_commands; do
  curl -sf -m 120 -u $CBAUTH -X POST $CB/pools/default/buckets/$BUCKET/scopes/$SCOPE/collections -d name=$c >/dev/null || true
done

echo "Creating Sync Gateway database..."
curl -sf -m 120 -u $SGAUTH -X PUT $SG/$BUCKET/ -H 'Content-Type: application/json' -d '{
  "bucket": "dinetime",
  "num_index_replicas": 0,
  "scopes": {
    "dinetime": {
      "collections": {
        "meals": {"sync": "function(doc, oldDoc, meta) { requireUser(doc.ownerId); channel(\"user::\" + doc.ownerId); }"},
        "meal_commands": {"sync": "function(doc, oldDoc, meta) { requireUser(doc.ownerId); channel(\"user::\" + doc.ownerId); }"}
      }
    }
  }
}' || echo "(database may already exist)"

echo "Creating user demo..."
curl -sf -m 120 -u $SGAUTH -X PUT $SG/$BUCKET/_user/demo -H 'Content-Type: application/json' -d '{
  "password": "password",
  "collection_access": {
    "dinetime": {
      "meals": {"admin_channels": ["user::demo"]},
      "meal_commands": {"admin_channels": ["user::demo"]}
    }
  }
}'
echo "Done. Public: ws://<host>:4984/$BUCKET  Admin: http://<host>:4985"
