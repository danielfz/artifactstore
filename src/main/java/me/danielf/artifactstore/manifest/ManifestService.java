package me.danielf.artifactstore.manifest;

import me.danielf.artifactstore.blobs.BlobService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class ManifestService {
    private final ManifestRepository manifestRepository;
    private final BlobService blobService;
    private final ObjectMapper objectMapper;

    public ManifestService(ManifestRepository manifestRepository, BlobService blobService, ObjectMapper objectMapper) {
        this.manifestRepository = manifestRepository;
        this.blobService = blobService;
        this.objectMapper = objectMapper;
    }

    public record ManifestContent(String mediaType, String content, Long size) {}
    public Optional<ManifestContent> getManifestContent(String repo, String tag) {
        var manifest = manifestRepository.getManifestContent(repo, tag);
        // TODO: update last_seen
        return manifest;
    }

    public Optional<Manifest.Layer> getManifestDescriptor(String repo, String tag) {
        return manifestRepository.getManifestDescriptor(repo, tag);
    }

    @Transactional
    public String store(String repo, String tag, String body) {
        Manifest manifest = objectMapper.readValue(body, Manifest.class);
        var digests = Stream.concat(Arrays.stream(manifest.layers()), Stream.of(manifest.config()))
                .filter(Objects::nonNull)
                .map(Manifest.Layer::digest)
                .toList();
        if (!blobService.verifyBlobs(digests)) {
            throw new IllegalArgumentException("blob doesn't exist");
        }
        try {
            var d = MessageDigest.getInstance("sha256").digest(body.getBytes(StandardCharsets.UTF_8));
            var dd = "sha256:" + bytesToHex(d);
            Long manifestId = manifestRepository.store(repo, tag, manifest, body, dd);
            manifestRepository.storeLayers(manifestId, digests);
            return dd;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if(hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public List<String> listTags(String repo) {
        return manifestRepository.list(repo.replaceAll("/$", ""));
    }

    public record RepoEntry(String repo, String tag) {}
    public List<RepoEntry> listAll() {
        return manifestRepository.list();
    };
}
