package me.danielf.artifactstore;

enum RegistryHeaders {
    DOCKER_CONTENT_DIGEST;

    private final String val;

    RegistryHeaders() {
        this.val = toHeader(this.name());
    }

    private String toHeader(String v) {
        var sb = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            var l = Character.toLowerCase(v.charAt(i));
            var u = Character.toUpperCase(v.charAt(i));
            if (i == 0) {
                sb.append(u);
            } else if (i != v.length() - 1 && sb.charAt(i+1) == '-') {
                sb.append(u);
            } else {
                sb.append(l);
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return this.val;
    }
}
