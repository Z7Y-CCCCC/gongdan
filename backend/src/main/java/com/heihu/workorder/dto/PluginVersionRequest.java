package com.heihu.workorder.dto;

public class PluginVersionRequest {
    private String version;
    private String artifactUrl;
    private String imageName;
    private String checksum;
    private String releaseNotes;
    private String manifestJson;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getArtifactUrl() { return artifactUrl; }
    public void setArtifactUrl(String artifactUrl) { this.artifactUrl = artifactUrl; }
    public String getImageName() { return imageName; }
    public void setImageName(String imageName) { this.imageName = imageName; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getReleaseNotes() { return releaseNotes; }
    public void setReleaseNotes(String releaseNotes) { this.releaseNotes = releaseNotes; }
    public String getManifestJson() { return manifestJson; }
    public void setManifestJson(String manifestJson) { this.manifestJson = manifestJson; }
}
