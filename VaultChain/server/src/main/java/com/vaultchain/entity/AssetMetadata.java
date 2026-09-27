package com.vaultchain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "asset_metadata")
public class AssetMetadata {
    public AssetMetadata() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "width")
    private Long width;

    @Column(name = "height")
    private Long height;

    @Column(name = "camera")
    private String camera;

    @Column(name = "location")
    private String location;

    @Column(name = "created_date")
    private String createdDate;

    @Column(name = "metadata_json")
    private String metadataJson;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", referencedColumnName = "id", insertable = false, updatable = false)
    private Asset asset;

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long value) { this.assetId = value; }
    public Long getWidth() { return width; }
    public void setWidth(Long value) { this.width = value; }
    public Long getHeight() { return height; }
    public void setHeight(Long value) { this.height = value; }
    public String getCamera() { return camera; }
    public void setCamera(String value) { this.camera = value; }
    public String getLocation() { return location; }
    public void setLocation(String value) { this.location = value; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String value) { this.createdDate = value; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String value) { this.metadataJson = value; }
}
