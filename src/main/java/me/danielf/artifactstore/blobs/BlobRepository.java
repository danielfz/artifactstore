package me.danielf.artifactstore.blobs;

import me.danielf.artifactstore.manifest.Manifest;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class BlobRepository {
    private final JdbcTemplate jdbcTemplate;

    public BlobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> getBlobSize(String digest) {
        var size = jdbcTemplate.queryForObject("""
                 SELECT size FROM blobs WHERE digest = ?
                """, Long.class, digest);
        return Optional.ofNullable(size);
    }

    @Transactional
    public int storeBlob(String digest, InputStream inputStream) {
        return jdbcTemplate.execute((Connection conn) -> {
            LargeObjectManager lObj = conn.unwrap(org.postgresql.PGConnection.class).getLargeObjectAPI();
            long oid = lObj.createLO(LargeObjectManager.WRITE);
            try (LargeObject obj = lObj.open(oid, LargeObjectManager.WRITE)) {
                byte[] buf = new byte[16384];
                int n;
                int size = 0;
                while ((n = inputStream.read(buf)) != -1) {
                    obj.write(buf, size, n);
                    size += n;
                }

                jdbcTemplate.update(
                        "INSERT INTO blobs (digest, size, lo_oid) VALUES (?, ?, ?)",
                        digest, size, oid
                );
                return size;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean verifyBlobs(List<String> digests) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM blobs WHERE digest IN (" +
                        digests.stream().map(l -> "?").collect(Collectors.joining(",")) +
                        ")",
                Integer.class, digests.toArray()
        );
        return count != null && count > 0;
    }

    public Optional<InputStream> getBlob(String digest) {
        Long oid = jdbcTemplate.queryForObject(
                "SELECT lo_oid FROM blobs WHERE digest = ?", Long.class, digest
        );
        if (oid == null) return Optional.empty();

        assert jdbcTemplate.getDataSource() != null;
        try {
            Connection conn = DataSourceUtils.getConnection(jdbcTemplate.getDataSource());
            conn.setAutoCommit(false);
            LargeObjectManager lobj = conn.unwrap(org.postgresql.PGConnection.class).getLargeObjectAPI();
            LargeObject obj = lobj.open(oid, LargeObjectManager.READ);

            // Wrap the stream so that closing it also cleans up the connection
            InputStream delegate = obj.getInputStream();
            return Optional.of(new FilterInputStream(delegate) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                        obj.close();
                        conn.commit();
                        conn.setAutoCommit(true);
                    } catch (SQLException e) {
                        throw new IOException(e);
                    } finally {
                        DataSourceUtils.releaseConnection(conn, jdbcTemplate.getDataSource());
                    }
                }
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public void deleteBlob(String digest) {
        Long oid = jdbcTemplate.queryForObject(
                "SELECT lo_oid FROM blobs WHERE digest = ?", Long.class, digest
        );
        if (oid != null) {
            jdbcTemplate.execute((Connection conn) -> {
                conn.setAutoCommit(false);
                LargeObjectManager lobj = conn.unwrap(org.postgresql.PGConnection.class).getLargeObjectAPI();
                lobj.delete(oid);
                return null;
            });
        }

        /* The order matters here: delete the large object first, then the row. If you do it the other way and the row
         * delete succeeds but lobj.delete fails, you've lost the OID reference and the large object becomes
         *  permanently orphaned with no way to find it. */
        jdbcTemplate.update("DELETE FROM blobs WHERE digest = ?", digest);
    }
}
