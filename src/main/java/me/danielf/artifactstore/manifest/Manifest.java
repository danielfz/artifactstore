package me.danielf.artifactstore.manifest;

public record Manifest(
        int schemaVersion,
        String mediaType,
        Layer config,
        Layer[] layers
) {

    @Override
    public Layer[] layers() {
        if (layers == null) {
            return new Layer[0];
        }
        return layers;
    }

    public record Layer(
            String mediaType,
            String digest,
            Long size,
            String annotations
    ) {}
}
