package me.danielf.artifactstore.manifest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Repository
public class ManifestRepository {
    private final JdbcTemplate jdbcTemplate;

    public ManifestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Manifest.Layer> getManifestDescriptor(String repo, String tag) {
        return lookupWithKey("SELECT media_type, digest, size FROM manifests WHERE repo = ? AND %s = ? LIMIT 1",
                tag, s -> jdbcTemplate.query(s,
                        rs -> {
                            if (!rs.next()) {
                                return Optional.empty();
                            }
                            var mediaType = rs.getString("media_type");
                            var digest = rs.getString("digest");
                            var size = rs.getLong("size");
                            return Optional.of(new Manifest.Layer(mediaType, digest, size, null));
                        }, repo, tag)
        );
    }

    public Optional<ManifestService.ManifestContent> getManifestContent(String repo, String tag) {
        return lookupWithKey("SELECT media_type, content, size FROM manifests WHERE repo = ? AND %s = ? LIMIT 1",
                tag, s -> jdbcTemplate.query(s,
                        rs -> {
                            if (!rs.next()) {
                                return Optional.empty();
                            }
                            var c = new ManifestService.ManifestContent(
                                    rs.getString("media_type"),
                                    rs.getString("content"),
                                    rs.getLong("size"));
                            return Optional.of(c);
                        }, repo, tag
                )
        );
    }

    private <T> Optional<T> lookupWithKey(String sql, String keyVal, Function<String, Optional<T>> function) {
        var opt = function.apply(String.format(sql, "tag"));
        if (opt.isEmpty() && keyVal.startsWith("sha256:")) {
            return function.apply(String.format(sql, "digest"));
        }
        return opt;
    }

    public Long store(String repo, String tag, Manifest manifest, String content, String dd) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
        INSERT INTO manifests (repo, tag, digest, content, media_type, size)
        VALUES (?, ?, ?, ?, ?, ?)
        """, new String[]{"id"});
            ps.setString(1, repo);
            ps.setString(2, tag);
            ps.setString(3, dd);
            ps.setString(4, content);
            ps.setString(5, manifest.mediaType());
            ps.setLong(6, content.getBytes(StandardCharsets.UTF_8).length);
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    public void storeLayers(Long manifestId, Collection<String> digests) {
        if (digests.isEmpty()) {
           return;
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO manifest_blobs (manifest_id, blob_digest) VALUES (?, ?)",
                digests,
                digests.size(),
                (ps, digest) -> {
                    ps.setLong(1, manifestId);
                    ps.setString(2, digest);
                }
        );
    }

    public List<String> list(String repo) {
        return jdbcTemplate.queryForList(
                "SELECT reference FROM manifests WHERE repository = ? AND reference NOT LIKE 'sha256:%'",
                String.class, repo
        );
    }


}
