package main

import (
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
	"time"

	"github.com/golang/protobuf/ptypes"
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

const (
	assetObjectType  = "originalAsset"
	sha256ObjectType = "originalSha256"

	AssetTypeImage    = "IMAGE"
	AssetTypeDocument = "DOCUMENT"
	StatusVerified    = "VERIFIED"
)

var (
	uuidPattern   = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)
	sha256Pattern = regexp.MustCompile(`^[0-9a-f]{64}$`)
)

// SmartContract implements the append-only registered-original registry.
type SmartContract struct {
	contractapi.Contract
}

// OriginalAsset is the immutable on-ledger registration record.
type OriginalAsset struct {
	AssetID            string `json:"assetId"`
	CreatorIDHash      string `json:"creatorIdHash"`
	CurrentOwnerIDHash string `json:"currentOwnerIdHash"`
	SHA256             string `json:"sha256"`
	AssetType          string `json:"assetType"`
	VerificationStatus string `json:"verificationStatus"`
	EvidenceHash       string `json:"evidenceHash"`
	RegisteredAt       string `json:"registeredAt"`
}

// AssetHistoryEntry contains one Fabric key-history revision.
type AssetHistoryEntry struct {
	TransactionID string         `json:"transactionId"`
	Timestamp     string         `json:"timestamp"`
	IsDelete      bool           `json:"isDelete"`
	Asset         *OriginalAsset `json:"asset,omitempty"`
}

// RegisterOriginal stores a verified original and reserves its exact SHA-256.
func (s *SmartContract) RegisterOriginal(
	ctx contractapi.TransactionContextInterface,
	assetID string,
	creatorIDHash string,
	sha256 string,
	assetType string,
	verificationStatus string,
	evidenceHash string,
) (*OriginalAsset, error) {
	creatorIDHash = strings.TrimSpace(creatorIDHash)
	if err := validateRegistration(assetID, creatorIDHash, sha256, assetType, verificationStatus, evidenceHash); err != nil {
		return nil, err
	}

	assetKey, err := ctx.GetStub().CreateCompositeKey(assetObjectType, []string{assetID})
	if err != nil {
		return nil, fmt.Errorf("create asset key: %w", err)
	}
	shaKey, err := ctx.GetStub().CreateCompositeKey(sha256ObjectType, []string{sha256})
	if err != nil {
		return nil, fmt.Errorf("create SHA-256 key: %w", err)
	}

	existingAsset, err := ctx.GetStub().GetState(assetKey)
	if err != nil {
		return nil, fmt.Errorf("check asset %q: %w", assetID, err)
	}
	if existingAsset != nil {
		return nil, fmt.Errorf("original asset %q already exists", assetID)
	}

	existingSHA, err := ctx.GetStub().GetState(shaKey)
	if err != nil {
		return nil, fmt.Errorf("check SHA-256 %q: %w", sha256, err)
	}
	if existingSHA != nil {
		return nil, fmt.Errorf("SHA-256 %q is already registered", sha256)
	}

	txTimestamp, err := ctx.GetStub().GetTxTimestamp()
	if err != nil {
		return nil, fmt.Errorf("read transaction timestamp: %w", err)
	}
	registeredTime, err := ptypes.Timestamp(txTimestamp)
	if err != nil {
		return nil, fmt.Errorf("invalid transaction timestamp: %w", err)
	}

	asset := &OriginalAsset{
		AssetID:            assetID,
		CreatorIDHash:      creatorIDHash,
		CurrentOwnerIDHash: creatorIDHash,
		SHA256:             sha256,
		AssetType:          assetType,
		VerificationStatus: verificationStatus,
		EvidenceHash:       evidenceHash,
		RegisteredAt:       registeredTime.UTC().Format(time.RFC3339Nano),
	}
	assetJSON, err := json.Marshal(asset)
	if err != nil {
		return nil, fmt.Errorf("encode original asset: %w", err)
	}

	if err := ctx.GetStub().PutState(assetKey, assetJSON); err != nil {
		return nil, fmt.Errorf("store original asset %q: %w", assetID, err)
	}
	if err := ctx.GetStub().PutState(shaKey, []byte(assetID)); err != nil {
		return nil, fmt.Errorf("reserve SHA-256 %q: %w", sha256, err)
	}

	return asset, nil
}

// GetOriginal returns an original by its public asset UUID.
func (s *SmartContract) GetOriginal(ctx contractapi.TransactionContextInterface, assetID string) (*OriginalAsset, error) {
	if !uuidPattern.MatchString(assetID) {
		return nil, fmt.Errorf("assetId must be a valid UUID")
	}

	assetKey, err := ctx.GetStub().CreateCompositeKey(assetObjectType, []string{assetID})
	if err != nil {
		return nil, fmt.Errorf("create asset key: %w", err)
	}
	assetJSON, err := ctx.GetStub().GetState(assetKey)
	if err != nil {
		return nil, fmt.Errorf("read original asset %q: %w", assetID, err)
	}
	if assetJSON == nil {
		return nil, fmt.Errorf("original asset %q does not exist", assetID)
	}

	var asset OriginalAsset
	if err := json.Unmarshal(assetJSON, &asset); err != nil {
		return nil, fmt.Errorf("decode original asset %q: %w", assetID, err)
	}
	return &asset, nil
}

// FindBySha256 returns the registered original for an exact SHA-256, or nil when absent.
func (s *SmartContract) FindBySha256(ctx contractapi.TransactionContextInterface, sha256 string) (*OriginalAsset, error) {
	if !sha256Pattern.MatchString(sha256) {
		return nil, fmt.Errorf("sha256 must be exactly 64 lowercase hexadecimal characters")
	}

	shaKey, err := ctx.GetStub().CreateCompositeKey(sha256ObjectType, []string{sha256})
	if err != nil {
		return nil, fmt.Errorf("create SHA-256 key: %w", err)
	}
	assetID, err := ctx.GetStub().GetState(shaKey)
	if err != nil {
		return nil, fmt.Errorf("look up SHA-256 %q: %w", sha256, err)
	}
	if assetID == nil {
		return nil, nil
	}

	return s.GetOriginal(ctx, string(assetID))
}

// OriginalExists reports whether an asset UUID is already registered.
func (s *SmartContract) OriginalExists(ctx contractapi.TransactionContextInterface, assetID string) (bool, error) {
	if !uuidPattern.MatchString(assetID) {
		return false, fmt.Errorf("assetId must be a valid UUID")
	}

	assetKey, err := ctx.GetStub().CreateCompositeKey(assetObjectType, []string{assetID})
	if err != nil {
		return false, fmt.Errorf("create asset key: %w", err)
	}
	assetJSON, err := ctx.GetStub().GetState(assetKey)
	if err != nil {
		return false, fmt.Errorf("check original asset %q: %w", assetID, err)
	}
	return assetJSON != nil, nil
}

// GetAssetHistory returns Fabric's immutable key history for an original.
func (s *SmartContract) GetAssetHistory(ctx contractapi.TransactionContextInterface, assetID string) ([]AssetHistoryEntry, error) {
	exists, err := s.OriginalExists(ctx, assetID)
	if err != nil {
		return nil, err
	}
	if !exists {
		return nil, fmt.Errorf("original asset %q does not exist", assetID)
	}

	assetKey, err := ctx.GetStub().CreateCompositeKey(assetObjectType, []string{assetID})
	if err != nil {
		return nil, fmt.Errorf("create asset key: %w", err)
	}
	iterator, err := ctx.GetStub().GetHistoryForKey(assetKey)
	if err != nil {
		return nil, fmt.Errorf("read history for original asset %q: %w", assetID, err)
	}
	defer iterator.Close()

	return collectHistory(iterator)
}

func validateRegistration(assetID, creatorIDHash, sha256, assetType, verificationStatus, evidenceHash string) error {
	if !uuidPattern.MatchString(assetID) {
		return fmt.Errorf("assetId must be a valid UUID")
	}
	if creatorIDHash == "" {
		return fmt.Errorf("creatorIdHash must not be blank")
	}
	if !sha256Pattern.MatchString(sha256) {
		return fmt.Errorf("sha256 must be exactly 64 lowercase hexadecimal characters")
	}
	if assetType != AssetTypeImage && assetType != AssetTypeDocument {
		return fmt.Errorf("assetType must be IMAGE or DOCUMENT")
	}
	if verificationStatus != StatusVerified {
		return fmt.Errorf("verificationStatus must be VERIFIED")
	}
	if !sha256Pattern.MatchString(evidenceHash) {
		return fmt.Errorf("evidenceHash must be exactly 64 lowercase hexadecimal characters")
	}
	return nil
}

func collectHistory(iterator shim.HistoryQueryIteratorInterface) ([]AssetHistoryEntry, error) {
	history := make([]AssetHistoryEntry, 0)
	for iterator.HasNext() {
		modification, err := iterator.Next()
		if err != nil {
			return nil, fmt.Errorf("iterate asset history: %w", err)
		}

		entry := AssetHistoryEntry{
			TransactionID: modification.TxId,
			IsDelete:      modification.IsDelete,
		}
		if modification.Timestamp != nil {
			timestamp, err := ptypes.Timestamp(modification.Timestamp)
			if err != nil {
				return nil, fmt.Errorf("invalid history timestamp for transaction %q: %w", modification.TxId, err)
			}
			entry.Timestamp = timestamp.UTC().Format(time.RFC3339Nano)
		}
		if !modification.IsDelete && len(modification.Value) > 0 {
			var asset OriginalAsset
			if err := json.Unmarshal(modification.Value, &asset); err != nil {
				return nil, fmt.Errorf("decode history for transaction %q: %w", modification.TxId, err)
			}
			entry.Asset = &asset
		}

		history = append(history, entry)
	}
	return history, nil
}
