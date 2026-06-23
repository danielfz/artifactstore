-- Blobs: store metadata here, file content on disk (or IPFS later)
CREATE TABLE blobs (
                       digest      VARCHAR(71) PRIMARY KEY,  -- "sha256:" + 64 hex chars
                       size        BIGINT      NOT NULL,
                       created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Manifests: store the raw JSON body, referenced by repo + tag or digest
CREATE TABLE manifests (
                           id          SERIAL      PRIMARY KEY,
                           repo        TEXT        NOT NULL,
                           tag         TEXT        NOT NULL,   -- tag (e.g. "latest") or digest
                           digest      VARCHAR(71) NOT NULL,
                           content     TEXT        NOT NULL,   -- raw manifest JSON
                           media_type  TEXT        NOT NULL,
                           created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                           UNIQUE (repo, tag)
);

-- Links blobs to manifests (a manifest references multiple layers, and a layer can be referenced by multiple manifests)
CREATE TABLE manifest_blobs (
                                manifest_id INT         REFERENCES manifests(id) ON DELETE CASCADE,
                                blob_digest VARCHAR(71) REFERENCES blobs(digest),
                                PRIMARY KEY (manifest_id, blob_digest)
);
