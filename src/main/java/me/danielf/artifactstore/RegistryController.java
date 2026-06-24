package me.danielf.artifactstore;

import jakarta.servlet.http.HttpServletRequest;
import me.danielf.artifactstore.blobs.BlobService;
import me.danielf.artifactstore.manifest.ManifestService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v2")
public class RegistryController {
    private final BlobService blobService;
    private final ManifestService manifestService;

    public RegistryController(BlobService blobService, ManifestService manifestService) {
        this.blobService = blobService;
        this.manifestService = manifestService;
    }

    // 1. Version check — Docker clients always hit this first
    @GetMapping("/")
    public ResponseEntity<Void> version() {
        return ResponseEntity.ok()
                .header("Docker-Distribution-API-Version", "registry/2.0")
                .build();
    }

    // 2. Check if a blob exists
    @RequestMapping(value = "/{repo1}/{repo2}/blobs/{digest}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> blobExists(@PathVariable String repo1, @PathVariable String repo2,
                                           @PathVariable String digest) {
        return blobExists(repo1 + "/" + repo2, digest);
    }
    @RequestMapping(value = "/{repo}/blobs/{digest}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> blobExists(@PathVariable String repo, String digest) {
        var optSize = blobService.size(digest);
        return optSize.map(size -> ResponseEntity.ok()
                    .header("Content-Length", String.valueOf(size))
                    .header("Docker-Content-Digest", digest)
                    .<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 3. Download a blob
    @GetMapping("/{repo1}/{repo2}/blobs/{digest}")
    public ResponseEntity<Resource> getBlob(@PathVariable String repo1,
                                            @PathVariable String repo2,
                                            @PathVariable String digest) {
        return getBlob(repo1 + "/" + repo2, digest);
    }
    @GetMapping("/{repo}/blobs/{digest}")
    public ResponseEntity<Resource> getBlob(@PathVariable String repo,
                                            @PathVariable String digest) {
        return blobService.get(digest)
                .map(resource -> ResponseEntity.ok()
                        .header("Docker-Content-Digest", digest)
                        .body(resource))
                .orElse(ResponseEntity.notFound().build());
    }

    // 4. Initiate blob upload — returns a session UUID
    @PostMapping("/{repo1}/{repo2}/blobs/uploads/")
    public ResponseEntity<Void> startUpload(@PathVariable String repo1, @PathVariable String repo2) {
        return startUpload(repo1 + "/" + repo2);
    }
    @PostMapping("/{repo}/blobs/uploads/")
    public ResponseEntity<Void> startUpload(@PathVariable String repo) {
        UUID uuid = blobService.startUpload();
        return ResponseEntity.status(202)
                .header("Location", "/v2/" + repo + "/blobs/uploads/" + uuid)
                .header("Docker-Upload-UUID", uuid.toString())
                .build();
    }

    // 5. Complete blob upload
    @PutMapping("/{repo1}/{repo2}/blobs/uploads/{uuid}")
    public ResponseEntity<Void> completeUpload(@PathVariable String repo1, @PathVariable String repo2,
                                               @PathVariable UUID uuid,
                                               @RequestParam String digest,
                                               HttpServletRequest request) throws IOException {
        return completeUpload(repo1 + "/" + repo2, uuid, digest, request);
    }
    @PutMapping("/{repo}/blobs/uploads/{uuid}")
    public ResponseEntity<Void> completeUpload(@PathVariable String repo,
                                               @PathVariable UUID uuid,
                                               @RequestParam String digest,
                                               HttpServletRequest request) throws IOException {
        blobService.finishUpload(uuid, digest, request.getInputStream());
        return ResponseEntity.status(201)
                .header("Docker-Content-Digest", digest)
                .header("Location", "/v2/" + repo + "/blobs/" + digest)
                .build();
    }

    @DeleteMapping("/{repo}/blobs/uploads/{uuid}")
    public ResponseEntity<Void> deleteBlob(@PathVariable String repo,
                                           @RequestParam String digest) {
        blobService.safelyDelete(digest);
        return ResponseEntity.status(201)
                .header("Docker-Content-Digest", digest)
                .build();
    }

    // ---- MANIFESTS

    // 6. Push a manifest (docker push finalizes here)
    @PutMapping("/{repo1}/{repo2}/manifests/{tag}")
    public ResponseEntity<Void> putManifest(@PathVariable String repo1, @PathVariable String repo2,
                                            @PathVariable String tag,
                                            @RequestBody String body) {
        return putManifest(repo1 + "/" + repo2, tag, body);
    }
    @PutMapping("/{repo}/manifests/{tag}")
    public ResponseEntity<Void> putManifest(@PathVariable String repo,
                                            @PathVariable String tag,
                                            @RequestBody String body) {
        String digest = manifestService.store(repo, tag, body);
        return ResponseEntity.status(201)
                .header("Docker-Content-Digest", digest)
                .header("Location", "/v2/" + repo + "/manifests/" + tag)
                .build();
    }

    @RequestMapping(value = "/{repo1}/{repo2}/manifests/{tag}",  method = RequestMethod.HEAD)
    public ResponseEntity<Void> getManifestDescriptor(@PathVariable String repo1,
                                                      @PathVariable String repo2,
                                                      @PathVariable String tag) {
        return getManifestDescriptor(repo1 + "/" + repo2, tag);
    }
    @RequestMapping(value = "/{repo}/manifests/{tag}",  method = RequestMethod.HEAD)
    public ResponseEntity<Void> getManifestDescriptor(@PathVariable String repo, @PathVariable String tag) {
        return manifestService.getManifestDescriptor(repo, tag)
                .map(info -> ResponseEntity
                        .ok()
                        .headers(headers -> {
                            headers.set("Docker-Content-Digest", info.digest());
                            headers.set("Content-Type", info.mediaType());
                            headers.set("Content-Length", String.valueOf(info.size()));
                        })
                        .<Void>build()
                )
                .orElse(ResponseEntity.notFound().build());
    }

    // 7. Pull a manifest (docker pull starts here)
    @GetMapping("/{repo1}/{repo2}/manifests/{tag}")
    public ResponseEntity<String> getManifest(@PathVariable String repo1, @PathVariable String repo2,
                                                @PathVariable String tag) {
        return getManifest(repo1 + "/" + repo2, tag);
    }
    @GetMapping("/{repo}/manifests/{tag}")
    public ResponseEntity<String> getManifest(@PathVariable String repo,
                                              @PathVariable String tag) {
        return manifestService.getManifestContent(repo, tag)
                .map(manifest -> ResponseEntity.ok()
                        .header("Content-Type", manifest.mediaType())
                        .header("Content-Length", String.valueOf(manifest.size()))
                        .body(manifest.content()))
                .orElse(ResponseEntity.notFound().build());
    }

    // 8. List tags
    @GetMapping("/{repo1}/{repo2}/tags/list")
    public ResponseEntity<Map<String, Object>> listTags(@PathVariable String repo1, @PathVariable String repo2) {
        return listTags(repo1 + "/" + repo2);
    }
    @GetMapping("/{repo}/tags/list")
    public ResponseEntity<Map<String, Object>> listTags(@PathVariable String repo) {
        List<String> tags = manifestService.listTags(repo);
        return ResponseEntity.ok(Map.of("name", repo, "tags", tags));
    }

}