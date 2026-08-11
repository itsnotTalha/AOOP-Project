package com.authvault.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Include
    @Column(nullable = false, unique = true)
    private String uuid;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(name = "profile_image")
    private String profileImage;

    private String phone;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "owner")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DigitalAsset> ownedAssets = new ArrayList<>();

    @OneToMany(mappedBy = "currentOwner")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<DigitalAsset> currentAssets = new ArrayList<>();

    @OneToMany(mappedBy = "verifiedBy")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<VerificationHistory> verifications = new ArrayList<>();

    @OneToMany(mappedBy = "owner")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<BlockchainLedger> ledgerBlocks = new ArrayList<>();

    @OneToMany(mappedBy = "seller")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<MarketplaceListing> listings = new ArrayList<>();

    @OneToMany(mappedBy = "previousOwner")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<OwnershipHistory> previousOwnershipTransfers = new ArrayList<>();

    @OneToMany(mappedBy = "newOwner")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<OwnershipHistory> receivedOwnershipTransfers = new ArrayList<>();

    @OneToMany(mappedBy = "owner")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<FractionalOwnership> fractionalOwnerships = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private VeriWallet wallet;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<SecureVault> vaultEntries = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Notification> notifications = new ArrayList<>();

    public enum Role {
        ADMIN,
        USER
    }

    public enum Status {
        ACTIVE,
        INACTIVE,
        SUSPENDED
    }
}
