package com.authvault.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
@Table(name = "blockchain_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class BlockchainLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @EqualsAndHashCode.Include
    @Column(name = "block_index", nullable = false, unique = true)
    private Long blockIndex;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    @ToString.Exclude
    private DigitalAsset asset;

    @Column(name = "previous_hash")
    private String previousHash;

    @Column(name = "current_hash", nullable = false, unique = true)
    private String currentHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    @ToString.Exclude
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @OneToMany(mappedBy = "blockchainBlock")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<OwnershipHistory> ownershipTransfers = new ArrayList<>();

    public enum Action {
        MINT,
        TRANSFER,
        LIST,
        SALE,
        UPDATE
    }
}
