package me.danielf.artifactstore.blobs;

import jakarta.servlet.ServletInputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BlobService {
    private final ConcurrentHashMap<UUID, UploadSession> uploadSessions = new ConcurrentHashMap<>();
    private final BlobRepository blobRepository;

    public BlobService(BlobRepository blobRepository) {
        this.blobRepository = blobRepository;
    }

    public UUID startUpload() {
        var uuid = UUID.randomUUID();
        uploadSessions.put(uuid, new UploadSession(uuid));
        return uuid;
    }

    public void finishUpload(UUID uuid, String digest, ServletInputStream inputStream) {
        if (uploadSessions.get(uuid) == null) {
            throw new IllegalStateException();
        }
        blobRepository.storeBlob(digest, inputStream);
        uploadSessions.remove(uuid);
    }

    public Optional<Long> size(String digest) {
        return blobRepository.getBlobSize(digest);
    }

    public Optional<Resource> get(String digest) {
        return blobRepository.getBlob(digest).map(InputStreamResource::new);
    }

    public boolean verifyBlobs(List<String> digests) {
        return digests.isEmpty() || blobRepository.verifyBlobs(digests);
    }

    public record UploadSession(UUID uuid) {}
}
